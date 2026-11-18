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
    val ipScanner: String,
    val ranges: String,
    val manual: String,
    val beastMode: String,
    val beastModeDesc: String,
    val scanRanges: String,
    val scanManual: String,
    val stop: String,
    val close: String,
    private val progressFormat: String,
) {
    /** "scanned / healthy" line under the scanner controls. */
    fun progress(scanned: Int, healthy: Int): String =
        progressFormat.replace("%1", scanned.toString()).replace("%2", healthy.toString())
}

fun psiphonScanTexts(localeCode: String): PsiphonScanTexts = if (localeCode == "fa") {
    PsiphonScanTexts(
        scanButton = "اسکن",
        title = "اسکن Psiphon",
        protocolSection = "Connection Protocol",
        protocolAuto = "خودکار (پیشنهادی)",
        protocolConduit = "رله‌های Conduit",
        protocolCdn = "CDN Fronting",
        protocolDirect = "اتصال مستقیم",
        cdnSection = "CDN Fronting",
        edgeIps = "IPهای لبه CDN",
        sni = "نام‌های SNI برای CDN",
        ipScanner = "اسکنر IP",
        ranges = "بازه‌های IP برای اسکن",
        manual = "IPهای دستی برای اسکن",
        beastMode = "Beast Mode",
        beastModeDesc = "اتصال تهاجمی: همه پروتکل‌ها را روی همه سرورها امتحان می‌کند",
        scanRanges = "اسکن بازه‌ها",
        scanManual = "اسکن دستی",
        stop = "توقف",
        close = "بستن",
        progressFormat = "اسکن‌شده: %1 — سالم: %2",
    )
} else {
    PsiphonScanTexts(
        scanButton = "Scan",
        title = "Psiphon scan",
        protocolSection = "Connection Protocol",
        protocolAuto = "Auto (recommended)",
        protocolConduit = "Conduit relays",
        protocolCdn = "CDN Fronting",
        protocolDirect = "Direct connection",
        cdnSection = "CDN Fronting",
        edgeIps = "CDN edge IPs",
        sni = "CDN SNI hostnames",
        ipScanner = "IP scanner",
        ranges = "IP ranges to scan",
        manual = "Manual IPs to scan",
        beastMode = "Beast Mode",
        beastModeDesc = "Aggressive connection: tries all protocols on every server",
        scanRanges = "Scan ranges",
        scanManual = "Scan manual",
        stop = "Stop",
        close = "Close",
        progressFormat = "Scanned: %1 — healthy: %2",
    )
}
