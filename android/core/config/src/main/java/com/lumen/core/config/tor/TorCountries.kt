package com.lumen.core.config.tor

import java.util.Locale

/**
 * Helpers for the countries of Tor bridges: normalizing codes, flags and names. A country is
 * listed only when at least one bridge in the app has been located there.
 */
object TorCountries {
    /** Returns the code in lower case when it is a two-letter code, or "" for "any country". */
    fun normalize(code: String?): String =
        code?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } }.orEmpty()

    fun flag(code: String): String =
        code.uppercase(Locale.ROOT).map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

    fun name(code: String, locale: Locale): String = Locale("", code.uppercase(Locale.ROOT)).getDisplayCountry(locale)

    /** Reads the `FINGERPRINT CC` lines of tor-countries.txt into a map keyed by upper-case fingerprint. */
    fun parseBridgeCountries(text: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (raw in text.lineSequence()) {
            val parts = raw.trim().split(Regex("\\s+"))
            if (parts.size != 2) continue
            val fp = parts[0].uppercase(Locale.ROOT)
            val cc = normalize(parts[1])
            if (fp.length == 40 && fp.all { it in '0'..'9' || it in 'A'..'F' } && cc.isNotEmpty()) {
                out[fp] = cc
            }
        }
        return out
    }
}
