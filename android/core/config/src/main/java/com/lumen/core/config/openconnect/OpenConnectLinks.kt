package com.lumen.core.config.openconnect

/**
 * One OpenConnect (AnyConnect) server, from a subscription line:
 * OPENCONNECT:server:port:username:password:authgroup:certpin:name:country
 * An empty field means an empty value.
 */
data class OpenConnectServer(
    val server: String,
    val port: Int,
    val username: String,
    val password: String,
    val authGroup: String,
    val certPin: String,
    val name: String,
    val country: String,
)

object OpenConnectLinks {
    const val SCHEME = "openconnect"
    const val PREFIX = "OPENCONNECT:"

    fun isOpenConnectLine(text: String): Boolean = text.trimStart().startsWith(PREFIX, ignoreCase = true)

    /** Parses one line; null when it is not an OpenConnect line or has no usable server and port. */
    fun parse(line: String): OpenConnectServer? {
        val trimmed = line.trim()
        if (!isOpenConnectLine(trimmed)) return null
        val fields = trimmed.substring(PREFIX.length).split(':')
        fun at(i: Int): String = fields.getOrNull(i)?.trim().orEmpty()
        val server = at(0)
        if (server.isEmpty()) return null
        val port = at(1).toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return OpenConnectServer(
            server = server,
            port = port,
            username = at(2),
            password = at(3),
            authGroup = at(4),
            certPin = at(5),
            name = at(6).ifEmpty { server },
            country = at(7),
        )
    }
}
