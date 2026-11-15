package sstp

import (
	"encoding/binary"
	"math/bits"
)

// md4Sum implements MD4 (RFC 1320). MS-CHAPv2 needs it for the NT password hash;
// the standard library does not provide it.
func md4Sum(data []byte) [16]byte {
	s := [4]uint32{0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476}
	msg := append(append([]byte{}, data...), 0x80)
	for len(msg)%64 != 56 {
		msg = append(msg, 0)
	}
	var lengthBits [8]byte
	binary.LittleEndian.PutUint64(lengthBits[:], uint64(len(data))*8)
	msg = append(msg, lengthBits[:]...)

	f := func(x, y, z uint32) uint32 { return (x & y) | (^x & z) }
	g := func(x, y, z uint32) uint32 { return (x & y) | (x & z) | (y & z) }
	h := func(x, y, z uint32) uint32 { return x ^ y ^ z }

	for off := 0; off < len(msg); off += 64 {
		var x [16]uint32
		for i := range x {
			x[i] = binary.LittleEndian.Uint32(msg[off+4*i:])
		}
		saved := s
		step := func(fn func(uint32, uint32, uint32) uint32, k int, shift int, add uint32) {
			t := bits.RotateLeft32(s[0]+fn(s[1], s[2], s[3])+x[k]+add, shift)
			s[0], s[1], s[2], s[3] = s[3], t, s[1], s[2]
		}
		shifts1 := [4]int{3, 7, 11, 19}
		for i := 0; i < 16; i++ {
			step(f, i, shifts1[i%4], 0)
		}
		order2 := [16]int{0, 4, 8, 12, 1, 5, 9, 13, 2, 6, 10, 14, 3, 7, 11, 15}
		shifts2 := [4]int{3, 5, 9, 13}
		for i := 0; i < 16; i++ {
			step(g, order2[i], shifts2[i%4], 0x5A827999)
		}
		order3 := [16]int{0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15}
		shifts3 := [4]int{3, 9, 11, 15}
		for i := 0; i < 16; i++ {
			step(h, order3[i], shifts3[i%4], 0x6ED9EBA1)
		}
		for i := range s {
			s[i] += saved[i]
		}
	}
	var out [16]byte
	for i := range s {
		binary.LittleEndian.PutUint32(out[4*i:], s[i])
	}
	return out
}
