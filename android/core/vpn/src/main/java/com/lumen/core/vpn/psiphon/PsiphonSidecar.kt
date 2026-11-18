package com.lumen.core.vpn.psiphon

import android.content.Context
import android.util.Base64
import com.lumen.core.config.psiphon.PsiphonLinks
import com.lumen.core.vpn.VpnLogBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Runs the Psiphon tunnel core (libpsiphon.so, built from psiphon-tunnel-core in CI) as one process
 * and waits until it serves SOCKS5 on loopback and the tunnel is up. The core writes notices as JSON
 * lines (stderr is merged into stdout). Errors and unreadable lines are logged, so a failed start
 * says why. The protocol choice follows UC-MAX's TunnelManager; Beast Mode sets AggressiveEstablishment.
 */
object PsiphonSidecar {
    private const val TAG = "PSIPHON"
    private const val CONNECT_TIMEOUT_MS = 120_000L
    private const val BINARY = "libpsiphon.so"
    private const val PROPAGATION_CHANNEL_ID = "FFFFFFFFFFFFFFFF"
    private const val SPONSOR_ID = "FFFFFFFFFFFFFFFF"
    private const val SIGNATURE_PUBLIC_KEY = "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42KcotLFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7GstZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1OgeF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xalKxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="

    private val CONDUIT_PROTOCOLS = listOf(
        "INPROXY-WEBRTC-SSH",
        "INPROXY-WEBRTC-OSSH",
        "INPROXY-WEBRTC-TLS-OSSH",
        "INPROXY-WEBRTC-UNFRONTED-MEEK-OSSH",
        "INPROXY-WEBRTC-UNFRONTED-MEEK-HTTPS-OSSH",
        "INPROXY-WEBRTC-UNFRONTED-MEEK-SESSION-TICKET-OSSH",
        "INPROXY-WEBRTC-FRONTED-MEEK-OSSH",
        "INPROXY-WEBRTC-FRONTED-MEEK-HTTP-OSSH",
        "INPROXY-WEBRTC-QUIC-OSSH",
        "INPROXY-WEBRTC-FRONTED-MEEK-QUIC-OSSH",
        "INPROXY-WEBRTC-SHADOWSOCKS-OSSH",
    )

    private val CDN_PROTOCOLS = listOf(
        "FRONTED-MEEK-CDN-OSSH",
        "FRONTED-MEEK-CDN-HTTP-OSSH",
        "FRONTED-MEEK-CDN-QUIC-OSSH",
    )

    private val DIRECT_PROTOCOLS = listOf(
        "SSH",
        "OSSH",
        "TLS-OSSH",
        "UNFRONTED-MEEK-OSSH",
        "UNFRONTED-MEEK-HTTPS-OSSH",
        "UNFRONTED-MEEK-SESSION-TICKET-OSSH",
        "QUIC-OSSH",
        "SHADOWSOCKS-OSSH",
        "FRONTED-MEEK-OSSH",
        "FRONTED-MEEK-CDN-OSSH",
        "FRONTED-MEEK-HTTP-OSSH",
        "FRONTED-MEEK-CDN-HTTP-OSSH",
        "FRONTED-MEEK-QUIC-OSSH",
        "FRONTED-MEEK-CDN-QUIC-OSSH",
    )

    /** Spare loopback ports for test tunnels; two probes run at a time, each on its own port. */
    private val probePorts = ConcurrentLinkedQueue((10860..10869).toList())
    private val probeGate = Semaphore(10)
    private const val PROBE_TIMEOUT_MS = 15_000L

    /** Region test results of this session (a failed test removes its region). */
    private val regionResults = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val activeRegionProbes = java.util.concurrent.atomic.AtomicInteger(0)

    /** The best region test time so far, or -1 when none is known. */
    fun bestRegionPing(): Int = regionResults.values.minOrNull() ?: -1

    /** How many region tests are still running. */
    fun regionProbesRunning(): Int = activeRegionProbes.get()
    private const val LIST_REUSE_MS = 10 * 60_000L
    private val listLock = Mutex()

    @Volatile
    private var preparedList: File? = null

    @Volatile
    private var preparedAt = 0L

    @Volatile
    private var process: Process? = null

    /** The last error the core reported for the current start, shown when the start fails. */
    @Volatile
    private var lastProblem = ""

    /**
     * Starts the tunnel for [region] ("" = auto) and returns once it is connected.
     * Throws with a readable reason when it cannot.
     */
    suspend fun connect(context: Context, region: String) {
        stop()
        lastProblem = ""
        val binary = File(context.applicationInfo.nativeLibraryDir, BINARY)
        if (!binary.exists()) throw IllegalStateException("The Psiphon core is not packaged in this build")
        val dir = File(context.filesDir, "psiphon").apply { mkdirs() }
        val entryFile = serverListFile(context)
        val configFile = File(dir, "psiphon.config")
        withContext(Dispatchers.IO) { configFile.writeText(buildConfig(context, dir, region)) }
        val portReady = CompletableDeferred<Unit>()
        val tunnelUp = CompletableDeferred<Unit>()
        val started = try {
            ProcessBuilder(
                binary.absolutePath, "-config", configFile.absolutePath,
                "-dataRootDirectory", dir.absolutePath, "-serverList", entryFile.absolutePath,
            ).directory(dir).redirectErrorStream(true).start()
        } catch (e: IOException) {
            throw IllegalStateException("Psiphon could not start: ${e.message}")
        }
        process = started
        Thread({ readNotices(started, portReady, tunnelUp) }, "psiphon-notices").apply {
            isDaemon = true
            start()
        }
        try {
            val ready = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                portReady.await()
                tunnelUp.await()
                true
            }
            if (ready != true) throw IllegalStateException("Psiphon did not connect in time")
        } catch (e: CancellationException) {
            stop()
            throw e
        } catch (e: IllegalStateException) {
            stop()
            throw e
        } catch (e: Exception) {
            stop()
            throw IllegalStateException(e.message ?: "Psiphon failed")
        }
        VpnLogBus.info(TAG, "tunnel is up on 127.0.0.1:${PsiphonLinks.SOCKS_PORT}")
    }

    /**
     * The server entries as one file. Downloaded once and reused for [LIST_REUSE_MS], so a ping batch
     * and a connect do not each download the list again.
     */
    private suspend fun serverListFile(context: Context): File = listLock.withLock {
        val known = preparedList
        if (known != null && known.exists() && System.currentTimeMillis() - preparedAt < LIST_REUSE_MS) {
            return@withLock known
        }
        val entries = withContext(Dispatchers.IO) { PsiphonServerList.resolve(context) }
        if (entries.isEmpty()) throw IllegalStateException("No Psiphon server entries could be downloaded")
        val file = File(File(context.filesDir, "psiphon").apply { mkdirs() }, "psiphon_server_entries.txt")
        withContext(Dispatchers.IO) { file.writeText(entries.joinToString("\n")) }
        preparedList = file
        preparedAt = System.currentTimeMillis()
        file
    }

    /**
     * A brief test tunnel for [region] ("" = auto): its own core on a spare port, timed until the tunnel
     * is up, then stopped. Returns the milliseconds, or -1 when no tunnel came up. The running tunnel is not touched.
     */
    suspend fun probeRegion(context: Context, region: String): Int {
        if (region.isEmpty()) return probeNow(context, region)
        activeRegionProbes.incrementAndGet()
        return try {
            probeNow(context, region)
        } finally {
            activeRegionProbes.decrementAndGet()
        }
    }

    private suspend fun probeNow(context: Context, region: String): Int {
        val binary = File(context.applicationInfo.nativeLibraryDir, BINARY)
        if (!binary.exists()) return -1
        val entryFile = try {
            serverListFile(context)
        } catch (e: IllegalStateException) {
            VpnLogBus.warning(TAG, e.message ?: "no server entries")
            return -1
        }
        return probeGate.withPermit {
            val port = probePorts.poll() ?: return@withPermit -1
            try {
                runProbe(context, binary, entryFile, region, port).also { ms ->
                    if (region.isNotEmpty()) {
                        if (ms > 0) regionResults[region] = ms else regionResults.remove(region)
                    }
                }
            } finally {
                probePorts.add(port)
            }
        }
    }

    private suspend fun runProbe(context: Context, binary: File, entryFile: File, region: String, port: Int): Int {
        val dir = File(context.filesDir, "psiphon/probe-$port").apply { mkdirs() }
        val configFile = File(dir, "psiphon.config")
        withContext(Dispatchers.IO) { configFile.writeText(buildConfig(context, dir, region, port, probe = true)) }
        val started = try {
            ProcessBuilder(
                binary.absolutePath, "-config", configFile.absolutePath,
                "-dataRootDirectory", dir.absolutePath, "-serverList", entryFile.absolutePath,
            ).directory(dir).redirectErrorStream(true).start()
        } catch (e: IOException) {
            VpnLogBus.warning(TAG, "probe ${region.ifEmpty { "auto" }} could not start: ${e.message}")
            return -1
        }
        val label = region.ifEmpty { "auto" }
        val tunnelUp = CompletableDeferred<Unit>()
        val began = System.nanoTime()
        var reported = 0
        Thread({
            try {
                BufferedReader(InputStreamReader(started.inputStream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (isTunnelUpNotice(line)) tunnelUp.complete(Unit)
                        else if (reported < 6) probeProblem(line)?.let {
                            reported++
                            VpnLogBus.warning(TAG, "probe $label: $it")
                        }
                    }
                }
            } catch (_: IOException) {
            } finally {
                tunnelUp.completeExceptionally(IOException("the probe core stopped"))
            }
        }, "psiphon-probe-$port").apply {
            isDaemon = true
            start()
        }
        return try {
            val up = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                tunnelUp.await()
                true
            }
            val ms = if (up == true) ((System.nanoTime() - began) / 1_000_000).toInt() else -1
            VpnLogBus.info(TAG, "probe $label: " + (if (ms > 0) "$ms ms" else "no tunnel in time"))
            ms
        } catch (e: IOException) {
            -1
        } finally {
            runCatching {
                started.destroy()
                started.waitFor(3, TimeUnit.SECONDS)
                if (started.isAlive) started.destroyForcibly()
            }
        }
    }

    /** The text worth logging from a core line: a non-JSON line, or an Error notice's message. */
    private fun probeProblem(line: String): String? {
        if (line.isBlank() || line.contains("networkid.Get failed")) return null
        val notice = runCatching { JSONObject(line) }.getOrNull() ?: return line.take(300)
        if (notice.optString("noticeType") != "Error") return null
        return (notice.optJSONObject("data") ?: JSONObject()).optString("message").take(300).takeIf { it.isNotEmpty() }
    }

    private fun isTunnelUpNotice(line: String): Boolean {
        val notice = runCatching { JSONObject(line) }.getOrNull() ?: return false
        return when (notice.optString("noticeType")) {
            "ActiveTunnel" -> true
            "Tunnels" -> (notice.optJSONObject("data") ?: JSONObject()).optInt("count") > 0
            else -> false
        }
    }

    /** Stops the core process. Safe to call when nothing runs. */
    fun stop() {
        val current = process ?: return
        process = null
        runCatching {
            current.destroy()
            current.waitFor(3, TimeUnit.SECONDS)
            if (current.isAlive) current.destroyForcibly()
        }
    }

    private fun readNotices(p: Process, portReady: CompletableDeferred<Unit>, tunnelUp: CompletableDeferred<Unit>) {
        try {
            BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    dispatch(line, portReady, tunnelUp)
                }
            }
        } catch (_: IOException) {
        } finally {
            val code = runCatching { p.waitFor() }.getOrDefault(-1)
            val reason = lastProblem.ifEmpty { "exit code $code" }
            if (process === p) VpnLogBus.warning(TAG, "the core exited: $reason")
            val gone = IllegalStateException("Psiphon exited: $reason")
            portReady.completeExceptionally(gone)
            tunnelUp.completeExceptionally(gone)
        }
    }

    private fun dispatch(line: String, portReady: CompletableDeferred<Unit>, tunnelUp: CompletableDeferred<Unit>) {
        val notice = runCatching { JSONObject(line) }.getOrNull()
        if (notice == null) {
            if (line.isNotBlank()) {
                lastProblem = line.take(300)
                VpnLogBus.info(TAG, line.take(300))
            }
            return
        }
        val data = notice.optJSONObject("data") ?: JSONObject()
        when (notice.optString("noticeType")) {
            "Error" -> {
                val message = data.optString("message").ifEmpty { line.take(300) }
                lastProblem = message.take(300)
                VpnLogBus.warning(TAG, message.take(300))
            }
            "ListeningSocksProxyPort" -> {
                if (data.optInt("port") == PsiphonLinks.SOCKS_PORT) {
                    portReady.complete(Unit)
                } else {
                    portReady.completeExceptionally(IllegalStateException("Psiphon opened port ${data.optInt("port")}"))
                }
            }
            "SocksProxyPortInUse" -> portReady.completeExceptionally(
                IllegalStateException("port ${PsiphonLinks.SOCKS_PORT} is in use")
            )
            "ActiveTunnel" -> tunnelUp.complete(Unit)
            "Tunnels" -> {
                if (data.optInt("count") > 0) tunnelUp.complete(Unit)
            }
        }
    }

    private fun buildConfig(
        context: Context, dir: File, region: String,
        port: Int = PsiphonLinks.SOCKS_PORT, probe: Boolean = false,
    ): String {
        val json = JSONObject()
            .put("PropagationChannelId", PROPAGATION_CHANNEL_ID)
            .put("SponsorId", SPONSOR_ID)
            .put("LocalSocksProxyPort", port)
            .put("LocalHttpProxyPort", 0)
            .put("EgressRegion", region)
            .put("EmitDiagnosticNotices", true)
            .put("DataRootDirectory", dir.absolutePath)
            .put("RemoteServerListDownloadFilename", File(dir, "psiphon_remote_server_list").absolutePath)
            // The core needs one list URL. A probe gets a closed local port instead, so it never
            // downloads a list; its entries come from the -serverList file.
            .put("RemoteServerListUrls",
                if (probe) JSONArray().put(JSONObject().put("URL", encodeUrl("http://127.0.0.1:9/")))
                else remoteServerListUrls())
            .put("RemoteServerListSignaturePublicKey", SIGNATURE_PUBLIC_KEY)
        // The same four choices as TunnelManager: Conduit, CDN fronting, direct, and auto.
        when (PsiphonPrefs.protocolMode(context)) {
            PsiphonPrefs.MODE_CONDUIT -> json.put("LimitTunnelProtocols", JSONArray(CONDUIT_PROTOCOLS))
            PsiphonPrefs.MODE_CDN_FRONTING -> {
                json.put("LimitTunnelProtocols", JSONArray(CDN_PROTOCOLS)).put("DisableTactics", true)
                putCdnFronting(json, context)
            }
            PsiphonPrefs.MODE_DIRECT -> {
                json.put("LimitTunnelProtocols", JSONArray(DIRECT_PROTOCOLS)).put("DisableTactics", true)
                putCdnFronting(json, context)
            }
            else -> putCdnFronting(json, context)
        }
        if (probe) json.put("DisableTactics", true)
        if (PsiphonPrefs.beastMode(context)) json.put("AggressiveEstablishment", true)
        return json.toString()
    }

    private fun putCdnFronting(json: JSONObject, context: Context) {
        val overrides = cdnFrontingOverrides(context)
        if (overrides.length() > 0) {
            json.put("FrontedMeekDialOverrides", overrides)
            json.put("FrontedMeekDialOverridesProbability", 1.0)
        }
    }

    /** One dial override per healthy edge IP. The first typed SNI hostname is used, else the scanner's probe name. */
    private fun cdnFrontingOverrides(context: Context): JSONArray {
        val overrides = JSONArray()
        val sni = PsiphonPrefs.parseSni(PsiphonPrefs.cdnSni(context)).firstOrNull() ?: CdnIpScanner.PROBE_SNI
        CdnIpScanner.savedIps(context).forEachIndexed { index, ip ->
            overrides.put(
                JSONObject()
                    .put("OverrideID", "uc-plus-edge-$index")
                    .put("MatchDialAddressRegexes", JSONArray().put(".*"))
                    .put("DialAddresses", JSONArray().put(ip))
                    .put("SNIServerName", sni)
                    .put("VerifyServerNames", JSONArray().put(sni).put("a248.e.akamai.net"))
                    .put("ALPNProtocols", JSONArray().put("http/1.1"))
                    .put("TLSProfile", "Chrome-83")
            )
        }
        return overrides
    }

    /** Each list URL is base64-encoded inside an object with a "URL" field, as the core expects. */
    private fun remoteServerListUrls(): JSONArray = JSONArray()
        .put(JSONObject().put("URL", encodeUrl(PsiphonServerList.MIRROR_URL)))
        .put(JSONObject().put("URL", encodeUrl(PsiphonServerList.ORIGINAL_URL)))

    private fun encodeUrl(url: String): String =
        Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}
