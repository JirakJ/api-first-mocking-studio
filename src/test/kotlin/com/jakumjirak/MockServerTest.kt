package com.jakumjirak

import com.google.gson.JsonParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class MockServerTest {

    private val server = MockServer()

    @AfterEach
    fun cleanup() = server.stop()

    private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    @Test
    fun `serves generated responses for spec operations on loopback`() {
        val openApi = OpenApiParser().parseContent(
            """
            openapi: 3.0.3
            info: {title: T, version: '1'}
            paths:
              /items/{id}:
                get:
                  operationId: getItem
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              id: {type: integer}
            """.trimIndent()
        )!!
        val port = freePort()
        server.start(port, openApi)
        assertTrue(server.isRunning())
        assertEquals(port, server.port)

        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://${MockServer.LOOPBACK}:$port/items/42")).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, response.statusCode())
        assertEquals(123, JsonParser.parseString(response.body()).asJsonObject.get("id").asInt)

        server.stop()
        assertFalse(server.isRunning())
        assertEquals(0, server.port)
    }

    @Test
    fun `invalid port is rejected`() {
        val openApi = OpenApiParser().parseContent("openapi: 3.0.3\ninfo: {title: T, version: '1'}\npaths: {}")!!
        assertThrows(IllegalArgumentException::class.java) { server.start(0, openApi) }
        assertFalse(server.isRunning())
    }
}
