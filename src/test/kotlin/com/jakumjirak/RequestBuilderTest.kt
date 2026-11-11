package com.jakumjirak

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

class RequestBuilderTest {

    private val vars = mapOf("base_url" to "http://localhost:9000", "token" to "t-1", "id" to "42")
    private val resolver = VariableResolver(vars)

    @Test
    fun `variables are resolved in url and headers and unknown placeholders stay`() {
        val request = ApiRequest(
            method = "get",
            url = "{{base_url}}/users/{{id}}",
            headers = mapOf("X-Trace" to "{{id}}-{{missing}}"),
        )
        val built = RequestBuilder.build(request, vars, Duration.ofSeconds(5))
        assertEquals("http://localhost:9000/users/42", built.uri().toString())
        assertEquals("GET", built.method())
        assertEquals("42-{{missing}}", built.headers().firstValue("X-Trace").get())
        assertEquals(Duration.ofSeconds(5), built.timeout().get())
    }

    @Test
    fun `content type is set per body type and user headers override it`() {
        val form = ApiRequest("POST", "http://h/x", bodyType = "x-www-form-urlencoded")
        val formType = RequestBuilder.build(form, vars).headers().firstValue("Content-Type").get()
        assertEquals("application/x-www-form-urlencoded", formType)

        val gql = ApiRequest("POST", "http://h/x", bodyType = "GraphQL", graphqlQuery = "{ a }")
        val gqlType = RequestBuilder.build(gql, vars).headers().firstValue("Content-Type").get()
        assertEquals("application/json", gqlType)

        val overridden = gql.copy(headers = mapOf("Content-Type" to "application/graphql+json"))
        assertEquals(
            listOf("application/graphql+json"),
            RequestBuilder.build(overridden, vars).headers().allValues("Content-Type")
        )
    }

    @Test
    fun `raw body without content and None body produce no body`() {
        fun length(request: ApiRequest) = RequestBuilder.build(request, vars).bodyPublisher().get().contentLength()
        assertEquals(0L, length(ApiRequest("POST", "http://h/x", bodyType = "None")))
        assertEquals(0L, length(ApiRequest("POST", "http://h/x", body = null)))
        assertEquals(2L, length(ApiRequest("POST", "http://h/x", body = "{{id}}")))
    }

    @Test
    fun `form urlencoded escapes keys and values and skips blank keys`() {
        val params = listOf(
            ApiFirstSettings.FormDataParam("q", "a b&c"),
            ApiFirstSettings.FormDataParam("id", "{{id}}"),
            ApiFirstSettings.FormDataParam(" ", "ignored"),
        )
        assertEquals("q=a+b%26c&id=42", RequestBuilder.formUrlEncoded(params, resolver))
    }

    @Test
    fun `multipart body contains text and file parts`(@TempDir dir: Path) {
        val file = dir.resolve("data.bin")
        Files.write(file, byteArrayOf(1, 2, 3))
        val params = listOf(
            ApiFirstSettings.FormDataParam("name", "{{id}}"),
            ApiFirstSettings.FormDataParam("upload", file.toString(), "File"),
        )
        val body = String(RequestBuilder.multipart(params, resolver, "B"), StandardCharsets.ISO_8859_1)
        assertTrue(body.startsWith("--B\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\n42\r\n"))
        val filePart = "name=\"upload\"; filename=\"data.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n"
        assertTrue(body.contains(filePart + "\u0001\u0002\u0003\r\n"))
        assertTrue(body.endsWith("--B--\r\n"))
    }

    @Test
    fun `missing upload file sends an empty part`() {
        val params = listOf(ApiFirstSettings.FormDataParam("f", "/definitely/not/here.txt", "File"))
        val body = String(RequestBuilder.multipart(params, resolver, "B"), StandardCharsets.UTF_8)
        assertTrue(body.contains("filename=\"here.txt\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n--B--"))
    }

    @Test
    fun `graphql payload is valid json even with quotes, backslashes and newlines`() {
        val query = "query {\n  user(name: \"a\\\\b\") { id }\n}"
        val payload = JsonParser.parseString(RequestBuilder.graphQlPayload(query, """{"x": 1}""")).asJsonObject
        assertEquals(query, payload.get("query").asString)
        assertEquals(1, payload.getAsJsonObject("variables").get("x").asInt)
    }

    @Test
    fun `graphql blank variables become an empty object, invalid ones are rejected`() {
        val payload = JsonParser.parseString(RequestBuilder.graphQlPayload("{ a }", "  ")).asJsonObject
        assertEquals(0, payload.getAsJsonObject("variables").size())
        assertThrows(IllegalArgumentException::class.java) { RequestBuilder.graphQlPayload("{ a }", "{not json") }
        assertThrows(IllegalArgumentException::class.java) { RequestBuilder.graphQlPayload("{ a }", "[1]") }
    }

    @Test
    fun `auth headers for each auth type`() {
        assertEquals("Authorization" to "Bearer t-1", RequestBuilder.authHeader("Bearer Token", mapOf("token" to "{{token}}"), resolver))
        assertEquals("Authorization" to "Bearer abc", RequestBuilder.authHeader("OAuth 2.0", mapOf("token" to "abc"), resolver))
        assertNull(RequestBuilder.authHeader("Bearer Token", mapOf("token" to ""), resolver))

        val basic = RequestBuilder.authHeader("Basic Auth", mapOf("username" to "u", "password" to "p:w"), resolver)!!
        assertEquals("Authorization", basic.first)
        assertEquals("u:p:w", String(Base64.getDecoder().decode(basic.second.removePrefix("Basic "))))

        assertEquals("X-Api-Key" to "42", RequestBuilder.authHeader("API Key", mapOf("key" to "X-Api-Key", "value" to "{{id}}"), resolver))
        assertNull(RequestBuilder.authHeader("API Key", mapOf("value" to "v"), resolver))
        assertNull(RequestBuilder.authHeader("None", emptyMap(), resolver))
    }

    @Test
    fun `auth header is applied to the built request`() {
        val request = ApiRequest("GET", "http://h/x", authType = "Bearer Token", authParams = mapOf("token" to "zzz"))
        assertEquals("Bearer zzz", RequestBuilder.build(request, vars).headers().firstValue("Authorization").get())
        assertFalse(RequestBuilder.build(request.copy(authType = "None"), vars).headers().firstValue("Authorization").isPresent)
    }

    @Test
    fun `variable resolver handles null and empty maps`() {
        assertNull(VariableResolver(vars).resolve(null))
        assertEquals("", VariableResolver(vars).resolveOrEmpty(null))
        assertEquals("{{a}}", VariableResolver(emptyMap()).resolve("{{a}}"))
    }
}
