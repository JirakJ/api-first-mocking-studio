package com.jakumjirak

import com.intellij.openapi.project.Project
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.JsonPathException

/**
 * Executes post-response scripts to extract variables.
 * Script format, one per line: `variableName=jsonPath` (e.g. `auth_token=$.token`).
 * Lines starting with `//` and lines whose path does not match are ignored.
 */
class ResponseScriptEngine(private val project: Project) {

    fun execute(script: String?, responseBody: String) {
        val settings = ApiFirstSettings.getInstance(project)
        extract(script, responseBody).forEach { (name, value) -> settings.setVariable(name, value) }
    }

    companion object {
        /** Variable assignments produced by [script] against [responseBody]. */
        fun extract(script: String?, responseBody: String): Map<String, String> {
            if (script.isNullOrBlank()) return emptyMap()
            val result = linkedMapOf<String, String>()
            script.lines()
                .map { it.trim() }
                .filter { it.contains('=') && !it.startsWith("//") }
                .forEach { line ->
                    val name = line.substringBefore('=').trim()
                    val path = line.substringAfter('=').trim()
                    if (name.isEmpty() || path.isEmpty()) return@forEach
                    readPath(responseBody, path)?.let { result[name] = it }
                }
            return result
        }

        private fun readPath(body: String, path: String): String? = try {
            JsonPath.read<Any?>(body, path)?.toString()
        } catch (ignored: JsonPathException) {
            null // unmatched path or non-JSON body: the variable is simply not set
        } catch (ignored: IllegalArgumentException) {
            null // body is not JSON
        }
    }
}
