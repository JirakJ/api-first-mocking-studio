package com.jakumjirak

import com.google.gson.GsonBuilder
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import java.util.UUID

class OpenApiDataGenerator(private val openApi: OpenAPI) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun generateForOperation(operation: Operation): String {
        val successResponse = operation.responses?.get("200") ?: operation.responses?.get("201") ?: operation.responses?.get("default")
        
        if (successResponse != null) {
            val content = successResponse.content?.get("application/json")
            if (content?.example != null) {
                return gson.toJson(content.example)
            }
            if (content?.schema != null) {
                val data = generateFromSchema(content.schema)
                return gson.toJson(data)
            }
        }
        
        return """{"message": "No schema or example defined for ${operation.operationId}"}"""
    }

    private fun generateFromSchema(schema: Schema<*>?): Any? {
        if (schema == null) return null
        
        // Resolve reference if it's a $ref
        var resolvedSchema = schema
        if (schema.`$ref` != null) {
            val refName = schema.`$ref`.substringAfterLast("/")
            resolvedSchema = openApi.components?.schemas?.get(refName) ?: schema
        }

        if (resolvedSchema.example != null) {
            return resolvedSchema.example
        }

        return when (resolvedSchema) {
            is ObjectSchema, is Schema<*> -> {
                if (resolvedSchema.type == "object" || resolvedSchema.properties != null) {
                    val map = mutableMapOf<String, Any?>()
                    resolvedSchema.properties?.forEach { (key, propSchema) ->
                        map[key] = generateFromSchema(propSchema)
                    }
                    map
                } else if (resolvedSchema.type == "string") {
                    generateString(resolvedSchema)
                } else if (resolvedSchema.type == "integer" || resolvedSchema.type == "number") {
                    123
                } else if (resolvedSchema.type == "boolean") {
                    true
                } else {
                    null
                }
            }
            is ArraySchema -> {
                listOf(generateFromSchema(resolvedSchema.items))
            }
            else -> {
                when (resolvedSchema.type) {
                    "string" -> generateString(resolvedSchema)
                    "integer", "number" -> 123
                    "boolean" -> true
                    "array" -> listOf(generateFromSchema(resolvedSchema.items))
                    else -> null
                }
            }
        }
    }

    private fun generateString(schema: Schema<*>): String {
        return when (schema.format) {
            "uuid" -> UUID.randomUUID().toString()
            "date-time" -> "2026-02-27T12:00:00Z"
            "date" -> "2026-02-27"
            "email" -> "test@example.com"
            else -> "sample_string"
        }
    }
}
