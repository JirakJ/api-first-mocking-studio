package com.jakumjirak

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "ApiFirstSettings", storages = [Storage("api-first-studio.xml")])
@Service(Service.Level.PROJECT)
class ApiFirstSettings : PersistentStateComponent<ApiFirstSettings> {
    
    var history: MutableList<SavedRequest> = mutableListOf()
    var variables: MutableMap<String, String> = mutableMapOf()

    data class SavedRequest(
        var name: String = "",
        var method: String = "GET",
        var url: String = "",
        var body: String? = null,
        var headers: MutableMap<String, String> = mutableMapOf(),
        var postResponseScript: String? = null
    )

    override fun getState(): ApiFirstSettings = this

    override fun loadState(state: ApiFirstSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: com.intellij.openapi.project.Project): ApiFirstSettings = 
            project.getService(ApiFirstSettings::class.java)
    }
}
