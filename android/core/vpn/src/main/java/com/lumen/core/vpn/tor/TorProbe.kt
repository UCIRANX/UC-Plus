package com.lumen.core.vpn.tor

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Checks that a Tor SOCKS port really carries traffic: a plain HTTP request through a circuit
 * must get an answer. Bootstrap 100% alone does not prove that a circuit can exit.
 */
object TorProbe {
    private const val HOST = "connectivitycheck.gstatic.com"

    fun check(port: Int): Boolean {
        return try {
            Socket().use { s ->
                s.soTimeout = 20_000
                s.connect(InetSocketAddress("127.0.0.1", port), 5_000)
                val out = s.getOutputStream()
                val inp = s.getInputStream()
                // Greeting: SOCKS5, one method, no authentication.
                out.write(byteArrayOf(5, 1, 0))
                out.flush()
                val auth = inp.readNBytes(2)
                if (auth.size < 2 || auth[1].toInt() != 0) return false
                // CONNECT to the host by name, so Tor resolves it on the exit side.
                val hostBytes = HOST.toByteArray(Charsets.US_ASCII)
                val req = ByteArrayOutputStream()
                req.write(byteArrayOf(5, 1, 0, 3, hostBytes.size.toByte()))
                req.write(hostBytes)
                req.write(byteArrayOf(0, 80))
                out.write(req.toByteArray())
                out.flush()
                val rep = inp.readNBytes(4)
                if (rep.size < 4 || rep[1].toInt() != 0) return false
                // Skip the bound address that follows the reply.
                when (rep[3].toInt()) {
                    1 -> inp.readNBytes(6)
                    4 -> inp.readNBytes(18)
                    3 -> {
                        val n = inp.read()
                        if (n < 0) return false
                        inp.readNBytes(n + 2)
                    }
                    else -> return false
                }
                out.write("GET /generate_204 HTTP/1.1\r\nHost: $HOST\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
                val status = BufferedReader(InputStreamReader(inp, Charsets.US_ASCII)).readLine().orEmpty()
                status.startsWith("HTTP/1.1 204") || status.startsWith("HTTP/1.1 200") || status.startsWith("HTTP/1.0 204")
            }
        } catch (_: Exception) {
            false
        }
    }
}
