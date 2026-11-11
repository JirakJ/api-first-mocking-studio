package com.jakumjirak

import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.core.models.ParseOptions

/**
 * Parser for OpenAPI 3.0/3.1 and Swagger 2.0 specifications (JSON or YAML).
 * Swagger 2.0 documents are converted to the OpenAPI 3 model by swagger-parser.
 */
class OpenApiParser {

    /**
     * Parses a specification from a local file path or URL.
     * @return The parsed OpenAPI model or null if parsing fails.
     */
    fun parseFromFile(path: String): OpenAPI? {
        val options = ParseOptions().apply { isResolve = true }
        return OpenAPIParser().readLocation(path, null, options).openAPI
    }

    /** Parses specification text (JSON or YAML). */
    fun parseContent(content: String): OpenAPI? {
        val options = ParseOptions().apply { isResolve = true }
        return OpenAPIParser().readContents(content, null, options).openAPI
    }
}
