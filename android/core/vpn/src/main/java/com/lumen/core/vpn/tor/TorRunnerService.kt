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
 * Runs inside the ":tor" process: starts the pluggable transport for the bridge, writes the
 * torrc and starts tor. Stopping kills the whole process, so every start is clean.
 */
class TorRunnerService : Service() {

    companion object {
        const val ACTION_START = "com.lumen.core.vpn.tor.action.START"
        const val ACTION_STOP = "com.lumen.core.vpn.tor.action.STOP"
        const val EXTRA_CONFIG = "config"

        /** Written by this service, read by [TorSidecar]: "starting", "running", "stopped" or "error:…". */
        fun stateFile(ctx: Context) = File(ctx.filesDir, "tor/runner.state")

        fun start(ctx: Context, cfg: String) {
            ctx.startService(Intent(ctx, TorRunnerService::class.java).setAction(ACTION_START).putExtra(EXTRA_CONFIG, cfg))
        }

        fun stop(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, TorRunnerService::class.java).setAction(ACTION_STOP)) }
        }
    }

    private var pt: PtManager? = null
    private var bound = false
    private var started = false
    private val main = Handler(Looper.getMainLooper())

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate() {
        super.onCreate()
        // A crash in this process (Tor, a transport or a native library) shows the system
        // "keeps stopping" dialog. Record the reason for the app instead and exit quietly.
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val reason = "${e.javaClass.simpleName}: ${e.message.orEmpty().take(160)}"
            runCatching { writeState("error:Tor process crashed ($reason)") }
            runCatching {
                File(filesDir, "tor/tor.log").appendText("[crash] ${e.stackTraceToString().take(4000)}\n")
            }
            Process.killProcess(Process.myPid())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            if (!started) {
                started = true
                val cfg = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                Thread({ launchTor(cfg) }, "lumen-tor-start").start()
            }
        } else {
            shutdown()
        }
        return START_NOT_STICKY
    }

    private fun launchTor(json: String) {
        try {
            writeState("starting")
            val cfg = JSONObject(json)
            val arr = cfg.optJSONArray("bridges") ?: JSONArray()
            val lines = (0 until arr.length()).map { TorBridges.normalize(arr.getString(it)) }
            val ports = LinkedHashMap<String, Int>()
            val types = lines.map { TorBridges.typeOf(it) }.filter { it != "vanilla" }.toSet()
            if (types.isNotEmpty()) {
                val m = PtManager(File(filesDir, "tor/pt_state"))
                pt = m
                lines.firstOrNull { TorBridges.typeOf(it) == "snowflake" }?.let { m.configureSnowflake(it) }
                for (t in types) ports[t] = m.start(t)
            }
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
            val socks = cfg.optInt("socks", 0)
            if (socks <= 0 || !waitForPort(socks, 60_000L)) {
                writeState("error:Tor SOCKS port did not open")
                shutdown()
                return
            }
            writeState("running")
        } catch (t: Throwable) {
            writeState("error:" + (t.message ?: t.javaClass.simpleName))
        }
    }

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
            val f = stateFile(this)
            f.parentFile?.mkdirs()
            f.writeText(s)
        }
    }

    private fun shutdown() {
        try {
            if (bound) unbindService(conn)
        } catch (_: Exception) {
        }
        bound = false
        runCatching { stopService(Intent(this, TorService::class.java)) }
        runCatching { pt?.stopAll() }
        writeState("stopped")
        stopSelf()
        main.postDelayed({ Process.killProcess(Process.myPid()) }, 500)
    }
}
