package com.lumen.core.config.tor

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Tor bridges as UC Plus servers. One bridge line is one server, stored as `tor://<encoded line>`.
 * Sing-box never speaks Tor: the VPN service starts Tor with that single bridge and sing-box
 * dials Tor's SOCKS port on [SOCKS_PORT], the same way the SSTP bridge works.
 */
object TorBridges {
    /** Loopback SOCKS5 port that the Tor sidecar serves. */
    const val SOCKS_PORT = 19060

    /** Built-in subscription that lists every bridge, one `tor://` link per line. */
    const val SUBSCRIPTION_URL = "https://raw.githubusercontent.com/UCIRANX/UC-Plus/bridges/tor.txt"

    /** Every transport a bridge list can contain. */
    val TYPES = listOf("webtunnel", "obfs4", "snowflake", "meek_lite", "vanilla")

    private const val PREFIX = "tor://"

    fun normalize(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("Bridge ", ignoreCase = true)) s = s.substring(7).trim()
        if (s.startsWith("meek-azure ")) s = "meek_lite " + s.removePrefix("meek-azure ")
        if (s.startsWith("meek ")) s = "meek_lite " + s.removePrefix("meek ")
        return s.replace(Regex("\\s+"), " ")
    }

    /** Transport of a bridge line. Vanilla bridges have no transport name, only an address. */
    fun typeOf(line: String): String {
        val first = normalize(line).substringBefore(' ')
        val c = first.firstOrNull() ?: return ""
        return if (c.isDigit() || c == '[') "vanilla" else first
    }

    fun isBridgeLine(line: String): Boolean = typeOf(line) in TYPES

    /** Address the bridge listens on, or null when the line has none that can be read. */
    fun hostPort(line: String): Pair<String, Int>? {
        val l = normalize(line)
        val type = typeOf(l)
        return runCatching {
            if (type == "webtunnel") {
                val url = URI(Regex("url=(\\S+)").find(l)!!.groupValues[1])
                val port = if (url.port != -1) url.port else if (url.scheme == "https") 443 else 80
                url.host.trim('[', ']') to port
            } else {
                val addr = if (type == "vanilla") l.substringBefore(' ') else l.split(' ')[1]
                addr.substringBeforeLast(':').trim('[', ']') to addr.substringAfterLast(':').toInt()
            }
        }.getOrNull()
    }

    /** Short name of the transport as users see it. */
    fun label(type: String): String = if (type == "meek_lite") "meek" else type

    /** Server name in the list. The transport comes first so the bridge type is visible at a glance. */
    fun displayName(type: String, host: String): String = "Tor · ${label(type)} · $host"

    fun encode(line: String): String =
        PREFIX + URLEncoder.encode(normalize(line), "UTF-8").replace("+", "%20")

    /** Bridge line stored in a `tor://` link, or null when the link is not a valid bridge. */
    fun decode(link: String): String? {
        val text = link.trim()
        if (!text.startsWith(PREFIX, ignoreCase = true)) return null
        val line = normalize(URLDecoder.decode(text.substring(PREFIX.length), "UTF-8"))
        return line.takeIf { isBridgeLine(it) && hostPort(it) != null }
    }
}
