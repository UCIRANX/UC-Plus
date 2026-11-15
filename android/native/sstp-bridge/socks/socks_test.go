package socks

import (
	"context"
	"encoding/binary"
	"io"
	"net"
	"testing"
)

func echoServer(t *testing.T) net.Listener {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) { defer c.Close(); _, _ = io.Copy(c, c) }(c)
		}
	}()
	return ln
}

func connectVia(t *testing.T, proxy net.Addr, atyp byte, addr []byte, port int) net.Conn {
	c, err := net.Dial("tcp", proxy.String())
	if err != nil {
		t.Fatal(err)
	}
	c.Write([]byte{5, 1, 0})
	g := make([]byte, 2)
	io.ReadFull(c, g)
	if g[1] != 0 {
		t.Fatalf("method rejected: %x", g)
	}
	req := append([]byte{5, 1, 0, atyp}, addr...)
	p := make([]byte, 2)
	binary.BigEndian.PutUint16(p, uint16(port))
	c.Write(append(req, p...))
	rep := make([]byte, 10)
	if _, err := io.ReadFull(c, rep); err != nil {
		t.Fatal(err)
	}
	if rep[1] != 0 {
		t.Fatalf("connect failed: code %d", rep[1])
	}
	return c
}

func TestConnectIPv4AndDomain(t *testing.T) {
	echo := echoServer(t)
	defer echo.Close()
	proxy, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer proxy.Close()
	dial := func(ctx context.Context, network, address string) (net.Conn, error) {
		var d net.Dialer
		return d.DialContext(ctx, network, address)
	}
	go Serve(proxy, dial)

	port := echo.Addr().(*net.TCPAddr).Port
	ipv4 := connectVia(t, proxy.Addr(), 1, []byte{127, 0, 0, 1}, port)
	ipv4.Write([]byte("hello-ipv4"))
	got := make([]byte, 10)
	io.ReadFull(ipv4, got)
	if string(got) != "hello-ipv4" {
		t.Fatalf("ipv4 echo = %q", got)
	}
	ipv4.Close()

	name := []byte("localhost")
	dom := connectVia(t, proxy.Addr(), 3, append([]byte{byte(len(name))}, name...), port)
	dom.Write([]byte("hello-dom!"))
	got2 := make([]byte, 10)
	io.ReadFull(dom, got2)
	if string(got2) != "hello-dom!" {
		t.Fatalf("domain echo = %q", got2)
	}
	dom.Close()
}
