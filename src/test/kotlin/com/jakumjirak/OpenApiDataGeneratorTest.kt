package com.jakumjirak

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiDataGeneratorTest {

    private val spec = """
        openapi: 3.0.3
        info: {title: Pets, version: '1'}
        paths:
          /pets:
            get:
              operationId: listPets
              responses:
                '200':
                  description: ok
                  content:
                    application/json:
                      schema:
                        type: array
                        items: {${'$'}ref: '#/components/schemas/Pet'}
          /pets/{id}:
            get:
              operationId: getPet
              responses:
                '200':
                  description: ok
                  content:
                    application/json:
                      example: {id: 7, name: Rex}
          /empty:
            delete:
              operationId: removeAll
              responses:
                '204': {description: gone}
        components:
          schemas:
            Pet:
              type: object
              properties:
                id: {type: integer}
                weight: {type: number}
                name: {type: string}
                email: {type: string, format: email}
                status: {type: string, enum: [available, sold]}
                vaccinated: {type: boolean}
                parent: {${'$'}ref: '#/components/schemas/Pet'}
    """.trimIndent()

    private val openApi = OpenApiParser().parseContent(spec)!!
    private val generator = OpenApiDataGenerator(openApi)

    private fun op(path: String) = openApi.paths[path]!!.readOperations().single()

    @Test
    fun `array of referenced objects is generated with typed sample values`() {
        val json = JsonParser.parseString(generator.generateForOperation(op("/pets")))
        assertTrue(json.isJsonArray, "arrays must not collapse to null")
        val pet = json.asJsonArray[0].asJsonObject
        assertEquals(123, pet.get("id").asInt)
        assertEquals(123.45, pet.get("weight").asDouble)
        assertEquals("sample_string", pet.get("name").asString)
        assertEquals("test@example.com", pet.get("email").asString)
        assertEquals("available", pet.get("status").asString)
        assertTrue(pet.get("vaccinated").asBoolean)
    }

    @Test
    fun `self referencing schema terminates`() {
        val json = JsonParser.parseString(generator.generateForOperation(op("/pets")))
        var node = json.asJsonArray[0].asJsonObject
        var depth = 0
        while (node.has("parent") && node.get("parent").isJsonObject) {
            node = node.getAsJsonObject("parent")
            depth++
        }
        assertTrue(depth <= OpenApiDataGenerator.MAX_DEPTH)
    }

    @Test
    fun `explicit example wins`() {
        val json = JsonParser.parseString(generator.generateForOperation(op("/pets/{id}"))).asJsonObject
        assertEquals(7, json.get("id").asInt)
        assertEquals("Rex", json.get("name").asString)
    }

    @Test
    fun `operation without schema yields a valid json message`() {
        val json = JsonParser.parseString(generator.generateForOperation(op("/empty"))).asJsonObject
        assertTrue(json.get("message").asString.contains("removeAll"))
    }

    @Test
    fun `openapi 3_1 types array is understood`() {
        val spec31 = """
            openapi: 3.1.0
            info: {title: T, version: '1'}
            paths: {}
            components:
              schemas:
                N:
                  type: [integer, "null"]
        """.trimIndent()
        val api = OpenApiParser().parseContent(spec31)!!
        assertEquals(OpenApiDataGenerator.SAMPLE_INTEGER, OpenApiDataGenerator(api).generateFromSchema(api.components.schemas["N"]))
    }

    @Test
    fun `null schema yields null`() {
        assertEquals(null, generator.generateFromSchema(null))
    }
}
