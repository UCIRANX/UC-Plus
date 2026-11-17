package com.lumen.core.config.tor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorBridgeCountriesTest {
    private val fp = "A".repeat(40)

    @Test fun fingerprintIsFoundInEveryTransport() {
        assertEquals(fp, TorBridges.fingerprint("obfs4 10.0.0.1:443 $fp cert=abc iat-mode=0"))
        assertEquals(fp, TorBridges.fingerprint("10.0.0.1:9001 $fp"))
        assertEquals(fp, TorBridges.fingerprint("webtunnel 10.0.0.1:443 $fp url=https://x.example/y ver=0.0.3"))
        assertNull(TorBridges.fingerprint("snowflake 192.0.2.3:80 nofingerprint"))
    }

    @Test fun parsesFingerprintCountryLinesAndSkipsBadOnes() {
        val parsed = TorCountries.parseBridgeCountries(
            "${fp.lowercase()} es\nBROKEN line\n${"B".repeat(40)} DE\n${"C".repeat(10)} FR\n"
        )
        assertEquals(mapOf(fp to "es", "B".repeat(40) to "de"), parsed.mapValues { it.value.lowercase() })
        assertEquals("es", parsed[fp])
    }
}
