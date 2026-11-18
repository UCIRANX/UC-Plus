package com.lumen.core.vpn.psiphon

import android.content.Context

/**
 * Settings of the Psiphon scan popup. The protocol values are the ones the tunnel config reads
 * (the same four choices as the Psiphon "Connection Protocol" setting).
 */
object PsiphonPrefs {
    const val MODE_AUTO = "auto"
    const val MODE_CONDUIT = "conduit"
    const val MODE_CDN_FRONTING = "cdn_fronting"
    const val MODE_DIRECT = "direct"
    val MODES = listOf(MODE_AUTO, MODE_CONDUIT, MODE_CDN_FRONTING, MODE_DIRECT)

    private const val FILE = "psiphon_prefs"
    private const val KEY_MODE = "protocol_mode"
    private const val KEY_SNI = "cdn_sni_hostnames"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun protocolMode(context: Context): String =
        runCatching { prefs(context).getString(KEY_MODE, MODE_AUTO) }.getOrNull()
            ?.takeIf { it in MODES } ?: MODE_AUTO

    fun setProtocolMode(context: Context, mode: String) {
        runCatching { prefs(context).edit().putString(KEY_MODE, if (mode in MODES) mode else MODE_AUTO).apply() }
    }

    /** The SNI list as typed: commas, spaces or new lines between hostnames. */
    private const val KEY_BEAST = "beast_mode"

    /** Beast Mode: try every protocol on every server. Shown in the scan section of the popup. */
    fun beastMode(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_BEAST, false) }.getOrDefault(false)

    fun setBeastMode(context: Context, on: Boolean) {
        runCatching { prefs(context).edit().putBoolean(KEY_BEAST, on).apply() }
    }

    fun cdnSni(context: Context): String =
        runCatching { prefs(context).getString(KEY_SNI, "") }.getOrNull().orEmpty()

    fun setCdnSni(context: Context, value: String) {
        runCatching { prefs(context).edit().putString(KEY_SNI, value).apply() }
    }

    /** Valid hostnames from typed text. Anything else is dropped. */
    fun parseSni(text: String): List<String> = text.split(Regex("[\\s,;]+"))
        .map { it.trim().lowercase() }
        .filter { it.length in 3..253 && it.contains('.') && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } }
        .distinct()
}

/** The CDN edge IP list: healthy IPs from the scanner plus any the user typed, stored as text. */
object CdnEdgeIpPreferences {
    private const val FILE = "psiphon_prefs"
    private const val KEY = "cdn_edge_ips"

    fun load(context: Context): String = runCatching {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "")
    }.getOrNull().orEmpty()

    fun save(context: Context, value: String) {
        runCatching { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, value).apply() }
    }
}
