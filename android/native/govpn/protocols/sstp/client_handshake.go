package sstp

import (
	"crypto/rand"
	"errors"
	"net/netip"

	protocol "github.com/bclswl0827/govpn/protocols/sstp/internal"
)

func clientHandshake(framer *protocol.Framer, username, password string, certificateDER []byte, logf func(string, ...any)) (netip.Addr, error) {
	if err := framer.WriteControl(protocol.CallConnectRequest, protocol.Attribute{ID: protocol.AttrEncapsulatedProtocol, Value: []byte{0, protocol.ProtocolPPP}}); err != nil {
		return netip.Addr{}, err
	}
	logf("CALL_CONNECT_REQUEST sent")
	packet, err := framer.ReadPacket()
	if err != nil {
		return netip.Addr{}, err
	}
	if !packet.Control || packet.Message != protocol.CallConnectAck {
		return netip.Addr{}, errors.New("sstp: expected CALL_CONNECT_ACK")
	}
	bindingRequest, ok := protocol.AttributeValue(packet, protocol.AttrCryptoBindingRequest)
	if !ok || len(bindingRequest) != 36 {
		return netip.Addr{}, errors.New("sstp: invalid crypto-binding request")
	}
	hashProtocol := byte(protocol.HashSHA256)
	if bindingRequest[3]&protocol.HashSHA256 == 0 {
		hashProtocol = protocol.HashSHA1
	}
	if bindingRequest[3]&hashProtocol == 0 {
		return netip.Addr{}, errors.New("sstp: server offered no supported certificate hash")
	}
	logf("CALL_CONNECT_ACK received: certificate-hash-mask=%#02x", bindingRequest[3])

	authentication, err := negotiateClientLCP(framer, logf)
	if err != nil {
		return netip.Addr{}, err
	}
	var hlak []byte
	if authentication == protocol.PPPCHAP {
		hlak, err = msChapV2Authenticate(framer, username, password, logf)
	} else {
		err = papAuthenticate(framer, username, password, logf)
	}
	if err != nil {
		return netip.Addr{}, err
	}

	binding, err := protocol.CryptoBinding(hashProtocol, bindingRequest[4:], certificateDER, hlak)
	if err != nil {
		return netip.Addr{}, err
	}
	if err := framer.WriteControl(protocol.CallConnected, protocol.Attribute{ID: protocol.AttrCryptoBinding, Value: binding}); err != nil {
		return netip.Addr{}, err
	}
	logf("CALL_CONNECTED sent")
	return negotiateClientIPCP(framer, logf)
}

func papAuthenticate(framer *protocol.Framer, username, password string, logf func(string, ...any)) error {
	logf("PPP LCP negotiation completed: authentication=PAP")
	pap, err := protocol.EncodePAPRequest(2, username, password)
	if err != nil {
		return err
	}
	if err := framer.WriteData(protocol.EncodePPP(protocol.PPPPAP, pap)); err != nil {
		return err
	}
	logf("PPP PAP authentication request sent")
	result, err := readPPP(framer)
	if err != nil {
		return err
	}
	if _, err := protocol.ExpectControl(result, protocol.PPPPAP, 2); err != nil {
		return errors.New("sstp: PAP authentication rejected")
	}
	logf("PPP PAP authentication accepted")
	return nil
}

// msChapV2Authenticate runs MS-CHAPv2 (RFC 2759) and returns the 32-byte HLAK
// that the crypto binding needs (MS-SSTP 3.2.5.2.4, RFC 3079 MPPE keys).
func msChapV2Authenticate(framer *protocol.Framer, username, password string, logf func(string, ...any)) ([]byte, error) {
	logf("PPP LCP negotiation completed: authentication=MS-CHAPv2")
	challenge, err := readPPP(framer)
	if err != nil {
		return nil, err
	}
	control, err := protocol.ExpectControl(challenge, protocol.PPPCHAP, 1)
	if err != nil {
		return nil, err
	}
	authenticator, err := protocol.ParseCHAPChallenge(control.Payload)
	if err != nil {
		return nil, err
	}
	var peer [16]byte
	if _, err := rand.Read(peer[:]); err != nil {
		return nil, err
	}
	nt := protocol.NTResponse(authenticator, peer, username, password)
	if err := writePPPControl(framer, protocol.PPPCHAP, 2, control.ID, protocol.EncodeMSCHAPv2Response(peer, nt, username)); err != nil {
		return nil, err
	}
	logf("PPP MS-CHAPv2 response sent")
	result, err := readPPP(framer)
	if err != nil {
		return nil, err
	}
	verdict, err := protocol.ExpectControl(result, protocol.PPPCHAP, 3)
	if err != nil {
		return nil, errors.New("sstp: MS-CHAPv2 authentication rejected")
	}
	_ = verdict
	logf("PPP MS-CHAPv2 authentication accepted")
	hlak := protocol.ClientHLAK(protocol.NTPasswordHash(password), nt)
	return hlak[:], nil
}
