package com.jakumjirak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ImporterTest {

    private val importer = Importer()

    private val postman = """
        {
          "info": {"name": "Shop", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item": [
            {"name": "List", "request": {"method": "GET", "url": {"raw": "{{base_url}}/items"},
              "header": [{"key": "Accept", "value": "application/json"}, {"key": "X-Old", "value": "1", "disabled": true}]}},
            {"name": "Orders", "item": [
              {"name": "Create", "request": {"method": "POST", "url": "https://shop/orders",
                "body": {"mode": "raw", "raw": "{\"qty\": 1}"}}},
              {"name": "Nested", "item": [{"name": "Deep", "request": {"url": "https://shop/deep"}}]}
            ]}
          ]
        }
    """.trimIndent()

    @Test
    fun `postman collection with folders is flattened`() {
        val collection = importer.importPostmanJson(postman)!!
        assertEquals("Shop", collection.name)
        assertEquals(listOf("List", "Orders / Create", "Orders / Nested / Deep"), collection.requests.map { it.name })
    }

    @Test
    fun `postman request fields are mapped`() {
        val requests = importer.importPostmanJson(postman)!!.requests
        val list = requests[0]
        assertEquals("GET", list.method)
        assertEquals("{{base_url}}/items", list.url)
        assertEquals(mapOf("Accept" to "application/json"), list.headers)
        assertEquals("None", list.bodyType)

        val create = requests[1]
        assertEquals("POST", create.method)
        assertEquals("https://shop/orders", create.url)
        assertEquals("Raw", create.bodyType)
        assertEquals("{\"qty\": 1}", create.body)

        assertEquals("GET", requests[2].method)
    }

    @Test
    fun `non collections are rejected`() {
        assertNull(importer.importPostmanJson("not json {"))
        assertNull(importer.importPostmanJson("[]"))
        assertNull(importer.importPostmanJson("""{"item": []}"""))
        assertNull(importer.importPostmanJson("""{"info": {"name": "x"}}"""))
    }

    @Test
    fun `postman import from file and missing file`(@TempDir dir: Path) {
        val file = dir.resolve("c.json")
        Files.writeString(file, postman)
        assertNotNull(importer.importPostmanCollection(file.toString()))
        assertNull(importer.importPostmanCollection(dir.resolve("missing.json").toString()))
    }

    @Test
    fun `openapi import creates one request per operation`() {
        val collection = importer.importOpenApi("src/test/resources/test-api.yaml")!!
        assertEquals("Test API", collection.name)
        assertEquals(1, collection.requests.size)
        val req = collection.requests.single()
        assertEquals("Get users", req.name)
        assertEquals("GET", req.method)
        assertEquals("{{base_url}}/users", req.url)
    }

    @Test
    fun `openapi import of unparsable file returns null`() {
        assertNull(importer.importOpenApi("does-not-exist.yaml"))
    }
}
