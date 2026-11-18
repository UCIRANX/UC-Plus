package com.lumen.core.vpn.psiphon

import android.content.Context
import android.util.Base64
import com.lumen.core.config.psiphon.PsiphonLinks
import com.lumen.core.vpn.VpnLogBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
        val entries = withContext(Dispatchers.IO) { PsiphonServerList.resolve(context) }
        if (entries.isEmpty()) throw IllegalStateException("No Psiphon server entries could be downloaded")
        val configFile = File(dir, "psiphon.config")
        val listFile = File(dir, "psiphon_server_entries.txt")
        withContext(Dispatchers.IO) {
            configFile.writeText(buildConfig(context, dir, region))
            listFile.writeText(entries.joinToString("\n"))
        }
        val portReady = CompletableDeferred<Unit>()
        val tunnelUp = CompletableDeferred<Unit>()
        val started = try {
            ProcessBuilder(
                binary.absolutePath, "-config", configFile.absolutePath,
                "-dataRootDirectory", dir.absolutePath, "-serverList", listFile.absolutePath,
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

    private fun buildConfig(context: Context, dir: File, region: String): String {
        val json = JSONObject()
            .put("PropagationChannelId", PROPAGATION_CHANNEL_ID)
            .put("SponsorId", SPONSOR_ID)
            .put("LocalSocksProxyPort", PsiphonLinks.SOCKS_PORT)
            .put("LocalHttpProxyPort", 0)
            .put("EgressRegion", region)
            .put("EmitDiagnosticNotices", true)
            .put("DataRootDirectory", dir.absolutePath)
            .put("RemoteServerListDownloadFilename", File(dir, "psiphon_remote_server_list").absolutePath)
            .put("RemoteServerListUrls", remoteServerListUrls())
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
