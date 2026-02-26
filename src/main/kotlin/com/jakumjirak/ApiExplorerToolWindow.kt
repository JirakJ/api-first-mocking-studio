package com.jakumjirak

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.BorderFactory

class ApiExplorerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val explorer = ApiExplorerToolWindow(project)
        val content = ContentFactory.getInstance().createContent(explorer.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ApiExplorerToolWindow(private val project: Project) {
    private val panel = JPanel(BorderLayout())
    
    // Top Panel Components
    private val methodCombo = ComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"))
    private val urlField = JBTextField("https://httpbin.org/get")
    private val sendButton = JButton("Send")
    
    // Request Components
    private val reqHeadersArea = JBTextArea("Accept: application/json\nContent-Type: application/json")
    private val reqBodyArea = JBTextArea()
    private val reqScriptArea = JBTextArea("// Example: auth_token=$.token")
    private val reqTabs = JBTabbedPane()
    
    // Response Components
    private val responseArea = JBTextArea()
    private val resHeadersArea = JBTextArea()
    private val resTabs = JBTabbedPane()
    private val statusLabel = JBLabel("Ready")
    
    private val executor = ApiRequestExecutor()
    private val scriptEngine = ResponseScriptEngine(project)

    init {
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // --- Top Bar ---
        val topBar = JPanel(BorderLayout(5, 0)).apply {
            border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
            add(methodCombo, BorderLayout.WEST)
            add(urlField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        // --- Request Panel ---
        reqTabs.addTab("Body", JBScrollPane(reqBodyArea))
        reqTabs.addTab("Headers", JBScrollPane(reqHeadersArea))
        reqTabs.addTab("Post-Script", JBScrollPane(reqScriptArea))

        val requestPanel = JPanel(BorderLayout()).apply {
            add(topBar, BorderLayout.NORTH)
            add(reqTabs, BorderLayout.CENTER)
        }

        // --- Response Panel ---
        responseArea.isEditable = false
        resHeadersArea.isEditable = false
        responseArea.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
        resHeadersArea.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
        
        resTabs.addTab("Body", JBScrollPane(responseArea))
        resTabs.addTab("Headers", JBScrollPane(resHeadersArea))

        val responsePanel = JPanel(BorderLayout()).apply {
            add(resTabs, BorderLayout.CENTER)
            
            val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(statusLabel)
            }
            add(statusPanel, BorderLayout.SOUTH)
        }

        // --- Main Splitter ---
        val splitter = OnePixelSplitter(true, 0.5f).apply {
            firstComponent = requestPanel
            secondComponent = responsePanel
        }

        panel.add(splitter, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        sendButton.addActionListener {
            executeRequest()
        }
        
        // Quick update body if method changes
        methodCombo.addActionListener {
            val method = methodCombo.selectedItem as String
            if (method == "GET" || method == "DELETE" || method == "HEAD") {
                reqTabs.selectedIndex = 1 // Switch to Headers if body not typical
            } else {
                reqTabs.selectedIndex = 0 // Switch to Body
            }
        }
    }

    private fun parseHeaders(headerText: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        headerText.lines().forEach { line ->
            if (line.contains(":")) {
                val key = line.substringBefore(":").trim()
                val value = line.substringAfter(":").trim()
                if (key.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun executeRequest() {
        val method = methodCombo.selectedItem as String
        val url = urlField.text.trim()
        
        if (url.isEmpty()) return
        
        val headers = parseHeaders(reqHeadersArea.text)
        val body = reqBodyArea.text.takeIf { it.isNotBlank() }
        val script = reqScriptArea.text.takeIf { it.isNotBlank() }
        
        statusLabel.text = "Sending $method request..."
        sendButton.isEnabled = false
        
        val settings = ApiFirstSettings.getInstance(project)
        val variables = settings.variables

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val request = ApiRequest(method, url, headers, body)
                val response = executor.execute(request, variables)
                
                // Execute Post-Response Script
                if (script != null) {
                    scriptEngine.execute(script, response.body)
                }
                
                val formattedHeaders = response.headers.entries.joinToString("\n") { 
                    "${it.key}: ${it.value.joinToString(", ")}" 
                }

                ApplicationManager.getApplication().invokeLater {
                    responseArea.text = response.body
                    resHeadersArea.text = formattedHeaders
                    statusLabel.text = "Status: ${response.statusCode} | Time: ${response.durationMs}ms"
                    sendButton.isEnabled = true
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    responseArea.text = "Error executing request:\n${e.message}\n\n${e.stackTraceToString()}"
                    resHeadersArea.text = ""
                    statusLabel.text = "Failed"
                    sendButton.isEnabled = true
                }
            }
        }
    }

    fun getContent(): JPanel = panel
}
