/*
 * Copyright 2024-2025 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakumjirak

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Persistent project-level settings for the API First & Mocking Studio plugin.
 * Stores request timeout, redirect policy, history size and mock server port.
 */
@State(name = "ApiFirstPluginSettings", storages = [Storage("api-first-plugin-settings.xml")])
class ApiFirstPluginSettings : PersistentStateComponent<ApiFirstPluginSettings.State> {

    private var myState = State()

    class State {
        var requestTimeoutMs: Int = 30000
        var followRedirects: Boolean = true
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

    var maxHistorySize: Int
        get() = myState.maxHistorySize
        set(value) { myState.maxHistorySize = value }

    var mockServerPort: Int
        get() = myState.mockServerPort
        set(value) { myState.mockServerPort = value }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000
        const val DEFAULT_MAX_HISTORY = 100
        const val DEFAULT_MOCK_PORT = 8085

        /** Positive integer from [text], or [default] when blank/invalid/out of range. */
        fun parsePositive(text: String, default: Int, max: Int = Int.MAX_VALUE): Int =
            text.trim().toIntOrNull()?.takeIf { it in 1..max } ?: default

        /** Valid TCP port from [text], or [DEFAULT_MOCK_PORT]. */
        fun parsePort(text: String): Int = parsePositive(text, DEFAULT_MOCK_PORT, MockServer.VALID_PORTS.last)

        /** Drops the oldest entries so at most [max] remain (history is stored oldest first). */
        fun <T> trimHistory(history: MutableList<T>, max: Int) {
            val limit = max.coerceAtLeast(1)
            while (history.size > limit) history.removeAt(0)
        }

        fun getInstance(project: Project): ApiFirstPluginSettings =
            project.getService(ApiFirstPluginSettings::class.java)
    }
}
