package com.jakumjirak

import com.jayway.jsonpath.JsonPath
import com.intellij.openapi.project.Project

/**
 * Executes post-response scripts to extract variables.
 * Script format: variableName=jsonPath (e.g., auth_token=$.token)
 */
class ResponseScriptEngine(private val project: Project) {

    fun execute(script: String?, responseBody: String) {
        if (script.isNullOrBlank()) return
        
        val settings = ApiFirstSettings.getInstance(project)
        
        script.lines().forEach { line ->
            if (line.contains("=")) {
                val varName = line.substringBefore("=").trim()
                val path = line.substringAfter("=").trim()
                
                try {
                    val value: Any = JsonPath.read(responseBody, path)
                    settings.setVariable(varName, value.toString())
                } catch (e: Exception) {
                    // Log or handle script error
                }
            }
        }
    }
}
