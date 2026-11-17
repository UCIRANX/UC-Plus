package com.lumen.core.config.tor

import java.util.Locale

/** Countries an exit can be pinned to. Codes are the two-letter values Tor's GeoIP data uses. */
object TorCountries {
    val CODES = listOf(
        "de", "nl", "us", "fr", "gb", "se", "ch", "ca", "fi", "at", "no", "is", "ie", "lu", "ro",
        "bg", "cz", "pl", "md", "ua", "es", "it", "jp", "sg", "hk", "au", "br",
    )

    /** Returns the code in lower case when it is supported, or "" for "any country". */
    fun normalize(code: String?): String = code?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in CODES }.orEmpty()

    fun flag(code: String): String =
        code.uppercase(Locale.ROOT).map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

    fun name(code: String, locale: Locale): String = Locale("", code.uppercase(Locale.ROOT)).getDisplayCountry(locale)
}
