package com.jakumjirak

import io.swagger.v3.oas.models.OpenAPI

class Importer {
    private val parser = OpenApiParser()

    fun importOpenApi(path: String): ApiFirstSettings.Collection? {
        val openApi = parser.parseFromFile(path) ?: return null
        
        val collection = ApiFirstSettings.Collection(
            name = openApi.info?.title ?: "Imported API"
        )

        openApi.paths?.forEach { (pathStr, pathItem) ->
            pathItem.readOperationsMap().forEach { (method, operation) ->
                val request = ApiFirstSettings.SavedRequest(
                    name = operation.summary ?: operation.operationId ?: "$method $pathStr",
                    method = method.name,
                    url = "{{base_url}}$pathStr",
                    bodyType = "Raw",
                    body = null // In future, generate from examples
                )
                
                // Set default base_url variable if not exists
                // Note: Actual variable management happens at settings level
                
                collection.requests.add(request)
            }
        }
        
        return collection
    }
}
