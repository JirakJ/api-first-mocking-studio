package com.jakumjirak

/**
 * Turns a pasted `curl` command into a [ApiFirstSettings.SavedRequest].
 *
 * Arguments are split like a POSIX shell would (single quotes, double quotes, backslash
 * escapes and `\`-newline continuations), so headers such as `-H 'Content-Type: application/json'`
 * and JSON bodies containing spaces survive intact.
 */
class CurlParser {

    fun parse(curlCommand: String): ApiFirstSettings.SavedRequest? {
        val tokens = tokenize(curlCommand)
        if (tokens.firstOrNull()?.equals("curl", ignoreCase = true) != true) return null

        val state = ParseState()
        var i = 1
        while (i < tokens.size) {
            i += apply(state, tokens[i], tokens.getOrNull(i + 1))
        }
        return state.toRequest()
    }

    private class ParseState {
        var method: String? = null
        var url = ""
        val headers = linkedMapOf<String, String>()
        var body: String? = null
        var user: String? = null

        fun toRequest(): ApiFirstSettings.SavedRequest {
            val resolvedMethod = method ?: if (body != null) "POST" else "GET"
            val request = ApiFirstSettings.SavedRequest(
                name = "Imported cURL",
                method = resolvedMethod,
                url = url,
                headers = headers,
                bodyType = if (body != null) "Raw" else "None",
                body = body,
            )
            user?.let { credentials ->
                request.authType = "Basic Auth"
                request.authParams = mutableMapOf(
                    "username" to credentials.substringBefore(':'),
                    "password" to credentials.substringAfter(':', ""),
                )
            }
            return request
        }
    }

    /** Applies one option to [state]; returns how many tokens were consumed. */
    private fun apply(state: ParseState, token: String, next: String?): Int =
        applyValueOption(state, token, next) ?: applyFlag(state, token)

    /** Options that take the following token as their value; `null` if [token] is not one of them. */
    private fun applyValueOption(state: ParseState, token: String, next: String?): Int? {
        val setter: ((String) -> Unit)? = when (token) {
            "-X", "--request" -> { v -> state.method = v.uppercase() }
            "-H", "--header" -> { v -> addHeader(state, v) }
            "-u", "--user" -> { v -> state.user = v }
            "--url" -> { v -> state.url = v }
            in DATA_FLAGS -> { v -> state.body = state.body?.let { "$it&$v" } ?: v }
            in OPTIONS_WITH_VALUE -> { _ -> }
            else -> null
        }
        setter ?: return null
        next?.let(setter)
        return if (next != null) 2 else 1
    }

    private fun applyFlag(state: ParseState, token: String): Int {
        when {
            token.startsWith("-X") && token.length > 2 -> state.method = token.substring(2).uppercase()
            token == "-I" || token == "--head" -> state.method = "HEAD"
            !token.startsWith("-") && state.url.isEmpty() -> state.url = token
        }
        return 1
    }

    private fun addHeader(state: ParseState, header: String) {
        val idx = header.indexOf(':')
        if (idx <= 0) return
        state.headers[header.substring(0, idx).trim()] = header.substring(idx + 1).trim()
    }

    companion object {
        private val DATA_FLAGS = setOf("-d", "--data", "--data-raw", "--data-binary", "--data-ascii", "--data-urlencode")
        private val OPTIONS_WITH_VALUE = setOf(
            "-o", "--output", "-A", "--user-agent", "-e", "--referer", "-b", "--cookie",
            "-c", "--cookie-jar", "-m", "--max-time", "--connect-timeout", "-x", "--proxy",
        )

        /** Shell-like word splitting. */
        fun tokenize(command: String): List<String> {
            val text = command.replace("\\\r\n", " ").replace("\\\n", " ")
            return Tokenizer(text).run()
        }
    }

    private class Tokenizer(private val text: String) {
        private val tokens = mutableListOf<String>()
        private val current = StringBuilder()
        private var inToken = false
        private var quote: Char? = null
        private var i = 0

        fun run(): List<String> {
            while (i < text.length) {
                when (quote) {
                    '\'' -> singleQuoted(text[i])
                    '"' -> doubleQuoted(text[i])
                    else -> unquoted(text[i])
                }
                i++
            }
            if (inToken) tokens += current.toString()
            return tokens
        }

        private fun singleQuoted(c: Char) {
            if (c == '\'') quote = null else current.append(c)
        }

        private fun doubleQuoted(c: Char) {
            when {
                c == '"' -> quote = null
                c == '\\' && i + 1 < text.length && text[i + 1] in "\"\\$`" -> current.append(text[++i])
                else -> current.append(c)
            }
        }

        private fun unquoted(c: Char) {
            when {
                c == '\'' || c == '"' -> {
                    quote = c
                    inToken = true
                }
                c == '\\' && i + 1 < text.length -> {
                    current.append(text[++i])
                    inToken = true
                }
                c.isWhitespace() -> {
                    if (inToken) tokens += current.toString()
                    current.clear()
                    inToken = false
                }
                else -> {
                    current.append(c)
                    inToken = true
                }
            }
        }
    }
}
