package com.lumen.core.vpn.tor

import com.lumen.core.config.tor.TorBridges
import org.json.JSONObject

/** Builds the torrc for one Tor run: a SOCKS listener and exactly one bridge. */
object TorrcBuilder {
    private fun clean(s: String) = s.replace('\n', ' ').replace('\r', ' ').trim()

    fun build(cfg: JSONObject, bridgeLines: List<String>, ptPorts: Map<String, Int>): String = buildString {
        // IPv6Traffic: Tor must accept IPv6 destinations from the VPN, otherwise it refuses them.
        appendLine("SocksPort 127.0.0.1:${cfg.getInt("socks")} IPv6Traffic")
        appendLine("Log notice file ${cfg.getString("log")}")
        appendLine("DisableNetwork 0")
        appendLine("ClientOnly 1")
        appendLine("AvoidDiskWrites 1")
        appendLine("SafeLogging 1")
        if (bridgeLines.isEmpty()) {
            appendLine("UseBridges 0")
        } else {
            appendLine("UseBridges 1")
            ptPorts.forEach { (t, p) -> appendLine("ClientTransportPlugin $t socks5 127.0.0.1:$p") }
            bridgeLines.forEach { appendLine("Bridge ${clean(TorBridges.normalize(it))}") }
        }
    }
}
