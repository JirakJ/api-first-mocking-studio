package com.jakumjirak

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import java.lang.StringBuilder

/**
 * Generates JetBrains HTTP Client compatible (.http) content from OpenAPI model.
 */
class HttpClientGenerator {

    fun generate(openApi: OpenAPI): String {
        val sb = StringBuilder()
        val title = openApi.info?.title ?: "Unnamed API"
        sb.append("### API: $title\n\n")

        openApi.paths?.forEach { (path, pathItem) ->
            pathItem.readOperationsMap().forEach { (method, operation) ->
                sb.append(generateRequest(path, method.name, operation))
                sb.append("\n\n")
            }
        }

        return sb.toString()
    }

    private fun generateRequest(path: String, method: String, operation: Operation): String {
        val sb = StringBuilder()
        val summary = operation.summary ?: operation.operationId ?: "Request"
        sb.append("### $summary\n")
        sb.append("$method {{base_url}}$path\n")
        sb.append("Accept: application/json\n")
        
        operation.parameters?.filter { it.`in` == "header" }?.forEach { param ->
            sb.append("${param.name}: {{${param.name}}}\n")
        }
        
        return sb.toString()
    }
}
