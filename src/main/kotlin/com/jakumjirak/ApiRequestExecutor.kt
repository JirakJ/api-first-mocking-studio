package com.jakumjirak

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.net.CookieManager
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.Base64
import java.util.UUID

data class ApiRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val bodyType: String = "Raw",
    val body: String? = null,
    val formData: List<ApiFirstSettings.FormDataParam> = emptyList(),
    val graphqlQuery: String? = null,
    val graphqlVariables: String? = null,
    val authType: String = "None",
    val authParams: Map<String, String> = emptyMap()
)

data class ApiResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String,
    val durationMs: Long
)

/** Replaces `{{name}}` placeholders with values from [variables]; unknown placeholders are left as-is. */
class VariableResolver(private val variables: Map<String, String>) {
    fun resolve(text: String?): String? {
        if (text == null) return null
        var resolved: String = text
        for ((key, value) in variables) {
            resolved = resolved.replace("{{$key}}", value)
        }
        return resolved
    }

    fun resolveOrEmpty(text: String?): String = resolve(text).orEmpty()
}

/**
 * Sends [ApiRequest]s with the JDK HTTP client. Timeout and redirect policy are read from
 * the providers on every call so changes in Settings apply immediately; cookies persist
 * across requests (see [getCookies]).
 */
class ApiRequestExecutor(
    private val timeoutMs: () -> Int = { DEFAULT_TIMEOUT_MS },
    private val followRedirects: () -> Boolean = { true },
) {
    private val cookieManager = CookieManager()
    private val clients = mutableMapOf<Boolean, HttpClient>()

    fun getCookies(): List<java.net.HttpCookie> = cookieManager.cookieStore.cookies

    fun clearCookies() = cookieManager.cookieStore.removeAll()

    fun execute(request: ApiRequest, variables: Map<String, String>): ApiResponse {
        val httpRequest = RequestBuilder.build(request, variables, Duration.ofMillis(timeoutMs().toLong()))
        val startTime = System.currentTimeMillis()
        val response = client(followRedirects()).send(httpRequest, HttpResponse.BodyHandlers.ofString())
        val duration = System.currentTimeMillis() - startTime
        return ApiResponse(response.statusCode(), response.headers().map(), response.body(), duration)
    }

    @Synchronized
    private fun client(redirects: Boolean): HttpClient = clients.getOrPut(redirects) {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(if (redirects) HttpClient.Redirect.NORMAL else HttpClient.Redirect.NEVER)
            .cookieHandler(cookieManager)
            .build()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000
        private const val CONNECT_TIMEOUT_SECONDS = 10L
    }
}

/** Pure translation of an [ApiRequest] into a JDK [HttpRequest]; unit-tested without network. */
object RequestBuilder {

    fun build(request: ApiRequest, variables: Map<String, String>, timeout: Duration? = null): HttpRequest {
        val resolver = VariableResolver(variables)
        val builder = HttpRequest.newBuilder().uri(URI.create(resolver.resolveOrEmpty(request.url).trim()))
        timeout?.let { builder.timeout(it) }

        val (contentType, publisher) = body(request, resolver)
        contentType?.let { builder.header("Content-Type", it) }
        builder.method(request.method.uppercase(), publisher)
        for ((k, v) in request.headers) builder.setHeader(k, resolver.resolve(v) ?: v)
        authHeader(request.authType, request.authParams, resolver)?.let { (k, v) -> builder.setHeader(k, v) }
        return builder.build()
    }

    private fun body(request: ApiRequest, resolver: VariableResolver): Pair<String?, HttpRequest.BodyPublisher> =
        when (request.bodyType) {
            "Raw" -> null to (resolver.resolve(request.body)?.let { HttpRequest.BodyPublishers.ofString(it) }
                ?: HttpRequest.BodyPublishers.noBody())
            "x-www-form-urlencoded" ->
                "application/x-www-form-urlencoded" to HttpRequest.BodyPublishers.ofString(formUrlEncoded(request.formData, resolver))
            "Form-Data" -> {
                val boundary = "---" + UUID.randomUUID().toString()
                "multipart/form-data; boundary=$boundary" to
                    HttpRequest.BodyPublishers.ofByteArray(multipart(request.formData, resolver, boundary))
            }
            "GraphQL" -> "application/json" to HttpRequest.BodyPublishers.ofString(
                graphQlPayload(resolver.resolveOrEmpty(request.graphqlQuery), resolver.resolve(request.graphqlVariables))
            )
            else -> null to HttpRequest.BodyPublishers.noBody()
        }

    fun formUrlEncoded(params: List<ApiFirstSettings.FormDataParam>, resolver: VariableResolver): String =
        params.filter { it.key.isNotBlank() }.joinToString("&") {
            "${encode(it.key)}=${encode(resolver.resolve(it.value) ?: it.value)}"
        }

    /** Builds a `multipart/form-data` body; `File` params are read from disk (missing files are sent empty). */
    fun multipart(params: List<ApiFirstSettings.FormDataParam>, resolver: VariableResolver, boundary: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun write(text: String) = out.write(text.toByteArray(StandardCharsets.UTF_8))
        params.filter { it.key.isNotBlank() }.forEach { param ->
            val value = resolver.resolve(param.value) ?: param.value
            val isFile = param.type == "File"
            write("--$boundary\r\nContent-Disposition: form-data; name=\"${quoteSafe(param.key)}\"")
            if (isFile) {
                write("; filename=\"${quoteSafe(Paths.get(value).fileName?.toString().orEmpty())}\"\r\n")
                write("Content-Type: application/octet-stream")
            }
            write("\r\n\r\n")
            if (isFile) {
                val path = Paths.get(value)
                if (Files.isRegularFile(path)) out.write(Files.readAllBytes(path))
            } else {
                write(value)
            }
            write("\r\n")
        }
        write("--$boundary--\r\n")
        return out.toByteArray()
    }

    /**
     * Serialises a GraphQL request body. [variables] must be a JSON object (blank means `{}`);
     * anything else is rejected instead of producing an invalid body.
     */
    fun graphQlPayload(query: String, variables: String?): String {
        val payload = JsonObject()
        payload.addProperty("query", query)
        val vars = if (variables.isNullOrBlank()) {
            JsonObject()
        } else {
            val parsed = try {
                JsonParser.parseString(variables)
            } catch (e: JsonParseException) {
                throw IllegalArgumentException("GraphQL variables are not valid JSON: ${e.message}", e)
            }
            require(parsed.isJsonObject) { "GraphQL variables must be a JSON object" }
            parsed
        }
        payload.add("variables", vars)
        return payload.toString()
    }

    /** The auth header for [authType], or `null` when no header should be sent. */
    fun authHeader(authType: String, params: Map<String, String>, resolver: VariableResolver): Pair<String, String>? =
        when (authType) {
            "Bearer Token", "OAuth 2.0" -> resolver.resolveOrEmpty(params["token"]).takeIf { it.isNotEmpty() }
                ?.let { "Authorization" to "Bearer $it" }
            "Basic Auth" -> {
                val user = resolver.resolveOrEmpty(params["username"])
                val pass = resolver.resolveOrEmpty(params["password"])
                val encoded = Base64.getEncoder().encodeToString("$user:$pass".toByteArray(StandardCharsets.UTF_8))
                "Authorization" to "Basic $encoded"
            }
            "API Key" -> resolver.resolveOrEmpty(params["key"]).takeIf { it.isNotEmpty() }
                ?.let { it to resolver.resolveOrEmpty(params["value"]) }
            else -> null
        }

    private fun encode(text: String): String = URLEncoder.encode(text, StandardCharsets.UTF_8)

    private fun quoteSafe(text: String): String = text.replace("\"", "%22").replace("\r", "").replace("\n", "")
}
