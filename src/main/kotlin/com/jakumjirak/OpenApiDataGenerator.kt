package com.jakumjirak

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Schema
import java.util.UUID

/** Produces example JSON responses for mock-server operations from examples or response schemas. */
class OpenApiDataGenerator(private val openApi: OpenAPI) {
    fun generateForOperation(operation: Operation): String {
        val successResponse = SUCCESS_CODES.firstNotNullOfOrNull { operation.responses?.get(it) }
        val content = successResponse?.content?.let { it["application/json"] ?: it.values.firstOrNull() }
        val value = when {
            content?.example != null -> content.example
            content?.examples?.values?.firstOrNull()?.value != null -> content.examples.values.first().value
            content?.schema != null -> generateFromSchema(content.schema, 0)
            else -> mapOf("message" to "No schema or example defined for ${operation.operationId ?: "operation"}")
        }
        // Examples parsed from YAML/JSON are Jackson trees, so serialise with swagger's Jackson mapper.
        return Json.pretty(value)
    }

    /** Builds a sample value for [schema]; recursion stops at [MAX_DEPTH] so self-referencing schemas terminate. */
    fun generateFromSchema(schema: Schema<*>?, depth: Int = 0): Any? {
        if (schema == null || depth > MAX_DEPTH) return null
        val resolved = resolveRef(schema)
        resolved.example?.let { return it }
        resolved.enum?.firstOrNull()?.let { return it }
        return when (typeOf(resolved)) {
            "array" -> listOf(generateFromSchema(resolved.items, depth + 1))
            "object" -> resolved.properties.orEmpty().mapValues { (_, prop) -> generateFromSchema(prop, depth + 1) }
            "string" -> generateString(resolved)
            "integer" -> SAMPLE_INTEGER
            "number" -> SAMPLE_NUMBER
            "boolean" -> true
            else -> null
        }
    }

    private fun resolveRef(schema: Schema<*>): Schema<*> {
        val ref = schema.`$ref` ?: return schema
        return openApi.components?.schemas?.get(ref.substringAfterLast("/")) ?: schema
    }

    /** OpenAPI 3.0 uses `type`, 3.1 uses `types`; schemas with properties but no type are objects. */
    private fun typeOf(schema: Schema<*>): String? =
        schema.type
            ?: schema.types?.firstOrNull { it != "null" }
            ?: when {
                schema.properties != null -> "object"
                schema.items != null -> "array"
                else -> null
            }

    private fun generateString(schema: Schema<*>): String {
        return when (schema.format) {
            "uuid" -> UUID.randomUUID().toString()
            "date-time" -> "2026-02-27T12:00:00Z"
            "date" -> "2026-02-27"
            "email" -> "test@example.com"
            "uri", "url" -> "https://example.com"
            else -> "sample_string"
        }
    }

    companion object {
        const val MAX_DEPTH = 8
        const val SAMPLE_INTEGER = 123
        const val SAMPLE_NUMBER = 123.45
        private val SUCCESS_CODES = listOf("200", "201", "202", "default")
    }
}
