package com.lumen.core.vpn.tor

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import com.lumen.core.config.tor.TorBridges
import org.json.JSONArray
import org.json.JSONObject
import org.torproject.jni.TorService
import java.io.File

/**
 * Runs one Tor instance ("slot") in its own process, so several instances can bootstrap in
 * parallel. Slot 0 runs in ":tor", slots 1 and 2 in ":tor1" and ":tor2" (see the subclasses).
 *
 * Preferred path: start the tor binary that ships with tor-android (libtor.so) with a torrc
 * that is ours, in a per-slot data directory. Fallback, only for slot 0 and only when that
 * binary is missing: the shared TorService, as before.
 *
 * Stopping kills the whole process, so every start is clean.
 */
open class TorRunnerService : Service() {

    companion object {
        const val ACTION_START = "com.lumen.core.vpn.tor.action.START"
        const val ACTION_STOP = "com.lumen.core.vpn.tor.action.STOP"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_SLOT = "slot"

        fun slotDir(ctx: Context, slot: Int) = File(ctx.filesDir, "tor/slot$slot")

        /** Written by the service, read by [TorSidecar]: "starting", "running", "stopped" or "error:…". */
        fun stateFile(ctx: Context, slot: Int) = File(slotDir(ctx, slot), "runner.state")

        /** The tor executable shipped by tor-android, extracted next to the app's native libraries. */
        fun torBinary(ctx: Context) = File(ctx.applicationInfo.nativeLibraryDir, "libtor.so")

        fun classFor(slot: Int): Class<out TorRunnerService> = when (slot) {
            1 -> TorRunnerService1::class.java
            2 -> TorRunnerService2::class.java
            else -> TorRunnerService::class.java
        }

        fun start(ctx: Context, slot: Int, cfg: String) {
            ctx.startService(
                Intent(ctx, classFor(slot)).setAction(ACTION_START)
                    .putExtra(EXTRA_CONFIG, cfg).putExtra(EXTRA_SLOT, slot)
            )
        }

        fun stop(ctx: Context, slot: Int) {
            runCatching {
                ctx.startService(Intent(ctx, classFor(slot)).setAction(ACTION_STOP).putExtra(EXTRA_SLOT, slot))
            }
        }
    }

    private var slot = 0
    private var pt: PtManager? = null
    private var proc: Process? = null
    private var bound = false
    private var started = false
    @Volatile private var stopping = false
    private val main = Handler(Looper.getMainLooper())

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            if (!started) {
                started = true
                slot = intent.getIntExtra(EXTRA_SLOT, 0)
                installCrashHandler()
                val cfg = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                Thread({ launch(cfg) }, "lumen-tor-start").start()
            }
        } else {
            slot = intent?.getIntExtra(EXTRA_SLOT, slot) ?: slot
            shutdown()
        }
        return START_NOT_STICKY
    }

    /**
     * A crash in this process (Tor, a transport or a native library) would show the system
     * "keeps stopping" dialog. Record the reason for the app instead and exit quietly.
     */
    private fun installCrashHandler() {
        val dir = slotDir(this, slot)
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val reason = "${e.javaClass.simpleName}: ${e.message.orEmpty().take(160)}"
            runCatching { writeState("error:Tor process crashed ($reason)") }
            runCatching { File(dir, "tor.log").appendText("[crash] ${e.stackTraceToString().take(4000)}\n") }
            Process.killProcess(Process.myPid())
        }
    }

    private fun launch(json: String) {
        try {
            writeState("starting")
            val cfg = JSONObject(json)
            val arr = cfg.optJSONArray("bridges") ?: JSONArray()
            val lines = (0 until arr.length()).map { TorBridges.normalize(arr.getString(it)) }
            val dir = slotDir(this, slot).apply { mkdirs() }
            val torBin = torBinary(this)
            when {
                torBin.exists() -> launchProcess(cfg, lines, dir, torBin)
                slot == 0 -> launchTorService(cfg, lines)
                else -> writeState("error:This build has no tor binary for parallel connections")
            }
        } catch (t: Throwable) {
            writeState("error:" + (t.message ?: t.javaClass.simpleName))
        }
    }

    /** Starts the pluggable transports this bridge list needs and returns their local ports. */
    private fun startTransports(lines: List<String>): Map<String, Int> {
        val ports = LinkedHashMap<String, Int>()
        val types = lines.map { TorBridges.typeOf(it) }.filter { it != "vanilla" }.toSet()
        if (types.isNotEmpty()) {
            val m = PtManager(File(slotDir(this, slot), "pt_state"))
            pt = m
            lines.firstOrNull { TorBridges.typeOf(it) == "snowflake" }?.let { m.configureSnowflake(it) }
            for (t in types) ports[t] = m.start(t)
        }
        return ports
    }

    private fun launchProcess(cfg: JSONObject, lines: List<String>, dir: File, torBin: File) {
        val ports = startTransports(lines)
        val dataDir = File(dir, "data").apply { mkdirs() }
        val torrc = File(dir, "torrc")
        torrc.writeText(TorrcBuilder.build(cfg, lines, ports, dataDir))
        val out = File(dir, "stdout.log")
        val p = ProcessBuilder(torBin.absolutePath, "-f", torrc.absolutePath)
            .directory(dataDir)
            .redirectErrorStream(true)
            .redirectOutput(out)
            .start()
        proc = p
        // An early exit is reported at once, instead of waiting for the SOCKS port to time out.
        Thread({
            val code = p.waitFor()
            if (!stopping) writeState("error:Tor exited with code $code. ${lastLine(out)}")
        }, "lumen-tor-wait").start()
        waitForReady(cfg)
    }

    /** Fallback for slot 0 when no tor binary is packaged: the shared TorService. */
    private fun launchTorService(cfg: JSONObject, lines: List<String>) {
        val ports = startTransports(lines)
        val torrc = torrcFile()
        torrc.parentFile?.mkdirs()
        torrc.writeText(TorrcBuilder.build(cfg, lines, ports))
        main.post {
            bound = try {
                // Binding alone does not start Tor. ACTION_START is required by tor-android.
                startService(Intent(this, TorService::class.java).setAction(TorService.ACTION_START))
                bindService(Intent(this, TorService::class.java), conn, Context.BIND_AUTO_CREATE)
            } catch (e: Throwable) {
                writeState("error:" + (e.message ?: e.javaClass.simpleName))
                false
            }
        }
        waitForReady(cfg)
    }

    private fun waitForReady(cfg: JSONObject) {
        val socks = cfg.optInt("socks", 0)
        if (socks <= 0 || !waitForPort(socks, 60_000L)) {
            writeState("error:Tor SOCKS port did not open")
            shutdown()
            return
        }
        writeState("running")
    }

    private fun lastLine(f: File): String = runCatching {
        f.readLines().lastOrNull { it.isNotBlank() }.orEmpty().take(160)
    }.getOrDefault("")

    private fun waitForPort(port: Int, timeoutMs: Long): Boolean {
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            try {
                java.net.Socket("127.0.0.1", port).use { return true }
            } catch (_: Exception) {
                Thread.sleep(250)
            }
        }
        return false
    }

    private fun torrcFile(): File = try {
        TorService::class.java.getMethod("getTorrc", Context::class.java).invoke(null, this) as File
    } catch (e: Throwable) {
        File(getDir("TorService", Context.MODE_PRIVATE), "torrc")
    }

    private fun writeState(s: String) {
        runCatching {
            val f = stateFile(this, slot)
            f.parentFile?.mkdirs()
            f.writeText(s)
        }
    }

    private fun shutdown() {
        stopping = true
        try {
            if (bound) unbindService(conn)
        } catch (_: Exception) {
        }
        bound = false
        runCatching { stopService(Intent(this, TorService::class.java)) }
        runCatching { proc?.destroy() }
        runCatching { pt?.stopAll() }
        writeState("stopped")
        stopSelf()
        main.postDelayed({ Process.killProcess(Process.myPid()) }, 300)
    }
}

/** Parallel slot 1, runs in its own process ":tor1". */
class TorRunnerService1 : TorRunnerService()

/** Parallel slot 2, runs in its own process ":tor2". */
class TorRunnerService2 : TorRunnerService()
