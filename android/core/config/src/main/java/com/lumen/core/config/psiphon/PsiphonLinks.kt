package com.lumen.core.config.psiphon

/**
 * Psiphon nodes. A subscription line is "psiphon://<region>#<name>": "auto" lets Psiphon choose
 * the egress, a two-letter code asks for that egress region. The tunnel core serves SOCKS5 on
 * [SOCKS_PORT] on loopback; sing-box dials that port.
 */
object PsiphonLinks {
    const val SCHEME = "psiphon"
    const val SOCKS_PORT = 10850
    const val AUTO = "auto"

    /** Published by the uc-plus-psiphon workflow on the psiphon branch (tools/collect_psiphon.py). */
    const val SUBSCRIPTION_URL = "https://raw.githubusercontent.com/UCIRANX/UC-Plus/psiphon/psiphon.txt"

    /** Egress region of a node link: "" for auto, an upper-case code, or null when the link is invalid. */
    fun regionOf(link: String): String? {
        val rest = link.trim().substringAfter("://", "").substringBefore('#').substringBefore('?').trim()
        if (rest.equals(AUTO, ignoreCase = true)) return ""
        return rest.uppercase().takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
    }
}
