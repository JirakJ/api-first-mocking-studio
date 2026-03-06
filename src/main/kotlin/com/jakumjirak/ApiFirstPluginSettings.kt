/*
 * Copyright (c) 2024-2025 Jakub Jirak. All rights reserved.
 * Licensed under the terms of the project license.
 */
package com.jakumjirak

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Persistent project-level settings for the API First & Mocking Studio plugin.
 * Stores general configuration such as request timeout, SSL verification, and mock server port.
 */
@State(name = "ApiFirstPluginSettings", storages = [Storage("api-first-plugin-settings.xml")])
class ApiFirstPluginSettings : PersistentStateComponent<ApiFirstPluginSettings.State> {

    private var myState = State()

    class State {
        var requestTimeoutMs: Int = 30000
        var followRedirects: Boolean = true
        var verifySsl: Boolean = true
        var maxHistorySize: Int = 100
        var mockServerPort: Int = 8085
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var requestTimeoutMs: Int
        get() = myState.requestTimeoutMs
        set(value) { myState.requestTimeoutMs = value }

    var followRedirects: Boolean
        get() = myState.followRedirects
        set(value) { myState.followRedirects = value }

    var verifySsl: Boolean
        get() = myState.verifySsl
        set(value) { myState.verifySsl = value }

    var maxHistorySize: Int
        get() = myState.maxHistorySize
        set(value) { myState.maxHistorySize = value }

    var mockServerPort: Int
        get() = myState.mockServerPort
        set(value) { myState.mockServerPort = value }

    companion object {
        fun getInstance(project: Project): ApiFirstPluginSettings =
            project.getService(ApiFirstPluginSettings::class.java)
    }
}
