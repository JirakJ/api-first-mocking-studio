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
            sb.append(" \\\n--data-raw '$it'")
        }
        
        return sb.toString()
    }

    fun generatePython(request: ApiFirstSettings.SavedRequest, variables: Map<String, String>): String {
        val resolver = VariableResolver(variables)
        val url = resolver.resolve(request.url) ?: request.url
        val sb = StringBuilder("import requests\n\n")
        sb.append("url = \"$url\"\n")
        
        if (request.body != null) {
            sb.append("payload = \"\"\"${request.body}\"\"\"\n")
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
        
        sb.append("\nconst requestOptions = {\n")
        sb.append("  method: '${request.method}',\n")
        sb.append("  headers: myHeaders,\n")
        if (request.body != null) {
            sb.append("  body: JSON.stringify(${request.body}),\n")
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
