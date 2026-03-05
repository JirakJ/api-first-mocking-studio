package com.jakumjirak

class CurlParser {
    fun parse(curlCommand: String): ApiFirstSettings.SavedRequest? {
        if (!curlCommand.trim().startsWith("curl", ignoreCase = true)) return null

        var method = "GET"
        var url = ""
        val headers = mutableMapOf<String, String>()
        var body: String? = null
        var bodyType = "None"

        // Basic tokenization
        val lines = curlCommand.replace("\\\n", " ").replace("\n", " ").split(" ")

        var i = 1
        while (i < lines.size) {
            val token = lines[i].trim()
            if (token.isEmpty()) {
                i++
                continue
            }

            when {
                token == "-X" || token == "--request" -> {
                    if (i + 1 < lines.size) {
                        method = lines[i + 1].trim('\'', '"').uppercase()
                        i += 2
                    } else i++
                }
                token == "-H" || token == "--header" -> {
                    if (i + 1 < lines.size) {
                        val headerStr = lines[i + 1].trim('\'', '"')
                        val splitIdx = headerStr.indexOf(':')
                        if (splitIdx > 0) {
                            val key = headerStr.substring(0, splitIdx).trim()
                            val value = headerStr.substring(splitIdx + 1).trim()
                            headers[key] = value

                            if (key.equals("Content-Type", ignoreCase = true) && value.contains("application/json")) {
                                bodyType = "Raw"
                            }
                        }
                        i += 2
                    } else i++
                }
                token == "-d" || token == "--data" || token == "--data-raw" || token == "--data-binary" -> {
                    if (i + 1 < lines.size) {
                        body = lines[i + 1].trim('\'', '"')
                        if (bodyType == "None") bodyType = "Raw"
                        if (method == "GET") method = "POST"
                        i += 2
                    } else i++
                }
                token.startsWith("http://") || token.startsWith("https://") || (token.startsWith("'http") && token.endsWith("'")) || (token.startsWith("\"http") && token.endsWith("\"")) -> {
                    url = token.trim('\'', '"')
                    i++
                }
                else -> i++
            }
        }

        // If url is missing but there is a non-flag token, it might be the URL
        if (url.isEmpty()) {
             val possibleUrl = lines.find { !it.startsWith("-") && it != "curl" && (it.contains("http://") || it.contains("https://")) }
             if (possibleUrl != null) url = possibleUrl.trim('\'', '"')
        }

        return ApiFirstSettings.SavedRequest(
            name = "Imported cURL",
            method = method,
            url = url,
            headers = headers,
            bodyType = bodyType,
            body = body
        )
    }
}
