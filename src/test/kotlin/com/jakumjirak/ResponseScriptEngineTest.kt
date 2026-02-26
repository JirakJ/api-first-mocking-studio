package com.jakumjirak

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResponseScriptEngineTest {

    @Test
    fun testExtractVariableFromJson() {
        val project = mockk<Project>()
        val settings = ApiFirstSettings()
        
        mockkStatic(ApiFirstSettings::class)
        every { ApiFirstSettings.getInstance(project) } returns settings
        
        val engine = ResponseScriptEngine(project)
        val responseBody = """{"token": "secret_abc_123", "user": {"id": 1}}"""
        val script = "auth_token=$.token\nuser_id=$.user.id"
        
        engine.execute(script, responseBody)
        
        assertEquals("secret_abc_123", settings.variables["auth_token"])
        assertEquals("1", settings.variables["user_id"])
    }
}
