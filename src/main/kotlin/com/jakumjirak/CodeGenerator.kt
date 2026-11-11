package com.jakumjirak

import com.google.gson.GsonBuilder

/**
 * Generates cURL, Python `requests` and JavaScript `fetch` snippets for a saved request.
 * Every user-provided value is emitted as a properly escaped literal for the target language.
 */
class CodeGenerator {

    private data class Snippet(
        val method: String,
        val url: String,
        val headers: Map<String, String>,
        val body: String?,
    )

    private fun prepare(request: ApiFirstSettings.SavedRequest, variables: Map<String, String>): Snippet {
        val resolver = VariableResolver(variables)
        val headers = linkedMapOf<String, String>()
        request.headers.forEach { (k, v) -> headers[k] = resolver.resolve(v) ?: v }
        if (request.authType == "Bearer Token" || request.authType == "OAuth 2.0") {
            val token = resolver.resolveOrEmpty(request.authParams["token"])
            if (token.isNotEmpty()) headers["Authorization"] = "Bearer $token"
        }
        val body = when (request.bodyType) {
            "Raw" -> resolver.resolve(request.body)
            "GraphQL" -> {
                headers["Content-Type"] = "application/json"
                graphQlBody(resolver.resolveOrEmpty(request.graphqlQuery), resolver.resolve(request.graphqlVariables))
            }
            else -> null
        }
        return Snippet(request.method.uppercase(), resolver.resolveOrEmpty(request.url), headers, body)
    }

    fun generateCurl(request: ApiFirstSettings.SavedRequest, variables: Map<String, String>): String {
        val s = prepare(request, variables)
        val sb = StringBuilder("curl --location --request ${s.method} ${shellQuote(s.url)}")
        s.headers.forEach { (k, v) -> sb.append(" \\\n--header ${shellQuote("$k: $v")}") }
        s.body?.let { sb.append(" \\\n--data-raw ${shellQuote(it)}") }
        return sb.toString()
    }

    fun generatePython(request: ApiFirstSettings.SavedRequest, variables: Map<String, String>): String {
        val s = prepare(request, variables)
        val sb = StringBuilder("import requests\n\n")
        sb.append("url = ${literal(s.url)}\n")
        sb.append("payload = ${s.body?.let { literal(it) } ?: "None"}\n")
        sb.append("headers = {\n")
        s.headers.forEach { (k, v) -> sb.append("  ${literal(k)}: ${literal(v)},\n") }
        sb.append("}\n\n")
        sb.append("response = requests.request(${literal(s.method)}, url, headers=headers, data=payload)\n")
        sb.append("print(response.text)\n")
        return sb.toString()
    }

    fun generateJsFetch(request: ApiFirstSettings.SavedRequest, variables: Map<String, String>): String {
        val s = prepare(request, variables)
        val sb = StringBuilder("const myHeaders = new Headers();\n")
        s.headers.forEach { (k, v) -> sb.append("myHeaders.append(${literal(k)}, ${literal(v)});\n") }
        sb.append("\nconst requestOptions = {\n")
        sb.append("  method: ${literal(s.method)},\n")
        sb.append("  headers: myHeaders,\n")
        s.body?.let { sb.append("  body: ${literal(it)},\n") }
        sb.append("  redirect: 'follow'\n")
        sb.append("};\n\n")
        sb.append("fetch(${literal(s.url)}, requestOptions)\n")
        sb.append("  .then(response => response.text())\n")
        sb.append("  .then(result => console.log(result))\n")
        sb.append("  .catch(error => console.log('error', error));\n")
        return sb.toString()
    }

    companion object {
        private val GSON = GsonBuilder().disableHtmlEscaping().create()

        /** A double-quoted string literal valid in JSON, JavaScript and Python. */
        fun literal(text: String): String = GSON.toJson(text)

        /** POSIX single-quoted shell word. */
        fun shellQuote(text: String): String = "'" + text.replace("'", "'\\''") + "'"

        /** GraphQL JSON body; invalid variables JSON degrades to `{}` so snippets never fail to render. */
        fun graphQlBody(query: String, variables: String?): String = try {
            RequestBuilder.graphQlPayload(query, variables)
        } catch (ignored: IllegalArgumentException) {
            RequestBuilder.graphQlPayload(query, null)
        }
    }
}
