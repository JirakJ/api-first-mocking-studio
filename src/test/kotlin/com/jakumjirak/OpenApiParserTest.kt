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
}
