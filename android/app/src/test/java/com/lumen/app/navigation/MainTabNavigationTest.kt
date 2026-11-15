package com.lumen.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTabNavigationTest {
    @Test fun `servers and settings are the only main tabs`() {
        assertEquals(listOf("servers", "settings"), MAIN_TAB_ROUTES)
        assertEquals(false, isSettingsChild("servers"))
    }
    @Test fun `logs and routing belong to the settings hub`() {
        assertEquals(true, isSettingsChild("logs"))
        assertEquals(true, isSettingsChild("routing"))
        assertEquals(true, isSettingsChild("routing/domain"))
        assertEquals(true, isSettingsChild("routing/apps"))
    }
    @Test fun `main tabs never pop through settings children`() {
        listOf(null, "dashboard", "servers", "settings", "logsville").forEach {
            assertEquals(false, isSettingsChild(it))
        }
    }
}
