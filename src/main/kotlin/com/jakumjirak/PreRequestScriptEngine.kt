package com.jakumjirak

import com.intellij.openapi.project.Project
import java.util.*

/**
 * Executes pre-request scripts to set variables.
 * Script format: variableName=value
 * Supports dynamic values: {{$timestamp}}, {{$guid}}
 */
class PreRequestScriptEngine(private val project: Project) {

    fun execute(script: String?) {
        if (script.isNullOrBlank()) return
        
        val settings = ApiFirstSettings.getInstance(project)
        val resolver = VariableResolver(settings.getActiveVariables())
        
        script.lines().forEach { line ->
            if (line.contains("=") && !line.trim().startsWith("//")) {
                val varName = line.substringBefore("=").trim()
                var value = line.substringAfter("=").trim()
                
                // Resolve existing variables in the value
                value = resolver.resolve(value) ?: value
                
                // Replace special macros
                value = value.replace("{{\$timestamp}}", System.currentTimeMillis().toString())
                value = value.replace("{{\$guid}}", UUID.randomUUID().toString())
                
                if (varName.isNotEmpty()) {
                    settings.setVariable(varName, value)
                }
            }
        }
    }
}
