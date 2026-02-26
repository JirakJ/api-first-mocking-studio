package com.jakumjirak

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.info.Info
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HttpClientGeneratorTest {

    private val generator = HttpClientGenerator()

    @Test
    fun `should generate http client content`() {
        val openApi = OpenAPI().apply {
            info = Info().title("Test API")
            path("/users", PathItem().apply {
                get(Operation().summary("Get users"))
            })
        }

        val result = generator.generate(openApi)
        
        assertTrue(result.contains("### API: Test API"))
        assertTrue(result.contains("GET {{base_url}}/users"))
        assertTrue(result.contains("Accept: application/json"))
    }
}
