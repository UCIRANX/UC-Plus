package com.lumen.core.vpn.openconnect

import com.lumen.core.vpn.VpnLogBus
import org.infradead.libopenconnect.LibOpenConnect
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

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
 *
 * Every native call runs on one dedicated thread. The native side keeps per-thread JNI state, so
 * sign-in, the packet loop and the final free must not run on different threads. [cancel] is the
 * only call made from outside that thread; libopenconnect documents it as safe from any thread.
 */
class OpenConnectTunnel(
    private val settings: OpenConnectSettings,
    private val protect: (Int) -> Boolean,
) {
    private val native = Executors.newSingleThreadExecutor { r ->
        Thread(r, "openconnect-native").apply { isDaemon = true }
    }

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
    fun connect(): LibOpenConnect.IPInfo = onNative { connectNative() }

    /** Hands the TUN file descriptor to the engine. */
    fun attachTun(fd: Int): Boolean = onNative { session?.setupTunFD(fd) == 0 }

    /** Runs until the tunnel ends. Returns null when it was cancelled, else the reason it ended. */
    fun mainloop(): String? = onNative { loopNative() }

    fun cancel() {
        cancelled = true
        runCatching { session?.cancel() }
    }

    /**
     * Frees the engine. It is queued behind any loop still running on the native thread, so the
     * engine is never freed under a running loop. Safe to call more than once.
     */
    @Synchronized
    fun close() {
        if (native.isShutdown) return
        try {
            native.execute {
                val s = session
                session = null
                runCatching { s?.destroy() }
            }
        } catch (ignored: RejectedExecutionException) {
            // Already shut down by an earlier close.
        }
        native.shutdown()
    }

    private fun connectNative(): LibOpenConnect.IPInfo {
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

    private fun loopNative(): String? {
        val s = session ?: return "OpenConnect session is not open"
        while (!cancelled) {
            if (s.mainloop(10, LibOpenConnect.RECONNECT_INTERVAL_MIN) < 0) break
        }
        return if (cancelled) null else "the OpenConnect tunnel ended"
    }

    /** Runs [block] on the native thread and returns its result, rethrowing its failure. */
    private fun <T> onNative(block: () -> T): T {
        val future = try {
            native.submit(Callable { block() })
        } catch (ignored: RejectedExecutionException) {
            throw IllegalStateException("OpenConnect: the session is already closed")
        }
        try {
            return future.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
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
