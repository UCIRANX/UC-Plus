package com.lumen.core.vpn.tor

import android.content.Context

/**
 * Tor choices made in the app: the bridge type, the country whose bridges to use, the countries
 * the user marked as favorite, whether one specific server was chosen, and the last bridge that
 * worked.
 */
class TorPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("uc_tor_prefs", Context.MODE_PRIVATE)

    /** "auto" tries every transport; otherwise one of the transport names. */
    var bridgeType: String
        get() = read(K_TYPE, "auto").ifBlank { "auto" }
        set(v) = write(K_TYPE, v)

    /** Two-letter code of the country whose bridges are used, or "" for bridges from every country. */
    var country: String
        get() = read(K_COUNTRY, "")
        set(v) = write(K_COUNTRY, v)

    /** True when the user chose one specific server; false when Tor picks from the pool. */
    var picked: Boolean
        get() = runCatching { prefs.getBoolean(K_PICKED, false) }.getOrDefault(false)
        set(v) = writeBoolean(K_PICKED, v)

    /** Countries the user marked with a heart. */
    var favoriteCountries: Set<String>
        get() = runCatching { prefs.getStringSet(K_FAVORITES, emptySet()) }.getOrNull().orEmpty()
        set(v) {
            runCatching { prefs.edit().putStringSet(K_FAVORITES, v.toSet()).apply() }
        }

    var lastGoodLine: String?
        get() = read(K_LAST, "").ifBlank { null }
        set(v) = write(K_LAST, v.orEmpty())

    private fun read(key: String, default: String): String =
        runCatching { prefs.getString(key, default) }.getOrNull() ?: default

    private fun write(key: String, value: String) {
        runCatching { prefs.edit().putString(key, value).apply() }
    }

    private fun writeBoolean(key: String, value: Boolean) {
        runCatching { prefs.edit().putBoolean(key, value).apply() }
    }

    private companion object {
        const val K_TYPE = "bridge_type"
        const val K_COUNTRY = "country"
        const val K_PICKED = "picked"
        const val K_FAVORITES = "favorite_countries"
        const val K_LAST = "last_good_line"
    }
}
