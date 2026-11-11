package com.jakumjirak

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class OpenApiParserTest {
    
    private val parser = OpenApiParser()
    
    @Test
    fun `should parse valid openapi yaml file`() {
        val path = "src/test/resources/test-api.yaml"
        val openApi = parser.parseFromFile(path)
        
        assertNotNull(openApi)
        assertEquals("Test API", openApi?.info?.title)
        assertTrue(openApi?.paths?.containsKey("/users") == true)
    }
    
    @Test
    fun `should return null for invalid file path`() {
        val openApi = parser.parseFromFile("non-existent-file.yaml")
        assertNull(openApi)
    }

    @Test
    fun `swagger 2_0 documents are converted`() {
        val swagger = """
            swagger: "2.0"
            info: {title: Legacy, version: "1"}
            basePath: /v1
            paths:
              /ping:
                get:
                  operationId: ping
                  responses:
                    "200": {description: ok}
        """.trimIndent()
        val openApi = parser.parseContent(swagger)
        assertNotNull(openApi)
        assertEquals("Legacy", openApi?.info?.title)
        assertTrue(openApi?.paths?.containsKey("/ping") == true)
    }

    @Test
    fun `garbage content yields null`() {
        assertNull(parser.parseContent("this is not a spec"))
    }
}
