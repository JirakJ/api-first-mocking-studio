package com.jakumjirak

import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import java.nio.file.Files
import java.nio.file.Paths
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

class VariableResolver(private val variables: Map<String, String>) {
    fun resolve(text: String?): String? {
        if (text == null) return null
        var resolved: String = text
        for ((key, value) in variables) {
            resolved = resolved.replace("{{$key}}", value)
        }
        return resolved
    }
}

class ApiRequestExecutor {
    private val cookieManager = CookieManager()
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .cookieHandler(cookieManager)
        .build()

    fun getCookies(): List<java.net.HttpCookie> = cookieManager.cookieStore.cookies

    fun clearCookies() = cookieManager.cookieStore.removeAll()

    fun execute(request: ApiRequest, variables: Map<String, String>): ApiResponse {
        val resolver = VariableResolver(variables)
        val resolvedUrl = resolver.resolve(request.url) ?: request.url
        
        val startTime = System.currentTimeMillis()
        val builder = HttpRequest.newBuilder().uri(URI.create(resolvedUrl))

        val bodyPublisher = when (request.bodyType) {
            "Raw" -> {
                val resolvedBody = resolver.resolve(request.body)
                if (resolvedBody != null) HttpRequest.BodyPublishers.ofString(resolvedBody) 
                else HttpRequest.BodyPublishers.noBody()
            }
            "x-www-form-urlencoded" -> {
                val form = request.formData.joinToString("&") { 
                    "${URLEncoder.encode(it.key, StandardCharsets.UTF_8)}=${URLEncoder.encode(resolver.resolve(it.value) ?: it.value, StandardCharsets.UTF_8)}" 
                }
                builder.header("Content-Type", "application/x-www-form-urlencoded")
                HttpRequest.BodyPublishers.ofString(form)
            }
            "Form-Data" -> {
                val boundary = "---" + UUID.randomUUID().toString()
                builder.header("Content-Type", "multipart/form-data; boundary=$boundary")
                val byteList = mutableListOf<ByteArray>()
                
                request.formData.forEach { param ->
                    val resolvedValue = resolver.resolve(param.value) ?: param.value
                    
                    val header = "--$boundary\r\nContent-Disposition: form-data; name=\"${param.key}\"" +
                            (if (param.type == "File") "; filename=\"${Paths.get(resolvedValue).fileName}\"\r\nContent-Type: application/octet-stream" else "") +
                            "\r\n\r\n"
                    byteList.add(header.toByteArray(StandardCharsets.UTF_8))
                    
                    if (param.type == "File") {
                        try {
                            val path = Paths.get(resolvedValue)
                            if (Files.exists(path)) byteList.add(Files.readAllBytes(path))
                        } catch (e: Exception) { byteList.add("Error reading file".toByteArray()) }
                    } else {
                        byteList.add(resolvedValue.toByteArray(StandardCharsets.UTF_8))
                    }
                    byteList.add("\r\n".toByteArray(StandardCharsets.UTF_8))
                }
                byteList.add("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
                
                val totalLength = byteList.sumOf { it.size }
                val combined = ByteArray(totalLength)
                var current = 0
                byteList.forEach { 
                    System.arraycopy(it, 0, combined, current, it.size)
                    current += it.size
                }
                HttpRequest.BodyPublishers.ofByteArray(combined)
            }
            "GraphQL" -> {
                builder.header("Content-Type", "application/json")
                val query = resolver.resolve(request.graphqlQuery) ?: ""
                val vars = resolver.resolve(request.graphqlVariables) ?: "{}"
                
                // Construct a basic JSON payload for GraphQL
                val escapedQuery = query.replace("\"", "\\\"").replace("\n", "\\n")
                val payload = "{\"query\": \"$escapedQuery\", \"variables\": $vars}"
                HttpRequest.BodyPublishers.ofString(payload)
            }
            else -> HttpRequest.BodyPublishers.noBody()
        }

        builder.method(request.method, bodyPublisher)
        for ((k, v) in request.headers) builder.header(k, resolver.resolve(v) ?: v) 
        applyAuth(builder, request, resolver)

        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val duration = System.currentTimeMillis() - startTime

        return ApiResponse(response.statusCode(), response.headers().map(), response.body(), duration)
    }

    private fun applyAuth(builder: HttpRequest.Builder, request: ApiRequest, resolver: VariableResolver) {
        when (request.authType) {
            "Bearer Token", "OAuth 2.0" -> {
                val token = resolver.resolve(request.authParams["token"]) ?: ""
                if (token.isNotEmpty()) builder.header("Authorization", "Bearer $token")
            }
            "Basic Auth" -> {
                val user = resolver.resolve(request.authParams["username"]) ?: ""
                val pass = resolver.resolve(request.authParams["password"]) ?: ""
                val encoded = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
                builder.header("Authorization", "Basic $encoded")
            }
            "API Key" -> {
                val key = resolver.resolve(request.authParams["key"]) ?: ""
                val value = resolver.resolve(request.authParams["value"]) ?: ""
                if (key.isNotEmpty()) builder.header(key, value)
            }
        }
    }
}
