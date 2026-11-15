package sstp

import (
	"crypto/des"
	"crypto/sha1"
	"encoding/binary"
	"errors"
	"unicode/utf16"
)

// NTPasswordHash = MD4(UTF-16LE(password)) (RFC 2759 section 8.3).
func NTPasswordHash(password string) [16]byte {
	units := utf16.Encode([]rune(password))
	buf := make([]byte, 2*len(units))
	for i, u := range units {
		binary.LittleEndian.PutUint16(buf[2*i:], u)
	}
	return md4Sum(buf)
}

// ChallengeHash = SHA1(PeerChallenge | AuthenticatorChallenge | UserName)[0:8] (RFC 2759 section 8.2).
func ChallengeHash(peer, authenticator [16]byte, user string) [8]byte {
	h := sha1.New()
	h.Write(peer[:])
	h.Write(authenticator[:])
	h.Write([]byte(user))
	var out [8]byte
	copy(out[:], h.Sum(nil))
	return out
}

// desKeyFrom7 expands 7 bytes into an 8-byte DES key (RFC 2759 section 8.5).
// Go's DES ignores the parity bit, so the 7-bit groups are shifted into place.
func desKeyFrom7(k []byte) []byte {
	key := make([]byte, 8)
	key[0] = k[0] >> 1
	key[1] = ((k[0] & 0x01) << 6) | (k[1] >> 2)
	key[2] = ((k[1] & 0x03) << 5) | (k[2] >> 3)
	key[3] = ((k[2] & 0x07) << 4) | (k[3] >> 4)
	key[4] = ((k[3] & 0x0f) << 3) | (k[4] >> 5)
	key[5] = ((k[4] & 0x1f) << 2) | (k[5] >> 6)
	key[6] = ((k[5] & 0x3f) << 1) | (k[6] >> 7)
	key[7] = k[6] & 0x7f
	for i := range key {
		key[i] <<= 1
	}
	return key
}

// ChallengeResponse returns the 24-byte response for an 8-byte challenge and
// the 16-byte NT password hash (RFC 2759 section 8.5).
func ChallengeResponse(challenge [8]byte, passwordHash [16]byte) [24]byte {
	var padded [21]byte
	copy(padded[:], passwordHash[:])
	var out [24]byte
	for i := 0; i < 3; i++ {
		c, err := des.NewCipher(desKeyFrom7(padded[7*i : 7*i+7]))
		if err != nil {
			panic(err) // the key is always 8 bytes
		}
		c.Encrypt(out[8*i:8*i+8], challenge[:])
	}
	return out
}

// NTResponse computes the 24-byte NT-Response for a user and password.
func NTResponse(authenticator, peer [16]byte, user, password string) [24]byte {
	challenge := ChallengeHash(peer, authenticator, user)
	return ChallengeResponse(challenge, NTPasswordHash(password))
}

// MasterKey = first 16 bytes of SHA1(PasswordHashHash | NTResponse | Magic1)
// (RFC 3079 section 4.3, GetMasterKey). PasswordHashHash = MD4(PasswordHash).
func MasterKey(passwordHash [16]byte, ntResponse [24]byte) [16]byte {
	pwHashHash := md4Sum(passwordHash[:])
	h := sha1.New()
	h.Write(pwHashHash[:])
	h.Write(ntResponse[:])
	h.Write([]byte("This is the MPPE Master Key"))
	var out [16]byte
	copy(out[:], h.Sum(nil))
	return out
}

var (
	magic2  = []byte("On the client side, this is the send key; on the server side, it is the receive key.")
	magic3  = []byte("On the client side, this is the receive key; on the server side, it is the send key.")
	shsPad1 = make([]byte, 40)
	shsPad2 = func() []byte {
		b := make([]byte, 40)
		for i := range b {
			b[i] = 0xf2
		}
		return b
	}()
)

// asymmetricStartKey = first 16 bytes of SHA1(MasterKey | SHSpad1 | Magic | SHSpad2),
// where Magic is chosen by direction and role (RFC 3079 section 4.3).
func asymmetricStartKey(master [16]byte, isSend, isServer bool) [16]byte {
	magic := magic3
	if (isSend && !isServer) || (!isSend && isServer) {
		magic = magic2
	}
	h := sha1.New()
	h.Write(master[:])
	h.Write(shsPad1)
	h.Write(magic)
	h.Write(shsPad2)
	var out [16]byte
	copy(out[:], h.Sum(nil))
	return out
}

// ClientHLAK is the 32-byte higher-layer authentication key for an MS-CHAPv2
// client: MasterSendKey | MasterReceiveKey (MS-SSTP 3.2.5.2.4, RFC 3079).
func ClientHLAK(passwordHash [16]byte, ntResponse [24]byte) [32]byte {
	master := MasterKey(passwordHash, ntResponse)
	send := asymmetricStartKey(master, true, false)
	recv := asymmetricStartKey(master, false, false)
	var out [32]byte
	copy(out[:16], send[:])
	copy(out[16:], recv[:])
	return out
}

// MSCHAPv2AuthenticationOption is the LCP option asking the peer to authenticate
// with CHAP using the MS-CHAPv2 algorithm (RFC 2433/2759).
func MSCHAPv2AuthenticationOption() []byte {
	return []byte{LCPOptionAuthentication, 5, byte(PPPCHAP >> 8), byte(PPPCHAP & 0xff), CHAPAlgorithmMSCHAPv2}
}

// ParseCHAPChallenge returns the 16-byte MS-CHAPv2 Authenticator Challenge from a
// CHAP Challenge payload (value-size, value, name).
func ParseCHAPChallenge(payload []byte) ([16]byte, error) {
	var out [16]byte
	if len(payload) < 17 || payload[0] != 16 {
		return out, errors.New("sstp: invalid CHAP challenge")
	}
	copy(out[:], payload[1:17])
	return out, nil
}

// EncodeMSCHAPv2Response builds a CHAP Response payload: value-size 49,
// PeerChallenge, 8 reserved zero bytes, NT-Response, flags 0, then the user name.
func EncodeMSCHAPv2Response(peer [16]byte, ntResponse [24]byte, username string) []byte {
	value := make([]byte, 0, 49)
	value = append(value, peer[:]...)
	value = append(value, make([]byte, 8)...)
	value = append(value, ntResponse[:]...)
	value = append(value, 0)
	out := append([]byte{49}, value...)
	return append(out, []byte(username)...)
}
