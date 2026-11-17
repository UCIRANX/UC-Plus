package com.lumen.core.vpn.tor

import com.lumen.core.config.tor.TorBridges
import org.json.JSONObject
import java.io.File

/** Builds the torrc for one Tor run: a SOCKS listener and one bridge. */
object TorrcBuilder {
    private fun clean(s: String) = s.replace('\n', ' ').replace('\r', ' ').trim()

    /**
     * [dataDir] is set when tor runs as its own process (parallel slots). The shared TorService
     * path leaves it null because the service chooses its own data directory.
     */
    fun build(cfg: JSONObject, bridgeLines: List<String>, ptPorts: Map<String, Int>, dataDir: File? = null): String = buildString {
        // IPv6Traffic: Tor must accept IPv6 destinations from the VPN, otherwise it refuses them.
        appendLine("SocksPort 127.0.0.1:${cfg.getInt("socks")} IPv6Traffic")
        dataDir?.let { appendLine("DataDirectory ${clean(it.absolutePath)}") }
        appendLine("Log notice file ${cfg.getString("log")}")
        appendLine("DisableNetwork 0")
        appendLine("ClientOnly 1")
        appendLine("AvoidDiskWrites 1")
        appendLine("SafeLogging 1")
        // Country lookups need Tor's GeoIP database. Without it, ExitNodes would match nothing,
        // so the restriction is only written when the database was installed.
        val geoip = cfg.optString("geoip_file")
        if (geoip.isNotEmpty()) appendLine("GeoIPFile ${clean(geoip)}")
        cfg.optString("geoip6_file").takeIf { it.isNotEmpty() }?.let { appendLine("GeoIPv6File ${clean(it)}") }
        val exit = com.lumen.core.config.tor.TorCountries.normalize(cfg.optString("exit_country"))
        if (exit.isNotEmpty() && geoip.isNotEmpty()) {
            // The IP seen on the internet is the exit relay's. Bridges only pick the entry.
            appendLine("ExitNodes {$exit}")
            appendLine("StrictNodes 1")
        }
        if (bridgeLines.isEmpty()) {
            appendLine("UseBridges 0")
        } else {
            appendLine("UseBridges 1")
            ptPorts.forEach { (t, p) -> appendLine("ClientTransportPlugin $t socks5 127.0.0.1:$p") }
            bridgeLines.forEach { appendLine("Bridge ${clean(TorBridges.normalize(it))}") }
        }
    }
}
