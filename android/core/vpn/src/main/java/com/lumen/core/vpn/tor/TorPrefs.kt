package com.lumen.core.vpn.tor

import android.content.Context

/**
 * Tor choices made in the app: the exit country, the bridge type, whether Tor connects
 * automatically through the bridge pool, and the last bridge that worked.
 */
class TorPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("uc_tor_prefs", Context.MODE_PRIVATE)

    /** Two-letter country code of the exit, or "" for any country. */
    var exitCountry: String
        get() = read(K_EXIT, "")
        set(v) = write(K_EXIT, v)

    /** "auto" tries every transport; otherwise one of the transport names. */
    var bridgeType: String
        get() = read(K_TYPE, "auto").ifBlank { "auto" }
        set(v) = write(K_TYPE, v)

    /** True when Tor picks bridges from the pool; false when the user chose one server. */
    var autoMode: Boolean
        get() = runCatching { prefs.getBoolean(K_AUTO, false) }.getOrDefault(false)
        set(v) = write(K_AUTO, v)

    var lastGoodLine: String?
        get() = read(K_LAST, "").ifBlank { null }
        set(v) = write(K_LAST, v.orEmpty())

    private fun read(key: String, default: String): String =
        runCatching { prefs.getString(key, default) }.getOrNull() ?: default

    private fun write(key: String, value: String) {
        runCatching { prefs.edit().putString(key, value).apply() }
    }

    private fun write(key: String, value: Boolean) {
        runCatching { prefs.edit().putBoolean(key, value).apply() }
    }

    private companion object {
        const val K_EXIT = "exit_country"
        const val K_TYPE = "bridge_type"
        const val K_AUTO = "auto_mode"
        const val K_LAST = "last_good_line"
    }
}
