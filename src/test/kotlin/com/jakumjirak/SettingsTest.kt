package com.jakumjirak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsTest {

    @Test
    fun `active variables merge globals with the active environment`() {
        val settings = ApiFirstSettings()
        settings.variables["base_url"] = "http://global"
        settings.variables["only_global"] = "g"
        settings.activeEnvironmentName = "Dev"
        val vars = settings.getActiveVariables()
        assertEquals("http://localhost:8080", vars["base_url"])
        assertEquals("g", vars["only_global"])
    }

    @Test
    fun `setVariable writes to the active environment or to globals`() {
        val settings = ApiFirstSettings()
        settings.setVariable("a", "1")
        assertEquals("1", settings.variables["a"])
        settings.activeEnvironmentName = "Prod"
        settings.setVariable("b", "2")
        assertEquals("2", settings.environments.first { it.name == "Prod" }.variables["b"])
        assertEquals(null, settings.variables["b"])
    }

    @Test
    fun `numeric settings parse with defaults and bounds`() {
        assertEquals(5000, ApiFirstPluginSettings.parsePositive(" 5000 ", 1))
        assertEquals(7, ApiFirstPluginSettings.parsePositive("0", 7))
        assertEquals(7, ApiFirstPluginSettings.parsePositive("-3", 7))
        assertEquals(7, ApiFirstPluginSettings.parsePositive("abc", 7))
        assertEquals(9090, ApiFirstPluginSettings.parsePort("9090"))
        assertEquals(ApiFirstPluginSettings.DEFAULT_MOCK_PORT, ApiFirstPluginSettings.parsePort("70000"))
    }

    @Test
    fun `history is trimmed from the oldest end`() {
        val history = mutableListOf(1, 2, 3, 4, 5)
        ApiFirstPluginSettings.trimHistory(history, 3)
        assertEquals(listOf(3, 4, 5), history)
        ApiFirstPluginSettings.trimHistory(history, 0)
        assertEquals(listOf(5), history)
    }
}
