package com.jakumjirak

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress

/** Runs against an in-process loopback server; no external network. */
class ApiRequestExecutorTest {

    private lateinit var server: HttpServer
    private lateinit var base: String

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/echo") { ex ->
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val reply = "${ex.requestMethod} ${ex.requestHeaders.getFirst("X-Test")} $body".toByteArray()
            ex.responseHeaders.add("Set-Cookie", "session=abc; Path=/")
            ex.sendResponseHeaders(200, reply.size.toLong())
            ex.responseBody.use { it.write(reply) }
        }
        server.createContext("/redirect") { ex ->
            ex.responseHeaders.add("Location", "/echo")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.start()
        base = "http://${server.address.hostString}:${server.address.port}"
    }

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    fun `executes request with resolved variables and returns status, body and headers`() {
        val executor = ApiRequestExecutor()
        val response = executor.execute(
            ApiRequest("POST", "{{base}}/echo", headers = mapOf("X-Test" to "{{v}}"), body = "hello {{v}}"),
            mapOf("base" to base, "v" to "42")
        )
        assertEquals(200, response.statusCode)
        assertEquals("POST 42 hello 42", response.body)
        assertTrue(response.durationMs >= 0)
        assertTrue(response.headers.keys.any { it.equals("set-cookie", ignoreCase = true) })
    }

    @Test
    fun `cookies are stored and can be cleared`() {
        val executor = ApiRequestExecutor()
        executor.execute(ApiRequest("GET", "$base/echo"), emptyMap())
        assertEquals(listOf("session"), executor.getCookies().map { it.name })
        executor.clearCookies()
        assertTrue(executor.getCookies().isEmpty())
    }

    @Test
    fun `redirect policy follows the settings provider`() {
        var follow = true
        val executor = ApiRequestExecutor(followRedirects = { follow })
        assertEquals(200, executor.execute(ApiRequest("GET", "$base/redirect"), emptyMap()).statusCode)
        follow = false
        assertEquals(302, executor.execute(ApiRequest("GET", "$base/redirect"), emptyMap()).statusCode)
    }
}
