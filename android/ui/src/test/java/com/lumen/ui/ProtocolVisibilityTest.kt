package com.lumen.ui

import com.lumen.ui.screens.*
import org.junit.Assert.*
import org.junit.Test

class ProtocolVisibilityTest {
    private val nodes = listOf(
        NodeUiModel("v", "Vless one", "vless", "v.example", 443, countryCode = "CH", pingMs = 20),
        NodeUiModel("t", "Trojan one", "trojan", "t.example", 443, countryCode = "NL", pingMs = 30),
        NodeUiModel("f", "Trojan fav", "trojan", "f.example", 443, countryCode = "DE", pingMs = 40, isFavorite = true)
    )

    @Test fun uncheckedProtocolIsHiddenFromAllViewExceptFavourites() {
        val result = prepareServerList(nodes, ServerListQuery("all", disabledProtocols = setOf("trojan")))
        assertEquals(listOf("v"), result.visible.map { it.id })
    }

    @Test fun uncheckedProtocolStillShowsInsideItsOwnSection() {
        val result = prepareServerList(nodes, ServerListQuery("all", protocol = "trojan", disabledProtocols = setOf("trojan")))
        assertEquals(setOf("t", "f"), result.visible.map { it.id }.toSet())
    }

    @Test fun checkedProtocolsAreUnaffected() {
        val result = prepareServerList(nodes, ServerListQuery("all", disabledProtocols = emptySet()))
        assertEquals(3, result.visible.size)
    }
}
