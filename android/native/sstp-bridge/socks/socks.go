// Package socks is a minimal SOCKS5 server (RFC 1928) with no authentication and
// CONNECT only. It forwards each connection through the dial function it is given.
package socks

import (
	"context"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"strconv"
	"sync"
	"time"
)

// DialFunc opens an outbound connection for a SOCKS CONNECT request.
type DialFunc func(ctx context.Context, network, address string) (net.Conn, error)

const (
	version        = 5
	methodNone     = 0
	methodNoAcpt   = 0xff
	cmdConnect     = 1
	cmdUDPAssoc    = 3
	atypIPv4       = 1
	atypDomain     = 3
	atypIPv6       = 4
	repSuccess     = 0
	repFailure     = 1
	repNotAllowed  = 2
	repCmdNotSupp  = 7
	repAddrNotSup  = 8
	repHostUnreach = 4
	handshakeTime  = 20 * time.Second
	dialTime       = 20 * time.Second
)

// Serve accepts connections on ln until it is closed, handling each in its own goroutine.
func Serve(ln net.Listener, dial DialFunc) error {
	for {
		conn, err := ln.Accept()
		if err != nil {
			return err
		}
		go handle(conn, dial)
	}
}

func handle(client net.Conn, dial DialFunc) {
	defer client.Close()
	_ = client.SetDeadline(time.Now().Add(handshakeTime))

	// Greeting: VER NMETHODS METHODS...
	head := make([]byte, 2)
	if _, err := io.ReadFull(client, head); err != nil || head[0] != version {
		return
	}
	methods := make([]byte, head[1])
	if _, err := io.ReadFull(client, methods); err != nil {
		return
	}
	if !contains(methods, methodNone) {
		_, _ = client.Write([]byte{version, methodNoAcpt})
		return
	}
	if _, err := client.Write([]byte{version, methodNone}); err != nil {
		return
	}

	// Request: VER CMD RSV ATYP DST.ADDR DST.PORT
	req := make([]byte, 4)
	if _, err := io.ReadFull(client, req); err != nil || req[0] != version {
		return
	}
	if req[1] != cmdConnect && req[1] != cmdUDPAssoc {
		reply(client, repCmdNotSupp)
		return
	}
	host, err := readAddress(client, req[3])
	if err != nil {
		reply(client, repAddrNotSup)
		return
	}
	portBytes := make([]byte, 2)
	if _, err := io.ReadFull(client, portBytes); err != nil {
		return
	}
	if req[1] == cmdUDPAssoc {
		// For UDP ASSOCIATE the address and port are the client's expected sender and are ignored.
		handleAssociate(client, dial)
		return
	}
	target := net.JoinHostPort(host, strconv.Itoa(int(binary.BigEndian.Uint16(portBytes))))

	ctx, cancel := context.WithTimeout(context.Background(), dialTime)
	upstream, err := dial(ctx, "tcp", target)
	cancel()
	if err != nil {
		reply(client, repHostUnreach)
		return
	}
	defer upstream.Close()
	if err := reply(client, repSuccess); err != nil {
		return
	}
	_ = client.SetDeadline(time.Time{})
	_ = upstream.SetDeadline(time.Time{})
	pipe(client, upstream)
}

func readAddress(r io.Reader, atyp byte) (string, error) {
	switch atyp {
	case atypIPv4:
		b := make([]byte, 4)
		if _, err := io.ReadFull(r, b); err != nil {
			return "", err
		}
		return net.IP(b).String(), nil
	case atypIPv6:
		b := make([]byte, 16)
		if _, err := io.ReadFull(r, b); err != nil {
			return "", err
		}
		return net.IP(b).String(), nil
	case atypDomain:
		n := make([]byte, 1)
		if _, err := io.ReadFull(r, n); err != nil {
			return "", err
		}
		name := make([]byte, n[0])
		if _, err := io.ReadFull(r, name); err != nil {
			return "", err
		}
		if len(name) == 0 {
			return "", errors.New("empty domain")
		}
		return string(name), nil
	default:
		return "", errors.New("unsupported address type")
	}
}

// reply writes a SOCKS5 reply with a zero IPv4 bound address.
func reply(w io.Writer, code byte) error {
	_, err := w.Write([]byte{version, code, 0, atypIPv4, 0, 0, 0, 0, 0, 0})
	return err
}

// replyBound writes a SOCKS5 reply that reports the given bound address.
func replyBound(w io.Writer, code byte, ip net.IP, port int) error {
	out := []byte{version, code, 0}
	if v4 := ip.To4(); v4 != nil {
		out = append(out, atypIPv4)
		out = append(out, v4...)
	} else {
		out = append(out, atypIPv6)
		out = append(out, ip.To16()...)
	}
	out = append(out, byte(port>>8), byte(port))
	_, err := w.Write(out)
	return err
}

func pipe(a, b net.Conn) {
	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		_, _ = io.Copy(a, b)
		closeWrite(a)
	}()
	go func() {
		defer wg.Done()
		_, _ = io.Copy(b, a)
		closeWrite(b)
	}()
	wg.Wait()
}

func closeWrite(c net.Conn) {
	if tc, ok := c.(*net.TCPConn); ok {
		_ = tc.CloseWrite()
		return
	}
	_ = c.Close()
}

func contains(list []byte, v byte) bool {
	for _, b := range list {
		if b == v {
			return true
		}
	}
	return false
}
