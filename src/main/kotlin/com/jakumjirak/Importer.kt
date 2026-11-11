package com.jakumjirak

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import io.swagger.v3.oas.models.OpenAPI
import java.io.File
import java.io.IOException

/** Imports request collections from OpenAPI/Swagger specs and Postman v2.x collections. */
class Importer(private val parser: OpenApiParser = OpenApiParser()) {

    fun importOpenApi(path: String): ApiFirstSettings.Collection? = parser.parseFromFile(path)?.let { fromOpenApi(it) }

    /** One request per operation, addressed relative to the `{{base_url}}` variable. */
    fun fromOpenApi(openApi: OpenAPI): ApiFirstSettings.Collection {
        val collection = ApiFirstSettings.Collection(name = openApi.info?.title ?: "Imported API")
        openApi.paths?.forEach { (pathStr, pathItem) ->
            pathItem.readOperationsMap().forEach { (method, operation) ->
                collection.requests.add(
                    ApiFirstSettings.SavedRequest(
                        name = operation.summary ?: operation.operationId ?: "$method $pathStr",
                        method = method.name,
                        url = "{{base_url}}$pathStr",
                        bodyType = "Raw",
                        body = null
                    )
                )
            }
        }
        return collection
    }

    fun importPostmanCollection(path: String): ApiFirstSettings.Collection? = try {
        importPostmanJson(File(path).readText())
    } catch (ignored: IOException) {
        null
    }

    /**
     * Parses a Postman v2.0/v2.1 collection. Requests inside folders are flattened into the
     * collection with a `Folder / Request` name. Returns `null` for anything that is not a collection.
     */
    fun importPostmanJson(json: String): ApiFirstSettings.Collection? {
        val root = try {
            JsonParser.parseString(json)
        } catch (ignored: JsonParseException) {
            return null
        }
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        val info = obj.objectOrNull("info") ?: return null
        val items = obj.get("item")?.takeIf { it.isJsonArray } ?: return null
        val collection = ApiFirstSettings.Collection(name = info.stringOrNull("name") ?: "Imported Postman Collection")
        collectItems(items, prefix = "", into = collection.requests)
        return collection
    }

    private fun collectItems(items: JsonElement, prefix: String, into: MutableList<ApiFirstSettings.SavedRequest>) {
        items.asJsonArray.filter { it.isJsonObject }.map { it.asJsonObject }.forEach { item ->
            val name = prefix + (item.stringOrNull("name") ?: "Unnamed Request")
            val request = item.objectOrNull("request")
            val children = item.get("item")?.takeIf { it.isJsonArray }
            when {
                request != null -> into += toSavedRequest(name, request)
                children != null -> collectItems(children, "$name / ", into)
            }
        }
    }

    private fun toSavedRequest(name: String, req: JsonObject): ApiFirstSettings.SavedRequest {
        val urlElement = req.get("url")
        val url = when {
            urlElement == null -> ""
            urlElement.isJsonObject -> urlElement.asJsonObject.stringOrNull("raw").orEmpty()
            urlElement.isJsonPrimitive -> urlElement.asString
            else -> ""
        }
        val headers = linkedMapOf<String, String>()
        req.get("header")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter { it.isJsonObject }
            ?.map { it.asJsonObject }
            ?.filterNot { it.get("disabled")?.takeIf { d -> d.isJsonPrimitive }?.asBoolean == true }
            ?.forEach { header ->
                val key = header.stringOrNull("key")
                val value = header.stringOrNull("value")
                if (key != null && value != null) headers[key] = value
            }
        val body = req.objectOrNull("body")
        val raw = body?.takeIf { it.stringOrNull("mode") == "raw" }?.stringOrNull("raw")
        return ApiFirstSettings.SavedRequest(
            name = name,
            method = req.stringOrNull("method") ?: "GET",
            url = url,
            headers = headers,
            bodyType = if (raw != null) "Raw" else "None",
            body = raw
        )
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString
}
