package com.jakumjirak

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "ApiFirstSettings", storages = [Storage("api-first-studio.xml")])
@Service(Service.Level.PROJECT)
class ApiFirstSettings : PersistentStateComponent<ApiFirstSettings> {
    
    var history: MutableList<SavedRequest> = mutableListOf()
    
    // Global variables (fallback if not in active environment)
    var variables: MutableMap<String, String> = mutableMapOf()
    
    var environments: MutableList<Environment> = mutableListOf(
        Environment("Dev", mutableMapOf("base_url" to "http://localhost:8080")),
        Environment("Prod", mutableMapOf("base_url" to "https://api.example.com"))
    )
    
    var collections: MutableList<Collection> = mutableListOf()
    
    var activeEnvironmentName: String? = null

    data class Environment(
        var name: String = "",
        var variables: MutableMap<String, String> = mutableMapOf()
    )

    data class Collection(
        var name: String = "",
        var requests: MutableList<SavedRequest> = mutableListOf()
    )

    data class SavedRequest(
        var name: String = "",
        var method: String = "GET",
        var url: String = "",
        var body: String? = null,
        var headers: MutableMap<String, String> = mutableMapOf(),
        var preRequestScript: String? = null,
        var postResponseScript: String? = null,
        var authType: String = "None",
        var authParams: MutableMap<String, String> = mutableMapOf()
    )

    override fun getState(): ApiFirstSettings = this

    override fun loadState(state: ApiFirstSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun getActiveVariables(): Map<String, String> {
        val merged = mutableMapOf<String, String>()
        merged.putAll(variables) // Global variables first
        
        val activeEnv = environments.find { it.name == activeEnvironmentName }
        if (activeEnv != null) {
            merged.putAll(activeEnv.variables) // Active environment overrides globals
        }
        return merged
    }

    fun setVariable(key: String, value: String) {
        val activeEnv = environments.find { it.name == activeEnvironmentName }
        if (activeEnv != null) {
            activeEnv.variables[key] = value
        } else {
            variables[key] = value
        }
    }

    companion object {
        fun getInstance(project: com.intellij.openapi.project.Project): ApiFirstSettings = 
            project.getService(ApiFirstSettings::class.java)
    }
}
