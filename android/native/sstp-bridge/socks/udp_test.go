package socks

import (
	"bytes"
	"context"
	"io"
	"net"
	"testing"
	"time"
)

func dialer(ctx context.Context, network, address string) (net.Conn, error) {
	var d net.Dialer
	return d.DialContext(ctx, network, address)
}

// startUDPEcho runs a UDP echo server and returns its address.
func startUDPEcho(t *testing.T) *net.UDPAddr {
	t.Helper()
	pc, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { pc.Close() })
	go func() {
		buf := make([]byte, 2048)
		for {
			n, from, err := pc.ReadFrom(buf)
			if err != nil {
				return
			}
			_, _ = pc.WriteTo(buf[:n], from)
		}
	}()
	return pc.LocalAddr().(*net.UDPAddr)
}

func startSocks(t *testing.T) net.Listener {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ln.Close() })
	go func() { _ = Serve(ln, dialer) }()
	return ln
}

// associate performs the greeting and UDP ASSOCIATE over TCP and returns the control
// connection plus the relay address the server bound.
func associate(t *testing.T, proxy net.Listener) (net.Conn, *net.UDPAddr) {
	t.Helper()
	ctrl, err := net.Dial("tcp", proxy.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ctrl.Close() })
	_ = ctrl.SetDeadline(time.Now().Add(5 * time.Second))

	if _, err := ctrl.Write([]byte{version, 1, methodNone}); err != nil {
		t.Fatal(err)
	}
	greet := make([]byte, 2)
	if _, err := io.ReadFull(ctrl, greet); err != nil || greet[1] != methodNone {
		t.Fatalf("greeting failed: %v %v", greet, err)
	}
	// VER CMD RSV ATYP=IPv4 0.0.0.0:0
	if _, err := ctrl.Write([]byte{version, cmdUDPAssoc, 0, atypIPv4, 0, 0, 0, 0, 0, 0}); err != nil {
		t.Fatal(err)
	}
	hdr := make([]byte, 4)
	if _, err := io.ReadFull(ctrl, hdr); err != nil || hdr[1] != repSuccess {
		t.Fatalf("associate failed: %v %v", hdr, err)
	}
	var ip net.IP
	switch hdr[3] {
	case atypIPv4:
		b := make([]byte, 4)
		_, _ = io.ReadFull(ctrl, b)
		ip = net.IP(b)
	default:
		t.Fatalf("unexpected bound atyp %d", hdr[3])
	}
	pb := make([]byte, 2)
	if _, err := io.ReadFull(ctrl, pb); err != nil {
		t.Fatal(err)
	}
	_ = ctrl.SetDeadline(time.Time{})
	return ctrl, &net.UDPAddr{IP: ip, Port: int(pb[0])<<8 | int(pb[1])}
}

func TestUDPAssociateEchoesDatagram(t *testing.T) {
	echo := startUDPEcho(t)
	proxy := startSocks(t)
	_, relay := associate(t, proxy)

	client, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer client.Close()

	// RSV RSV FRAG ATYP=IPv4 DST.ADDR DST.PORT DATA
	payload := []byte("dns-query-bytes")
	req := []byte{0, 0, 0, atypIPv4}
	req = append(req, echo.IP.To4()...)
	req = append(req, byte(echo.Port>>8), byte(echo.Port))
	req = append(req, payload...)
	if _, err := client.WriteTo(req, relay); err != nil {
		t.Fatal(err)
	}

	_ = client.SetReadDeadline(time.Now().Add(5 * time.Second))
	buf := make([]byte, 2048)
	n, _, err := client.ReadFrom(buf)
	if err != nil {
		t.Fatalf("no reply: %v", err)
	}
	// The reply carries the same address header followed by the echoed payload.
	want := append(append([]byte{0, 0, 0}, req[3:10]...), payload...)
	if !bytes.Equal(buf[:n], want) {
		t.Fatalf("reply = %x, want %x", buf[:n], want)
	}
}

func TestParseUDPRequestRejectsFragments(t *testing.T) {
	if _, _, _, ok := parseUDPRequest([]byte{0, 0, 1, atypIPv4, 1, 2, 3, 4, 0, 53, 'x'}); ok {
		t.Fatal("fragmented datagram accepted")
	}
}

func TestParseUDPRequestDomain(t *testing.T) {
	p := []byte{0, 0, 0, atypDomain, 3, 'd', 'n', 's', 0, 53, 9, 9}
	target, header, data, ok := parseUDPRequest(p)
	if !ok || target != "dns:53" || !bytes.Equal(header, p[3:10]) || !bytes.Equal(data, []byte{9, 9}) {
		t.Fatalf("got target=%q header=%x data=%x ok=%v", target, header, data, ok)
	}
}
