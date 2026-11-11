package com.jakumjirak

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Two-way sync between a URL's query string and the Params table. */
object QueryParams {

    /** Decoded `key to value` pairs of [url]'s query string, or `null` when the URL has no `?`. */
    fun parse(url: String): List<Pair<String, String>>? {
        val idx = url.indexOf('?')
        if (idx == -1) return null
        return url.substring(idx + 1).substringBefore('#')
            .split('&')
            .filter { it.isNotEmpty() }
            .map { part ->
                val eq = part.indexOf('=')
                if (eq == -1) decode(part) to "" else decode(part.substring(0, eq)) to decode(part.substring(eq + 1))
            }
    }

    /** [url] without its query string plus the non-blank-key [params], URL-encoded. `{{variables}}` are kept readable. */
    fun build(url: String, params: List<Pair<String, String>>): String {
        val base = url.substringBefore('?')
        val query = params.filter { it.first.isNotEmpty() }.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        return if (query.isEmpty()) base else "$base?$query"
    }

    /** Lenient decode: malformed `%` escapes are kept verbatim instead of failing. */
    private fun decode(text: String): String = try {
        URLDecoder.decode(text, StandardCharsets.UTF_8)
    } catch (ignored: IllegalArgumentException) {
        text
    }

    private fun encode(text: String): String =
        URLEncoder.encode(text, StandardCharsets.UTF_8).replace("%7B%7B", "{{").replace("%7D%7D", "}}")
}
