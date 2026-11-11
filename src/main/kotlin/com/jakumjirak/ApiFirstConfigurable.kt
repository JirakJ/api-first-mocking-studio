/*
 * Copyright 2024-2025 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakumjirak

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Project-level configurable for the API First & Mocking Studio plugin.
 * Provides a settings UI under Settings > Tools > API First & Mocking Studio.
 */
class ApiFirstConfigurable(private val project: Project) : SearchableConfigurable {

    private lateinit var requestTimeoutField: JBTextField
    private lateinit var followRedirectsCheckBox: JBCheckBox
    private lateinit var maxHistorySizeField: JBTextField
    private lateinit var mockServerPortField: JBTextField
    private var myPanel: JPanel? = null

    override fun getId(): String = "apifirst.mocking.studio.settings"

    override fun getDisplayName(): String = "API First & Mocking Studio"

    override fun createComponent(): JComponent {
        requestTimeoutField = JBTextField()
        followRedirectsCheckBox = JBCheckBox("Follow redirects")
        maxHistorySizeField = JBTextField()
        mockServerPortField = JBTextField()

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Request timeout (ms):"), requestTimeoutField, 1, false)
            .addComponent(followRedirectsCheckBox, 1)
            .addLabeledComponent(JBLabel("Max history size:"), maxHistorySizeField, 1, false)
            .addLabeledComponent(JBLabel("Mock server port (127.0.0.1):"), mockServerPortField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        myPanel = panel
        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val settings = ApiFirstPluginSettings.getInstance(project)
        return requestTimeoutField.text != settings.requestTimeoutMs.toString() ||
                followRedirectsCheckBox.isSelected != settings.followRedirects ||
                maxHistorySizeField.text != settings.maxHistorySize.toString() ||
                mockServerPortField.text != settings.mockServerPort.toString()
    }

    override fun apply() {
        val settings = ApiFirstPluginSettings.getInstance(project)
        settings.requestTimeoutMs = ApiFirstPluginSettings.parsePositive(
            requestTimeoutField.text, ApiFirstPluginSettings.DEFAULT_TIMEOUT_MS
        )
        settings.followRedirects = followRedirectsCheckBox.isSelected
        settings.maxHistorySize = ApiFirstPluginSettings.parsePositive(
            maxHistorySizeField.text, ApiFirstPluginSettings.DEFAULT_MAX_HISTORY
        )
        settings.mockServerPort = ApiFirstPluginSettings.parsePort(mockServerPortField.text)
    }

    override fun reset() {
        val settings = ApiFirstPluginSettings.getInstance(project)
        requestTimeoutField.text = settings.requestTimeoutMs.toString()
        followRedirectsCheckBox.isSelected = settings.followRedirects
        maxHistorySizeField.text = settings.maxHistorySize.toString()
        mockServerPortField.text = settings.mockServerPort.toString()
    }
}
