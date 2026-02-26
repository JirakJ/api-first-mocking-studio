package com.jakumjirak

import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.oas.models.OpenAPI
import java.io.File

/**
 * Parser for OpenAPI 3.0/3.1 and Swagger 2.0 specifications.
 * Uses swagger-parser to handle JSON and YAML formats.
 */
class OpenApiParser {
    
    /**
     * Parses an OpenAPI specification from a local file path.
     * @param path The absolute or relative path to the specification file.
     * @return The parsed OpenAPI model or null if parsing fails.
     */
    fun parseFromFile(path: String): OpenAPI? {
        val result = OpenAPIV3Parser().readLocation(path, null, null)
        return result.openAPI
    }
}
