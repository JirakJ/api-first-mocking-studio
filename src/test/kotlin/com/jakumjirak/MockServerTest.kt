package com.jakumjirak

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Operation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MockServerTest {

    @Test
    fun `should start and stop mock server`() {
        val server = MockServer()
        val openApi = OpenAPI().apply {
            path("/test", PathItem().apply {
                get(Operation().operationId("testOp"))
            })
        }

        server.start(8081, openApi)
        assertTrue(server.isRunning())
        
        server.stop()
        assertFalse(server.isRunning())
    }
}
