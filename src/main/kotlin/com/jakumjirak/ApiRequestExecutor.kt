package com.jakumjirak

import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class ApiRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
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

    fun execute(request: ApiRequest, variables: Map<String, String>): ApiResponse {
        val resolver = VariableResolver(variables)
        val resolvedUrl = resolver.resolve(request.url) ?: request.url
        val resolvedBody = resolver.resolve(request.body)
        
        val startTime = System.currentTimeMillis()
        
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(resolvedUrl))
            .method(request.method, resolvedBody?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())

        for ((k, v) in request.headers) {
            val resolvedValue = resolver.resolve(v) ?: v
            builder.header(k, resolvedValue) 
        }

        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val duration = System.currentTimeMillis() - startTime

        return ApiResponse(
            statusCode = response.statusCode(),
            headers = response.headers().map(),
            body = response.body(),
            durationMs = duration
        )
    }
}
