package com.lumen.core.config.tor

import java.util.Locale

/**
 * Helpers for exit countries. The list of countries comes from the GeoIP index that the
 * repository publishes, so it only shows countries that have data.
 */
object TorCountries {
    /** Returns the code in lower case when it is a two-letter code, or "" for "any country". */
    fun normalize(code: String?): String =
        code?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } }.orEmpty()

    fun flag(code: String): String =
        code.uppercase(Locale.ROOT).map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

    fun name(code: String, locale: Locale): String = Locale("", code.uppercase(Locale.ROOT)).getDisplayCountry(locale)
}
