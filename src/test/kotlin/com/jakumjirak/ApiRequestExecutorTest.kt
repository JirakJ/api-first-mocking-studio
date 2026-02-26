package com.jakumjirak

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.http.HttpTimeoutException

class ApiRequestExecutorTest {

    private val executor = ApiRequestExecutor()

    @Test
    fun `should execute get request`() {
        // We use a known stable public API for testing or a mock
        val request = ApiRequest("GET", "https://postman-echo.com/get")
        val response = executor.execute(request, emptyMap())
        
        assertEquals(200, response.statusCode)
        assertTrue(response.body.contains("args"))
    }
}
