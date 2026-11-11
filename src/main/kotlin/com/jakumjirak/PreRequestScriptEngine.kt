package com.jakumjirak

import com.intellij.openapi.project.Project
import java.util.UUID

/**
 * Executes pre-request scripts to set variables.
 * Script format, one per line: `variableName=value`. Values may reference existing
 * `{{variables}}` and the dynamic values `{{$timestamp}}` and `{{$guid}}`.
 */
class PreRequestScriptEngine(private val project: Project) {

    fun execute(script: String?) {
        val settings = ApiFirstSettings.getInstance(project)
        evaluate(script, settings.getActiveVariables()).forEach { (name, value) -> settings.setVariable(name, value) }
    }

    companion object {
        /**
         * Variable assignments produced by [script]. Later lines see variables assigned by earlier ones.
         */
        fun evaluate(
            script: String?,
            variables: Map<String, String>,
            now: () -> Long = System::currentTimeMillis,
            guid: () -> String = { UUID.randomUUID().toString() },
        ): Map<String, String> {
            if (script.isNullOrBlank()) return emptyMap()
            val known = variables.toMutableMap()
            val assigned = linkedMapOf<String, String>()
            script.lines()
                .map { it.trim() }
                .filter { it.contains('=') && !it.startsWith("//") }
                .forEach { line ->
                    val name = line.substringBefore('=').trim()
                    if (name.isEmpty()) return@forEach
                    val value = VariableResolver(known).resolveOrEmpty(line.substringAfter('=').trim())
                        .replace("{{\$timestamp}}", now().toString())
                        .replace("{{\$guid}}", guid())
                    known[name] = value
                    assigned[name] = value
                }
            return assigned
        }
    }
}
