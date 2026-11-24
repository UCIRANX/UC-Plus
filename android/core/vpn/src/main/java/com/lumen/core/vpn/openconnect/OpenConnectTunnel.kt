package com.lumen.core.vpn.openconnect

import com.lumen.core.vpn.VpnLogBus
import org.infradead.libopenconnect.LibOpenConnect
import org.json.JSONObject

/** The OpenConnect settings of a start: the `_lumen_openconnect` object of the start config. */
data class OpenConnectSettings(
    val server: String,
    val port: Int,
    val user: String,
    val password: String,
    val group: String,
    val pin: String,
) {
    companion object {
        const val KEY = "_lumen_openconnect"

        fun fromConfig(configJson: String): OpenConnectSettings? {
            val o = runCatching { JSONObject(configJson).optJSONObject(KEY) }.getOrNull() ?: return null
            return OpenConnectSettings(
                server = o.optString("server"),
                port = o.optInt("port", 443),
                user = o.optString("user"),
                password = o.optString("password"),
                group = o.optString("group"),
                pin = o.optString("pin"),
            )
        }

        /** Netmask text ("255.255.255.0") to prefix length; 32 when it cannot be read. */
        fun prefixOf(mask: String?): Int =
            mask?.split(".")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 4 }
                ?.sumOf { Integer.bitCount(it) } ?: 32
    }
}

/**
 * One libopenconnect session. libopenconnect owns the tunnel: it logs in, opens the CSTP tunnel,
 * and reads and writes packets on the TUN file descriptor the VPN service gives it.
 */
class OpenConnectTunnel(
    private val settings: OpenConnectSettings,
    private val protect: (Int) -> Boolean,
) {
    @Volatile
    private var session: OcSession? = null

    @Volatile
    private var cancelled = false

    /** True once the user (or the service) asked this session to stop. */
    val isCancelled: Boolean get() = cancelled

    /**
     * Logs in and opens the tunnel. Blocking. Throws IllegalStateException with a readable message.
     * A [cancel] during the sign-in makes the blocking calls return, and the steps after them are skipped.
     */
    fun connect(): LibOpenConnect.IPInfo {
        val loadError = runCatching { System.loadLibrary("openconnect") }.exceptionOrNull()
        if (loadError != null) {
            throw fail("the OpenConnect library is not in this build: ${loadError.message}")
        }
        val s = OcSession(settings, protect)
        session = s
        s.setLogLevel(LibOpenConnect.PRG_INFO)
        if (s.setProtocol("anyconnect") != 0) throw fail("the AnyConnect protocol was refused")
        runCatching { s.setSystemTrust(true) }
        val host = settings.server.removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (s.parseURL("https://$host:${settings.port}") != 0) throw fail("the server address could not be read")
        if (cancelled) throw Stopped()
        if (s.obtainCookie() != 0) throw if (cancelled) Stopped() else fail("sign-in failed: check the username, password and group")
        if (cancelled) throw Stopped()
        if (s.makeCSTPConnection() != 0) throw if (cancelled) Stopped() else fail("the server accepted sign-in but did not open the tunnel")
        if (cancelled) throw Stopped()
        return s.getIPInfo() ?: throw fail("the server sent no address settings")
    }

    /** Hands the TUN file descriptor to the engine. */
    fun attachTun(fd: Int): Boolean = session?.setupTunFD(fd) == 0

    /** Runs until the tunnel ends. Returns null when it was cancelled, else the reason it ended. */
    fun mainloop(): String? {
        val s = session ?: return "OpenConnect session is not open"
        while (!cancelled) {
            if (s.mainloop(10, LibOpenConnect.RECONNECT_INTERVAL_MIN) < 0) break
        }
        return if (cancelled) null else "the OpenConnect tunnel ended"
    }

    fun cancel() {
        cancelled = true
        runCatching { session?.cancel() }
    }

    /**
     * Frees the engine. Call it only after [mainloop] has returned, or when no loop was ever
     * started. Safe to call more than once: the engine is freed once.
     */
    @Synchronized
    fun close() {
        val s = session
        session = null
        runCatching { s?.destroy() }
    }

    private fun fail(reason: String): IllegalStateException {
        VpnLogBus.error(TAG, "OpenConnect: $reason")
        return IllegalStateException("OpenConnect: $reason")
    }

    /** The user stopped the connection while it was being made. This is not an error. */
    private class Stopped : java.util.concurrent.CancellationException("OpenConnect stopped by the user")

    private companion object {
        const val TAG = "OpenConnect"
    }
}
