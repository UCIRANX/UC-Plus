package com.lumen.core.vpn.tor

import android.content.Context
import java.io.File

/**
 * Copies the GeoIP files bundled in the APK (assets/geoip, fetched by the build from the Tor
 * source) to a real path, because Tor reads them from the file system.
 */
object GeoIpInstaller {
    class Paths(val v4: File, val v6: File?)

    private const val ASSET_DIR = "geoip"

    fun install(ctx: Context): Paths? {
        val bundled = runCatching { ctx.assets.list(ASSET_DIR)?.contains("geoip") == true }.getOrDefault(false)
        if (!bundled) return null
        val dir = File(ctx.filesDir, "tor/geoip").apply { mkdirs() }
        @Suppress("DEPRECATION")
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode.toString() }
            .getOrDefault("0")
        val marker = File(dir, "installed.version")
        val v4 = File(dir, "geoip")
        val v6 = File(dir, "geoip6")
        val fresh = marker.exists() && marker.readText().trim() == version && v4.length() > 0
        if (!fresh) {
            marker.delete()
            if (!copy(ctx, "geoip", v4)) return null
            copy(ctx, "geoip6", v6)
            marker.writeText(version)
        }
        return Paths(v4, v6.takeIf { it.length() > 0 })
    }

    private fun copy(ctx: Context, name: String, dest: File): Boolean = try {
        val tmp = File(dest.parentFile, "$name.part")
        ctx.assets.open("$ASSET_DIR/$name").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        dest.delete()
        tmp.renameTo(dest) && dest.length() > 0
    } catch (_: Exception) {
        false
    }
}
