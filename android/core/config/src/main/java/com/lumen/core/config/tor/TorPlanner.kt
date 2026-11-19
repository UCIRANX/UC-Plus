package com.lumen.core.config.tor

/**
 * Chooses which bridges to try, and in what order, when Tor connects automatically.
 * The last working bridge comes first; the rest follow a fixed transport order with a
 * random sample inside each transport, so one bad group does not block the others.
 */
object TorPlanner {
    private val ORDER = listOf("webtunnel", "snowflake", "obfs4", "meek_lite", "vanilla")
    private const val PER_TYPE = 12
    /** Most bridges tried per transport. */
    private val LIMITS = mapOf("obfs4" to 100, "webtunnel" to 100, "vanilla" to 300, "snowflake" to 100, "meek_lite" to 100)

    /**
     * [types] limits the transports to try (null means all). [lastGoodLine] is tried first
     * when it is in the pool.
     */
    fun order(lines: List<String>, lastGoodLine: String?, types: Set<String>? = null): List<String> {
        val pool = lines.map { TorBridges.normalize(it) }.filter { TorBridges.isBridgeLine(it) }.distinct()
        val groups = pool.groupBy { TorBridges.typeOf(it) }
        val first = lastGoodLine?.let { TorBridges.normalize(it) }?.takeIf { it in pool }
        val result = mutableListOf<String>()
        if (first != null && (types == null || TorBridges.typeOf(first) in types)) result += first
        for (t in ORDER) {
            if (types != null && t !in types) continue
            val list = groups[t].orEmpty().filter { it != first }.shuffled()
            val limit = LIMITS[t] ?: PER_TYPE
            result += list.take(limit)
        }
        return result
    }
}
