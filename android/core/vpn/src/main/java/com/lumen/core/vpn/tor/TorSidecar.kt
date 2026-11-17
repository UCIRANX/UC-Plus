package com.lumen.core.vpn.tor

import android.content.Context
import com.lumen.core.config.tor.TorCountries
import com.lumen.core.config.tor.torSlotPort
import com.lumen.core.vpn.VpnLogBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Connects through Tor. Several bridges are tried at once, each in its own Tor instance
 * ("slot"). The first instance whose traffic really passes an HTTP check wins; the others
 * are stopped. Without the tor binary only one bridge is tried at a time.
 */
object TorSidecar {
    private const val TAG = "TOR"
    private const val SLOT_TIMEOUT_MS = 240_000L
    private const val COUNTRY_CIRCUIT_MS = 150_000L
    private const val RACE_TIMEOUT_MS = 480_000L
    private const val POLL_MS = 500L
    const val MAX_PARALLEL = 3
    private val bootstrapRe = Regex("Bootstrapped (\\d+)%")

    /** One bridge to try. */
    data class Candidate(val transport: String, val line: String)

    /** The instance that connected: its SOCKS port is where the VPN sends traffic. */
    class Winner(val port: Int, val transport: String, val line: String)

    private val _progress = MutableStateFlow(-1)

    /** Bootstrap percent of the best running instance, or -1 when no Tor run is starting. */
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val slotPct = ConcurrentHashMap<Int, Int>()

    /**
     * Tries [candidates] and returns the first working one. [exitCountry] "" means any exit.
     * Throws with a readable reason when none works in time.
     */
    suspend fun connect(context: Context, candidates: List<Candidate>, exitCountry: String): Winner {
        require(candidates.isNotEmpty()) { "No Tor bridge to try" }
        val country = TorCountries.normalize(exitCountry)
        val geoOk = if (country.isNotEmpty()) {
            if (GeoIpInstaller.install(context) == null) {
                throw IllegalStateException("GeoIP files are missing from this build, so an exit country cannot be chosen")
            }
            true
        } else {
            false
        }
        stop(context)
        val binary = TorRunnerService.torBinary(context)
        if (candidates.size > 1 && !binary.exists()) {
            val nearby = runCatching {
                File(context.applicationInfo.nativeLibraryDir).list()?.filter { it.contains("tor") }?.joinToString(", ")
            }.getOrNull().orEmpty()
            VpnLogBus.warning(TAG, "tor binary ${binary.name} is not packaged; trying one bridge at a time. Found: $nearby")
        }
        val slots = if (candidates.size > 1 && binary.exists()) min(MAX_PARALLEL, candidates.size) else 1
        val queue = ConcurrentLinkedQueue(candidates)
        val remaining = AtomicInteger(slots)
        val winnerSlot = AtomicInteger(-1)
        val done = CompletableDeferred<Winner>()
        val failures = ConcurrentLinkedQueue<String>()
        _progress.value = 0
        VpnLogBus.info(
            TAG,
            "Trying ${candidates.size} bridge(s) on $slots instance(s)" +
                if (country.isNotEmpty()) ", exit ${country.uppercase()}" else ""
        )
        val winner = withTimeoutOrNull(RACE_TIMEOUT_MS) {
            coroutineScope {
                val jobs = (0 until slots).map { slot ->
                    launch(Dispatchers.IO) {
                        try {
                            while (!done.isCompleted) {
                                val next = queue.poll() ?: break
                                val w = runSlot(context, slot, next, country, geoOk, failures)
                                if (w != null) {
                                    // Only the first working slot may win; a late one stops itself.
                                    if (winnerSlot.compareAndSet(-1, slot)) done.complete(w) else stopSlot(context, slot)
                                    break
                                }
                            }
                        } finally {
                            if (remaining.decrementAndGet() == 0) {
                                done.completeExceptionally(
                                    IllegalStateException(failures.lastOrNull() ?: "No Tor bridge connected")
                                )
                            }
                        }
                    }
                }
                val result = done.await()
                jobs.forEachIndexed { slot, job -> if (slot != winnerSlot.get()) job.cancel() }
                result
            }
        }
        if (winner == null) {
            stop(context)
            throw IllegalStateException("No Tor bridge connected in time (${failures.size} failed)")
        }
        _progress.value = 100
        VpnLogBus.info(TAG, "Tor is ready on 127.0.0.1:${winner.port} via ${winner.transport}")
        return winner
    }

    /** Runs one bridge in [slot]. Returns the winner, or null after stopping the slot. */
    private suspend fun runSlot(
        context: Context,
        slot: Int,
        cand: Candidate,
        country: String,
        geoOk: Boolean,
        failures: ConcurrentLinkedQueue<String>,
    ): Winner? {
        val port = torSlotPort(slot)
        val dir = TorRunnerService.slotDir(context, slot).apply { mkdirs() }
        val log = File(dir, "tor.log")
        var result: Winner? = null
        try {
            stopIfActive(context, slot)
            waitForPortFree(port)
            log.delete()
            TorRunnerService.stateFile(context, slot).delete()
            slotPct[slot] = 0
            publish()
            val cfg = JSONObject()
                .put("socks", port)
                .put("log", log.absolutePath)
                .put("bridges", JSONArray().put(cand.line))
                .put("exitCountry", country)
            if (geoOk) {
                GeoIpInstaller.install(context)?.let { g ->
                    cfg.put("geoip", g.v4.absolutePath)
                    g.v6?.let { cfg.put("geoip6", it.absolutePath) }
                }
            }
            TorRunnerService.start(context, slot, cfg.toString())
            result = waitForCircuit(context, slot, port, cand, log, country)
        } catch (c: CancellationException) {
            stopSlot(context, slot)
            throw c
        } catch (e: Exception) {
            val reason = "${cand.transport}: ${e.message ?: e.javaClass.simpleName}"
            failures.add(reason)
            VpnLogBus.warning(TAG, reason)
        }
        if (result == null) stopSlot(context, slot)
        return result
    }

    /** Waits for bootstrap, then checks that traffic really leaves through the circuit. */
    private suspend fun waitForCircuit(
        context: Context,
        slot: Int,
        port: Int,
        cand: Candidate,
        log: File,
        country: String,
    ): Winner? {
        val start = System.currentTimeMillis()
        val deadline = start + SLOT_TIMEOUT_MS
        var best = -1
        var lastChange = start
        val stallMs = if (cand.transport == "snowflake" || cand.transport == "meek_lite") 100_000L else 50_000L
        val stateFile = TorRunnerService.stateFile(context, slot)
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_MS)
            val state = runCatching { stateFile.readText().trim() }.getOrDefault("")
            if (state.startsWith("error:")) throw IllegalStateException(state.removePrefix("error:"))
            val text = runCatching { log.readText() }.getOrDefault("")
            text.lines().lastOrNull { it.contains("[err]") }?.let {
                throw IllegalStateException("Tor: ${it.substringAfter("] ").take(160)}")
            }
            val pct = bootstrapRe.findAll(text).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val now = System.currentTimeMillis()
            if (pct > best) {
                best = pct
                lastChange = now
                slotPct[slot] = pct
                publish()
            }
            if (pct >= 100) {
                if (probe(port)) return Winner(port, cand.transport, cand.line)
                if (country.isEmpty()) {
                    throw IllegalStateException("${cand.transport}: connected, but the internet check failed")
                }
                // A circuit that must exit in one country takes longer to build; keep checking for a while.
                val until = now + COUNTRY_CIRCUIT_MS
                while (System.currentTimeMillis() < until) {
                    delay(2_000)
                    if (probe(port)) return Winner(port, cand.transport, cand.line)
                }
                throw IllegalStateException("${cand.transport}: no exit in ${country.uppercase()} answered")
            }
            if (now - lastChange > stallMs) throw IllegalStateException("${cand.transport}: stopped at $best%")
        }
        throw IllegalStateException("${cand.transport}: timed out at $best%")
    }

    private suspend fun probe(port: Int): Boolean = withContext(Dispatchers.IO) { TorProbe.check(port) }

    private fun publish() {
        _progress.value = slotPct.values.maxOrNull() ?: 0
    }

    private fun stopSlot(context: Context, slot: Int) {
        TorRunnerService.stop(context, slot)
        slotPct.remove(slot)
        publish()
    }

    /** Stops [slot] only when it is still running, so idle slots do not start processes for nothing. */
    private fun stopIfActive(context: Context, slot: Int) {
        val state = runCatching { TorRunnerService.stateFile(context, slot).readText().trim() }.getOrDefault("")
        if (state.isNotEmpty() && state != "stopped" && !state.startsWith("error:")) stopSlot(context, slot)
    }

    /** Stops every running Tor instance. Safe to call when nothing is running. */
    fun stop(context: Context) {
        for (slot in 0 until MAX_PARALLEL) stopIfActive(context, slot)
        slotPct.clear()
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
