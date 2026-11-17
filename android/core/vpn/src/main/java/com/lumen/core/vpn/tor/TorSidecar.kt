package com.lumen.core.vpn.tor

import android.content.Context
import com.lumen.core.config.tor.TorBridges
import com.lumen.core.vpn.VpnLogBus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Runs one Tor node: starts Tor with its bridge in the ":tor" process and returns once Tor has
 * bootstrapped. The VPN then routes through Tor's SOCKS port like through any other local proxy.
 */
object TorSidecar {
    private const val TAG = "TOR"
    private const val BOOTSTRAP_TIMEOUT_MS = 240_000L
    private const val POLL_MS = 500L
    private val bootstrapRe = Regex("Bootstrapped (\\d+)%")

    private val _progress = MutableStateFlow(-1)

    /** Bootstrap percent of the Tor run in progress, or -1 when no Tor run is starting. */
    val progress: StateFlow<Int> = _progress.asStateFlow()

    @Volatile private var running = false

    private fun logFile(ctx: Context) = File(ctx.filesDir, "tor/tor.log")

    /**
     * Starts Tor with [line] as its only bridge. Throws with a readable reason when Tor fails
     * or does not reach 100% before the timeout.
     */
    suspend fun start(context: Context, line: String, transport: String) {
        stop(context)
        waitForPortFree(TorBridges.SOCKS_PORT)
        val log = logFile(context)
        log.parentFile?.mkdirs()
        log.delete()
        TorRunnerService.stateFile(context).delete()
        val cfg = JSONObject()
            .put("socks", TorBridges.SOCKS_PORT)
            .put("log", log.absolutePath)
            .put("bridges", JSONArray().put(line))
        _progress.value = 0
        VpnLogBus.info(TAG, "Starting Tor with a $transport bridge")
        try {
            TorRunnerService.start(context, cfg.toString())
        } catch (e: Exception) {
            stop(context)
            throw IllegalStateException("Tor could not be started: ${e.message ?: e.javaClass.simpleName}")
        }
        running = true
        val deadline = System.currentTimeMillis() + BOOTSTRAP_TIMEOUT_MS
        while (true) {
            currentCoroutineContext().ensureActive()
            val state = runCatching { TorRunnerService.stateFile(context).readText().trim() }.getOrDefault("")
            if (state.startsWith("error:")) fail(context, state.removePrefix("error:"))
            val text = runCatching { log.readText() }.getOrDefault("")
            text.lines().lastOrNull { it.contains("[err]") }?.let { fail(context, "Tor: ${it.substringAfter("] ").take(160)}") }
            val pct = bootstrapRe.findAll(text).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (pct > _progress.value) _progress.value = pct
            if (pct >= 100) break
            if (System.currentTimeMillis() > deadline) fail(context, "Tor did not finish connecting in time (${_progress.value}%)")
            delay(POLL_MS)
        }
        _progress.value = 100
        VpnLogBus.info(TAG, "Tor is ready on 127.0.0.1:${TorBridges.SOCKS_PORT}")
    }

    private fun fail(context: Context, reason: String): Nothing {
        stop(context)
        VpnLogBus.warning(TAG, reason)
        throw IllegalStateException(reason)
    }

    /** Stops the Tor run, if any. Safe to call when nothing is running. */
    fun stop(context: Context) {
        if (running || TorRunnerService.stateFile(context).exists()) {
            running = false
            TorRunnerService.stop(context)
        }
        _progress.value = -1
    }

    /** The old process releases the SOCKS port a moment after it is told to stop. */
    private suspend fun waitForPortFree(port: Int) {
        repeat(20) {
            if (portFree(port)) return
            delay(250)
        }
    }

    private fun portFree(port: Int): Boolean = try {
        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).close()
        true
    } catch (_: Exception) {
        false
    }
}
