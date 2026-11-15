// Command sstpbridge connects to an SSTP server through a local TCP relay and
// serves a SOCKS5 proxy on loopback. The app points a sing-box socks outbound at
// that proxy, so every connection leaves through the SSTP tunnel.
package main

import (
	"context"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"strconv"
	"time"

	"github.com/bclswl0827/govpn/protocols/sstp"
	"sstpbridge/socks"
)

func main() {
	relayPort := flag.Int("relay-port", 0, "loopback port of the protected TCP relay that reaches the SSTP server")
	serverName := flag.String("server-name", "", "real SSTP server host name, used for TLS SNI")
	socksPort := flag.Int("socks-port", 0, "loopback port for the SOCKS5 proxy")
	flag.Parse()
	// Credentials come from the environment so they stay out of the process arguments.
	username := os.Getenv("SSTP_USERNAME")
	password := os.Getenv("SSTP_PASSWORD")
	if *relayPort <= 0 || *relayPort > 65535 || *socksPort <= 0 || *socksPort > 65535 || *serverName == "" {
		fmt.Fprintln(os.Stderr, "usage: sstpbridge -relay-port N -server-name HOST -socks-port N (credentials in SSTP_USERNAME and SSTP_PASSWORD)")
		os.Exit(64)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	session, err := sstp.NewClient(sstp.Config{
		// The relay forwards raw bytes to the real server, so TLS still targets
		// ServerName. The certificate is self-signed on the public gateways.
		Server:     "127.0.0.1",
		Port:       *relayPort,
		ServerName: *serverName,
		SkipVerify: true,
		Username:   username,
		Password:   password,
		Logger:     log.New(io.Discard, "", 0),
	}).Start(ctx)
	cancel()
	if err != nil {
		fmt.Fprintln(os.Stderr, "SSTP connect failed:", err)
		os.Exit(1)
	}

	go func() {
		err := session.Wait(context.Background())
		fmt.Fprintln(os.Stderr, "SSTP session ended:", err)
		os.Exit(2)
	}()

	ln, err := net.Listen("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(*socksPort)))
	if err != nil {
		fmt.Fprintln(os.Stderr, "SOCKS listen failed:", err)
		_ = session.Close()
		os.Exit(1)
	}
	fmt.Println("SSTP ready")
	if err := socks.Serve(ln, session.DialContext); err != nil {
		fmt.Fprintln(os.Stderr, "SOCKS server stopped:", err)
		os.Exit(3)
	}
}
