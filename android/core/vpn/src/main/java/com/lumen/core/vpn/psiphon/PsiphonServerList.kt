package com.lumen.core.vpn.psiphon

import android.content.Context
import com.lumen.core.vpn.VpnLogBus
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.InflaterInputStream

/**
 * Supplies the Psiphon server entries handed to the tunnel core at start. The same sources as UC-MAX,
 * in this order:
 *   1. the GitHub mirror (refreshed every 12 hours by UC-MAX's workflow)
 *   2. the Amazon S3 original published by Psiphon
 *   3. the last list that was downloaded successfully (cached on disk)
 * Each entry is a hex-encoded line. The APK carries no entries.
 */
object PsiphonServerList {

    const val MIRROR_URL =
        "https://raw.githubusercontent.com/UCIRANX/UC-MAX/main/psiphon-mirror/server_list_compressed"
    const val ORIGINAL_URL =
        "https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed"

    private const val TAG = "PSIPHON"
    private const val CACHE_FILE = "psiphon_entries_cache.txt"
    private const val TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 16 * 1024 * 1024

    /** Blocking; call from a background thread. */
    fun resolve(context: Context): List<String> {
        for ((name, url) in listOf("mirror" to MIRROR_URL, "s3" to ORIGINAL_URL)) {
            val entries = download(url)
            if (entries != null) {
                saveCache(context, entries)
                VpnLogBus.info(TAG, "${entries.size} server entries from $name")
                return entries
            }
        }
        val cached = loadCache(context)
        if (cached != null) {
            VpnLogBus.warning(TAG, "download failed, using ${cached.size} cached entries")
            return cached
        }
        VpnLogBus.warning(TAG, "download failed and there is no cache")
        return emptyList()
    }

    /** The file is zlib-compressed JSON whose "data" field holds hex lines, one server per line. */
    private fun download(url: String): List<String>? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                VpnLogBus.warning(TAG, "$url returned HTTP ${connection.responseCode}")
                return null
            }
            val compressed = readBounded(connection.inputStream)
            val plain = InflaterInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
            val data = JSONObject(String(plain, Charsets.UTF_8)).getString("data")
            val lines = data.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty() || !isValidEntry(lines.first())) {
                VpnLogBus.warning(TAG, "$url has no usable server entries")
                null
            } else {
                lines
            }
        } catch (e: Exception) {
            VpnLogBus.warning(TAG, "$url failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Reads at most MAX_BYTES with a loop: readNBytes needs API 33, and minSdk is 26. */
    private fun readBounded(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(8192)
            var total = 0
            while (total < MAX_BYTES) {
                val read = stream.read(buffer, 0, minOf(buffer.size, MAX_BYTES - total))
                if (read < 0) break
                out.write(buffer, 0, read)
                total += read
            }
        }
        return out.toByteArray()
    }

    /** A usable entry decodes from hex to text that contains a JSON object. */
    private fun isValidEntry(hex: String): Boolean = try {
        String(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), Charsets.UTF_8).contains('{')
    } catch (e: Exception) {
        false
    }

    private fun saveCache(context: Context, entries: List<String>) {
        try {
            val target = File(context.filesDir, CACHE_FILE)
            val temp = File(context.filesDir, "$CACHE_FILE.tmp")
            temp.writeText(entries.joinToString("\n"))
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (e: Exception) {
            VpnLogBus.warning(TAG, "could not save the entries cache: ${e.message}")
        }
    }

    private fun loadCache(context: Context): List<String>? = try {
        File(context.filesDir, CACHE_FILE)
            .takeIf { it.exists() }
            ?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}
