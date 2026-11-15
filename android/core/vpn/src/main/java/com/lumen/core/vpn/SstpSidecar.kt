package com.lumen.core.vpn

import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Runs an SSTP node: a protected TCP relay to the SSTP server, plus the
 * `libsstpbridge.so` process that speaks SSTP through that relay and serves SOCKS5 on
 * loopback. Sing-box dials that SOCKS port like any other local proxy.
 *
 * The relay exists because the bridge's own sockets would otherwise be routed into the
 * tunnel. Only the relay's upstream socket is protected, so the bridge dials loopback.
 */
class SstpSidecar(
    private val bridgeBinary: File,
    private val protect: (Socket) -> Boolean
) {
    private companion object {
        const val TAG = "SSTP"
        const val READY_TIMEOUT_MS = 60_000L
        const val NO_RESPONSE_MESSAGE = "SSTP server did not respond"
        const val POLL_MS = 250L
        const val MAX_TAIL_LINES = 20
    }

    private var relay: SstpTcpRelay? = null
    private var process: Process? = null
    private val tail = ArrayDeque<String>()
    private var socksPort = 0

    /**
     * Starts the relay and bridge on the given loopback ports and returns once the SOCKS
     * port accepts connections. The ports are chosen per start by the caller, so they never
     * collide with the user's local inbound or with another app.
     */
    suspend fun start(
        host: String,
        port: Int,
        username: String,
        password: String,
        relayPort: Int,
        socksPort: Int
    ) {
        stop()
        this.socksPort = socksPort
        val upstream = SstpTcpRelay(relayPort, host, port, protect) { VpnLogBus.warning(TAG, it) }
        upstream.start()
        relay = upstream

        val command = listOf(
            bridgeBinary.absolutePath,
            "-relay-port", relayPort.toString(),
            "-server-name", host,
            "-socks-port", socksPort.toString()
        )
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        builder.environment()["SSTP_USERNAME"] = username
        builder.environment()["SSTP_PASSWORD"] = password
        val child = builder.start()
        process = child
        thread(isDaemon = true, name = "sstp-bridge-log") {
            runCatching {
                child.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(tail) {
                        tail.addLast(line)
                        while (tail.size > MAX_TAIL_LINES) tail.removeFirst()
                    }
                    VpnLogBus.info(TAG, line)
                }
            }
        }
        VpnLogBus.info(TAG, "Connecting to SSTP server $host:$port")

        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (true) {
            if (!child.isAlive) {
                // Keep the bridge's own error in the log only; the user sees the same short
                // message other protocols show when their server does not answer.
                VpnLogBus.warning(TAG, "SSTP bridge exited: ${lastLine()}")
                throw IllegalStateException(NO_RESPONSE_MESSAGE)
            }
            if (socksReady()) break
            if (System.currentTimeMillis() > deadline) {
                VpnLogBus.warning(TAG, "SSTP connection timed out: ${lastLine()}")
                stop()
                throw IllegalStateException(NO_RESPONSE_MESSAGE)
            }
            delay(POLL_MS)
        }
        VpnLogBus.info(TAG, "SSTP tunnel is ready on 127.0.0.1:$socksPort")
    }

    fun stop() {
        process?.let { runCatching { it.destroy() } }
        process = null
        relay?.let { runCatching { it.stop() } }
        relay = null
    }

    private fun socksReady(): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", socksPort), 500)
            true
        }
    } catch (_: IOException) {
        false
    }

    private fun lastLine(): String = synchronized(tail) { tail.lastOrNull() ?: "no output" }
}

/**
 * Loopback TCP relay that dials [host]:[port] through a socket protected from the VPN.
 * It forwards raw bytes in both directions, so TLS to the SSTP server is end to end.
 */
internal class SstpTcpRelay(
    private val localPort: Int,
    private val host: String,
    private val port: Int,
    private val protect: (Socket) -> Boolean,
    private val log: (String) -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private val open = java.util.Collections.synchronizedSet(mutableSetOf<Socket>())

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress("127.0.0.1", localPort))
        server = socket
        thread(isDaemon = true, name = "sstp-relay-accept") {
            while (running.get()) {
                val client = try { socket.accept() } catch (_: IOException) { break }
                thread(isDaemon = true, name = "sstp-relay-conn") { serve(client) }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        synchronized(open) {
            open.forEach { runCatching { it.close() } }
            open.clear()
        }
    }

    private fun serve(client: Socket) {
        val upstream = Socket()
        track(client)
        track(upstream)
        try {
            // An unbound Socket has no file descriptor yet, so VpnService.protect would fail.
            // Binding to an ephemeral wildcard address creates it; protect must run before connect.
            upstream.bind(InetSocketAddress(0))
            if (!protect(upstream)) throw IOException("could not protect the SSTP server socket")
            upstream.connect(InetSocketAddress(host, port), 15_000)
            val toServer = thread(isDaemon = true, name = "sstp-relay-up") { pipe(client, upstream) }
            pipe(upstream, client)
            toServer.join()
        } catch (e: Exception) {
            // Record why the server side failed; the bridge only sees the closed socket.
            log("relay to $host:$port failed: ${e.javaClass.simpleName}: ${e.message ?: "no message"}")
        } finally {
            runCatching { client.close() }
            runCatching { upstream.close() }
            synchronized(open) {
                open.remove(client)
                open.remove(upstream)
            }
        }
    }

    private fun track(socket: Socket) {
        synchronized(open) { open.add(socket) }
    }

    private fun pipe(from: Socket, to: Socket) {
        val buffer = ByteArray(32 * 1024)
        try {
            val input = from.getInputStream()
            val output = to.getOutputStream()
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                output.flush()
            }
        } catch (_: IOException) {
            // Either side closing ends the pipe.
        } finally {
            runCatching { to.shutdownOutput() }
        }
    }
}
