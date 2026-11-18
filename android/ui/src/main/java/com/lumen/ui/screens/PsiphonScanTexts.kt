package com.lumen.ui.screens

/** Texts of the Psiphon scan popup. Persian when the app language is Persian, otherwise English. */
class PsiphonScanTexts(
    val scanButton: String,
    val title: String,
    val protocolSection: String,
    val protocolAuto: String,
    val protocolConduit: String,
    val protocolCdn: String,
    val protocolDirect: String,
    val cdnSection: String,
    val edgeIps: String,
    val sni: String,
    val ranges: String,
    val manual: String,
    val scanRanges: String,
    val scanManual: String,
    val stop: String,
    val close: String,
    private val progressFormat: String,
) {
    /** "scanned / healthy" line under the scan controls. */
    fun progress(scanned: Int, healthy: Int): String =
        progressFormat.replace("%1", scanned.toString()).replace("%2", healthy.toString())
}

fun psiphonScanTexts(localeCode: String): PsiphonScanTexts = if (localeCode == "fa") {
    PsiphonScanTexts(
        scanButton = "اسکن",
        title = "اسکن Psiphon",
        protocolSection = "نوع اتصال",
        protocolAuto = "خودکار (پیشنهادی)",
        protocolConduit = "رله‌های Conduit",
        protocolCdn = "CDN fronting",
        protocolDirect = "اتصال مستقیم",
        cdnSection = "CDN",
        edgeIps = "IPهای لبه CDN",
        sni = "نام‌های SNI برای CDN",
        ranges = "بازه‌های IP برای اسکن",
        manual = "IPهای دستی برای اسکن",
        scanRanges = "اسکن بازه‌ها",
        scanManual = "اسکن IPهای دستی",
        stop = "توقف",
        close = "بستن",
        progressFormat = "اسکن‌شده: %1 — سالم: %2",
    )
} else {
    PsiphonScanTexts(
        scanButton = "Scan",
        title = "Psiphon scan",
        protocolSection = "Connection protocol",
        protocolAuto = "Auto (recommended)",
        protocolConduit = "Conduit relays",
        protocolCdn = "CDN fronting",
        protocolDirect = "Direct connection",
        cdnSection = "CDN",
        edgeIps = "CDN edge IPs",
        sni = "CDN SNI hostnames",
        ranges = "IP ranges to scan",
        manual = "Manual IPs to scan",
        scanRanges = "Scan ranges",
        scanManual = "Scan manual IPs",
        stop = "Stop",
        close = "Close",
        progressFormat = "Scanned: %1 — healthy: %2",
    )
}
