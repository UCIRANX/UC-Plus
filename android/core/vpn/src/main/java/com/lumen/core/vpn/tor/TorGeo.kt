package com.lumen.core.vpn.tor

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * GeoIP data for choosing a Tor exit country. The bridges branch of the repository holds one
 * small file per country (built by tools/build_geo.py). The app downloads only the country the
 * user picks, so the APK does not grow with the number of countries.
 *
 * Tor treats an address that is missing from its GeoIP data as "unknown", so a file that holds
 * only one country's ranges is enough for ExitNodes {code} with StrictNodes.
 */
object TorGeo {
    private const val BASE = "https://raw.githubusercontent.com/UCIRANX/UC-Plus/bridges/geo/"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val WEEK_MS = 7 * DAY_MS

    /** Files for one country: IPv4 ranges, and IPv6 ranges when the country has any. */
    class Paths(val v4: File, val v6: File?)

    private fun dir(ctx: Context): File = File(ctx.filesDir, "tor/geo").apply { mkdirs() }

    /** Country codes the repository offers, from its index. Falls back to the cached list offline. */
    fun countries(ctx: Context): List<String> {
        val index = File(dir(ctx), "index.txt")
        if (!index.exists() || System.currentTimeMillis() - index.lastModified() > DAY_MS) {
            runCatching { download(BASE + "index.txt", index) }
        }
        return runCatching {
            index.readLines().map { it.trim().lowercase() }.filter { CODE.matches(it) }.distinct()
        }.getOrDefault(emptyList())
    }

    /** Makes sure the files for [code] are present and fresh, and returns them. Throws when they cannot be had. */
    fun fetch(ctx: Context, code: String): Paths {
        val c = code.lowercase()
        if (!CODE.matches(c)) throw IllegalArgumentException("Unknown country $code")
        val d = dir(ctx)
        val v4 = File(d, "$c.geoip")
        val v6 = File(d, "$c.geoip6")
        val fresh = v4.exists() && v4.length() > 0 && System.currentTimeMillis() - v4.lastModified() < WEEK_MS
        if (!fresh) {
            try {
                download(BASE + "$c.geoip", v4)
            } catch (e: Exception) {
                if (!v4.exists()) {
                    throw IllegalStateException("GeoIP for ${c.uppercase()} could not be downloaded: ${e.message}")
                }
                // An old copy is still good enough for an exit choice.
            }
            // IPv6 is optional: a country without IPv6 ranges has no file.
            runCatching { download(BASE + "$c.geoip6", v6) }
        }
        return Paths(v4, v6.takeIf { it.exists() && it.length() > 0 })
    }

    private fun download(url: String, dest: File) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            conn.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        } finally {
            conn.disconnect()
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) throw IllegalStateException("could not save ${dest.name}")
    }

    private val CODE = Regex("[a-z]{2}")
}
