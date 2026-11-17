package com.lumen.core.vpn.tor

import com.lumen.core.config.tor.TorBridges
import com.lumen.core.config.tor.TorCountries
import org.json.JSONObject
import java.io.File

/** Builds the torrc for one Tor run: a SOCKS listener, one bridge and an optional exit country. */
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
        // Without GeoIP files Tor cannot map relays to countries, so an exit country never matches.
        cfg.optString("geoip").takeIf { it.isNotBlank() }?.let { appendLine("GeoIPFile ${clean(it)}") }
        cfg.optString("geoip6").takeIf { it.isNotBlank() }?.let { appendLine("GeoIPv6File ${clean(it)}") }
        if (bridgeLines.isEmpty()) {
            appendLine("UseBridges 0")
        } else {
            appendLine("UseBridges 1")
            ptPorts.forEach { (t, p) -> appendLine("ClientTransportPlugin $t socks5 127.0.0.1:$p") }
            bridgeLines.forEach { appendLine("Bridge ${clean(TorBridges.normalize(it))}") }
        }
        // StrictNodes: when no exit of that country is reachable, Tor fails instead of using another country.
        val country = TorCountries.normalize(cfg.optString("exitCountry"))
        if (country.isNotEmpty()) {
            appendLine("ExitNodes {$country}")
            appendLine("StrictNodes 1")
        }
    }
}
