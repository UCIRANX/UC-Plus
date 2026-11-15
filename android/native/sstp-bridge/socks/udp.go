package socks

import (
	"bytes"
	"context"
	"encoding/binary"
	"io"
	"net"
	"strconv"
	"sync"
	"time"
)

const (
	udpIdle    = 2 * time.Minute
	maxUDPSize = 65535
)

// udpFlow is one outbound UDP socket for a single target address.
type udpFlow struct {
	conn   net.Conn
	header []byte // ATYP DST.ADDR DST.PORT exactly as the client sent it, echoed on replies
}

// handleAssociate implements SOCKS5 UDP ASSOCIATE (RFC 1928 section 7). It binds a
// loopback UDP socket, reports it to the client, and relays datagrams through dial
// until the TCP control connection closes or the association goes idle.
func handleAssociate(ctrl net.Conn, dial DialFunc) {
	tcpAddr, ok := ctrl.RemoteAddr().(*net.TCPAddr)
	if !ok {
		reply(ctrl, repFailure)
		return
	}
	pc, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		reply(ctrl, repFailure)
		return
	}
	defer pc.Close()
	bound := pc.LocalAddr().(*net.UDPAddr)
	if err := replyBound(ctrl, repSuccess, bound.IP, bound.Port); err != nil {
		return
	}
	_ = ctrl.SetDeadline(time.Time{})

	// The association lives exactly as long as the control connection.
	go func() {
		_, _ = io.Copy(io.Discard, ctrl)
		_ = pc.Close()
	}()

	var mu sync.Mutex
	flows := make(map[string]*udpFlow)
	var clientAddr *net.UDPAddr
	closeAll := func() {
		mu.Lock()
		defer mu.Unlock()
		for k, f := range flows {
			_ = f.conn.Close()
			delete(flows, k)
		}
	}
	defer closeAll()

	buf := make([]byte, maxUDPSize)
	for {
		n, from, err := pc.ReadFrom(buf)
		if err != nil {
			return
		}
		src, ok := from.(*net.UDPAddr)
		// Only the client that opened the control connection may use this association.
		if !ok || !src.IP.Equal(tcpAddr.IP) {
			continue
		}
		mu.Lock()
		clientAddr = src
		mu.Unlock()

		target, header, data, ok := parseUDPRequest(buf[:n])
		if !ok {
			continue
		}

		mu.Lock()
		flow := flows[target]
		mu.Unlock()
		if flow == nil {
			ctx, cancel := context.WithTimeout(context.Background(), dialTime)
			conn, err := dial(ctx, "udp", target)
			cancel()
			if err != nil {
				continue
			}
			flow = &udpFlow{conn: conn, header: header}
			mu.Lock()
			flows[target] = flow
			mu.Unlock()
			go relayUpstream(flow, target, pc, func() *net.UDPAddr {
				mu.Lock()
				defer mu.Unlock()
				return clientAddr
			}, func() {
				mu.Lock()
				defer mu.Unlock()
				if flows[target] == flow {
					delete(flows, target)
				}
				_ = flow.conn.Close()
			})
		}
		_, _ = flow.conn.Write(data)
	}
}

// relayUpstream copies replies from one upstream flow back to the SOCKS client.
func relayUpstream(f *udpFlow, target string, pc net.PacketConn, client func() *net.UDPAddr, done func()) {
	defer done()
	buf := make([]byte, maxUDPSize)
	for {
		_ = f.conn.SetReadDeadline(time.Now().Add(udpIdle))
		n, err := f.conn.Read(buf)
		if err != nil {
			return
		}
		dst := client()
		if dst == nil {
			continue
		}
		pkt := make([]byte, 0, 3+len(f.header)+n)
		pkt = append(pkt, 0, 0, 0) // RSV RSV FRAG
		pkt = append(pkt, f.header...)
		pkt = append(pkt, buf[:n]...)
		if _, err := pc.WriteTo(pkt, dst); err != nil {
			return
		}
	}
}

// parseUDPRequest decodes a SOCKS5 UDP request header (RFC 1928 section 7) and
// returns the target "host:port", the raw address header, and the payload.
// Fragmented datagrams are not supported and are rejected.
func parseUDPRequest(p []byte) (target string, header []byte, data []byte, ok bool) {
	if len(p) < 4 || p[0] != 0 || p[1] != 0 || p[2] != 0 {
		return "", nil, nil, false
	}
	r := bytes.NewReader(p[4:])
	host, err := readAddress(r, p[3])
	if err != nil {
		return "", nil, nil, false
	}
	portBytes := make([]byte, 2)
	if _, err := io.ReadFull(r, portBytes); err != nil {
		return "", nil, nil, false
	}
	end := len(p) - r.Len()
	port := binary.BigEndian.Uint16(portBytes)
	target = net.JoinHostPort(host, strconv.Itoa(int(port)))
	return target, p[3:end], p[end:], true
}
