package com.jakumjirak

import java.lang.StringBuilder

class CodeGenerator {
    fun generateCurl(request: ApiFirstSettings.SavedRequest, variables: Map<String, String>): String {
        val resolver = VariableResolver(variables)
        val url = resolver.resolve(request.url) ?: request.url
        val sb = StringBuilder("curl --location --request ${request.method} \"$url\"")
        
        request.headers.forEach { (k, v) ->
            sb.append(" \\\n--header \"$k: ${resolver.resolve(v) ?: v}\"")
        }
        
        if (request.authType == "Bearer Token") {
            val token = resolver.resolve(request.authParams["token"]) ?: ""
            sb.append(" \\\n--header \"Authorization: Bearer $token\"")
        }
        
        request.body?.let {
            if (request.bodyType == "Raw") {
                sb.append(" \\\n--data-raw '$it'")
            }
        }
        if (request.bodyType == "GraphQL") {
            val query = resolver.resolve(request.graphqlQuery) ?: ""
            val vars = resolver.resolve(request.graphqlVariables) ?: "{}"
            val escapedQuery = query.replace("\"", "\\\"").replace("\n", "\\n")
            sb.append(" \\\n--header 'Content-Type: application/json'")
            sb.append(" \\\n--data-raw '{\"query\": \"$escapedQuery\", \"variables\": $vars}'")
        }
        
        return sb.toString()
    }

    fun generatePython(request: ApiFirstSettings.SavedRequest, variables: Map<String, String>): String {
        val resolver = VariableResolver(variables)
        val url = resolver.resolve(request.url) ?: request.url
        val sb = StringBuilder("import requests\nimport json\n\n")
        sb.append("url = \"$url\"\n")
        
        if (request.bodyType == "Raw" && request.body != null) {
            sb.append("payload = \"\"\"${request.body}\"\"\"\n")
        } else if (request.bodyType == "GraphQL") {
            val query = resolver.resolve(request.graphqlQuery) ?: ""
            val vars = resolver.resolve(request.graphqlVariables) ?: "{}"
            val escapedQuery = query.replace("\"", "\\\"").replace("\n", "\\n")
            sb.append("payload = json.dumps({\n")
            sb.append("  \"query\": \"$escapedQuery\",\n")
            sb.append("  \"variables\": $vars\n")
            sb.append("})\n")
        } else {
            sb.append("payload = {}\n")
        }
        
        sb.append("headers = {\n")
        request.headers.forEach { (k, v) ->
            sb.append("  \"$k\": \"${resolver.resolve(v) ?: v}\",\n")
        }
        if (request.authType == "Bearer Token") {
            val token = resolver.resolve(request.authParams["token"]) ?: ""
            sb.append("  \"Authorization\": \"Bearer $token\",\n")
        }
        if (request.bodyType == "GraphQL") {
            sb.append("  \"Content-Type\": \"application/json\",\n")
        }
        sb.append("}\n\n")
        
        sb.append("response = requests.request(\"${request.method}\", url, headers=headers, data=payload)\n")
        sb.append("print(response.text)\n")
        
        return sb.toString()
    }

    fun generateJsFetch(request: ApiFirstSettings.SavedRequest, variables: Map<String, String>): String {
        val resolver = VariableResolver(variables)
        val url = resolver.resolve(request.url) ?: request.url
        val sb = StringBuilder("const myHeaders = new Headers();\n")
        
        request.headers.forEach { (k, v) ->
            sb.append("myHeaders.append(\"$k\", \"${resolver.resolve(v) ?: v}\");\n")
        }
        if (request.authType == "Bearer Token") {
            val token = resolver.resolve(request.authParams["token"]) ?: ""
            sb.append("myHeaders.append(\"Authorization\", \"Bearer $token\");\n")
        }
        if (request.bodyType == "GraphQL") {
            sb.append("myHeaders.append(\"Content-Type\", \"application/json\");\n")
        }
        
        sb.append("\nconst requestOptions = {\n")
        sb.append("  method: '${request.method}',\n")
        sb.append("  headers: myHeaders,\n")
        
        if (request.bodyType == "Raw" && request.body != null) {
            sb.append("  body: JSON.stringify(${request.body}),\n")
        } else if (request.bodyType == "GraphQL") {
            val query = resolver.resolve(request.graphqlQuery) ?: ""
            val vars = resolver.resolve(request.graphqlVariables) ?: "{}"
            val escapedQuery = query.replace("\"", "\\\"").replace("\n", "\\n")
            sb.append("  body: JSON.stringify({\n")
            sb.append("    query: \"$escapedQuery\",\n")
            sb.append("    variables: $vars\n")
            sb.append("  }),\n")
        }

        sb.append("  redirect: 'follow'\n")
        sb.append("};\n\n")
        
        sb.append("fetch(\"$url\", requestOptions)\n")
        sb.append("  .then(response => response.text())\n")
        sb.append("  .then(result => console.log(result))\n")
        sb.append("  .catch(error => console.log('error', error));\n")
        
        return sb.toString()
    }
}
