package com.jakumjirak

import com.google.gson.JsonParser
import io.swagger.v3.oas.models.OpenAPI
import java.io.File

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
                    body = null
                )
                collection.requests.add(request)
            }
        }
        
        return collection
    }

    fun importPostmanCollection(path: String): ApiFirstSettings.Collection? {
        try {
            val content = File(path).readText()
            val root = JsonParser.parseString(content).asJsonObject
            
            val info = root.getAsJsonObject("info") ?: return null
            val collectionName = info.get("name")?.asString ?: "Imported Postman Collection"
            
            val collection = ApiFirstSettings.Collection(name = collectionName)
            
            val items = root.getAsJsonArray("item") ?: return null
            
            items.forEach { itemElement ->
                if (itemElement.isJsonObject) {
                    val itemObj = itemElement.asJsonObject
                    val name = itemObj.get("name")?.asString ?: "Unnamed Request"
                    
                    if (itemObj.has("request")) {
                        val reqObj = itemObj.getAsJsonObject("request")
                        val method = reqObj.get("method")?.asString ?: "GET"
                        
                        var url = ""
                        val urlElement = reqObj.get("url")
                        if (urlElement != null) {
                            if (urlElement.isJsonObject) {
                                url = urlElement.asJsonObject.get("raw")?.asString ?: ""
                            } else if (urlElement.isJsonPrimitive) {
                                url = urlElement.asString
                            }
                        }
                        
                        val headersMap = mutableMapOf<String, String>()
                        if (reqObj.has("header") && reqObj.get("header").isJsonArray) {
                            reqObj.getAsJsonArray("header").forEach { headerEl ->
                                val hObj = headerEl.asJsonObject
                                val key = hObj.get("key")?.asString
                                val value = hObj.get("value")?.asString
                                if (key != null && value != null) {
                                    headersMap[key] = value
                                }
                            }
                        }
                        
                        var bodyType = "None"
                        var bodyContent: String? = null
                        if (reqObj.has("body") && reqObj.get("body").isJsonObject) {
                            val bodyObj = reqObj.getAsJsonObject("body")
                            val mode = bodyObj.get("mode")?.asString
                            if (mode == "raw") {
                                bodyType = "Raw"
                                bodyContent = bodyObj.get("raw")?.asString
                            }
                            // Simplified: handling only raw for now, can be expanded to form-data later
                        }
                        
                        val request = ApiFirstSettings.SavedRequest(
                            name = name,
                            method = method,
                            url = url,
                            headers = headersMap,
                            bodyType = bodyType,
                            body = bodyContent
                        )
                        collection.requests.add(request)
                    }
                }
            }
            return collection
        } catch (e: Exception) {
            return null
        }
    }
}

