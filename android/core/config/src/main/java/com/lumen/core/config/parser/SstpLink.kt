package com.lumen.core.config.parser

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * SSTP nodes are stored as `sstp://user:pass@host:port#CC`. The engine never dials the
 * SSTP server directly: the VPN service runs a protected relay to the server and the
 * `libsstpbridge.so` process, which serves SOCKS5 on [SOCKS_PORT]. Sing-box then uses a
 * plain socks outbound to that loopback port.
 */
data class SstpParams(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val countryCode: String
)

object SstpLink {
    /** Loopback SOCKS5 port that the SSTP bridge serves. */
    const val SOCKS_PORT = 18998

    /** Loopback port of the protected TCP relay that the bridge dials to reach the server. */
    const val RELAY_PORT = 18997

    fun build(host: String, port: Int, username: String, password: String, countryCode: String): String {
        val user = URLEncoder.encode(username, "UTF-8")
        val pass = URLEncoder.encode(password, "UTF-8")
        val cc = URLEncoder.encode(countryCode, "UTF-8")
        return "sstp://$user:$pass@$host:$port#$cc"
    }

    fun parse(link: String): SstpParams? {
        val text = link.trim()
        if (!text.startsWith("sstp://", ignoreCase = true)) return null
        val body = text.substring("sstp://".length)
        val fragmentAt = body.indexOf('#')
        val beforeFragment = if (fragmentAt >= 0) body.substring(0, fragmentAt) else body
        val country = if (fragmentAt >= 0) URLDecoder.decode(body.substring(fragmentAt + 1), "UTF-8") else ""
        val at = beforeFragment.lastIndexOf('@')
        if (at < 0) return null
        val userInfo = beforeFragment.substring(0, at)
        val hostPort = beforeFragment.substring(at + 1)
        val colon = userInfo.indexOf(':')
        if (colon < 0) return null
        val user = URLDecoder.decode(userInfo.substring(0, colon), "UTF-8")
        val pass = URLDecoder.decode(userInfo.substring(colon + 1), "UTF-8")
        val portColon = hostPort.lastIndexOf(':')
        if (portColon <= 0) return null
        val host = hostPort.substring(0, portColon)
        val port = hostPort.substring(portColon + 1).toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        return SstpParams(host = host, port = port, username = user, password = pass, countryCode = country)
    }
}
