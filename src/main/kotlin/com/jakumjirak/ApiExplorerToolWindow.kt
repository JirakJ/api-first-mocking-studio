package com.jakumjirak

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
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
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.DefaultListModel
import javax.swing.table.DefaultTableModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ApiExplorerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val explorer = ApiExplorerToolWindow(project)
        val content = ContentFactory.getInstance().createContent(explorer.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ApiExplorerToolWindow(private val project: Project) {
    private val panel = JPanel(BorderLayout())
    
    // Sidebar Components
    private val historyListModel = DefaultListModel<String>()
    private val historyList = JBList(historyListModel)
    private val savedRequestsList = mutableListOf<ApiFirstSettings.SavedRequest>()
    
    // Top Panel Components
    private val methodCombo = ComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"))
    private val urlField = JBTextField("https://httpbin.org/get")
    private val sendButton = JButton("Send")
    private val envCombo = ComboBox<String>()
    private val manageEnvButton = JButton(AllIcons.General.Settings)
    
    // Request Components
    private val paramsTableModel = DefaultTableModel(arrayOf("Key", "Value"), 0)
    private val paramsTable = JBTable(paramsTableModel)
    private val headersTableModel = DefaultTableModel(arrayOf("Key", "Value"), 0)
    private val headersTable = JBTable(headersTableModel)
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
    
    private var isSyncingParams = false

    init {
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        val settings = ApiFirstSettings.getInstance(project)
        
        // Load History
        settings.history.reversed().forEach { req -> 
            savedRequestsList.add(req)
            historyListModel.addElement("${req.method} ${req.url}")
        }
        
        // Populate Environment Combo
        updateEnvCombo(settings)
        
        // --- Top Bar ---
        val topBar = JPanel(BorderLayout(5, 0)).apply {
            border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
            
            val leftControls = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(envCombo)
                add(manageEnvButton)
                add(Box.createHorizontalStrut(5))
                add(methodCombo)
            }
            
            add(leftControls, BorderLayout.WEST)
            add(urlField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        // Initialize headers with some defaults and an empty row for new input
        headersTableModel.addRow(arrayOf("Accept", "application/json"))
        headersTableModel.addRow(arrayOf("Content-Type", "application/json"))
        headersTableModel.addRow(arrayOf("", ""))
        
        // Initialize params with empty row
        paramsTableModel.addRow(arrayOf("", ""))

        // Add listener to automatically add a new row when the last row is edited
        headersTableModel.addTableModelListener { e ->
            if (e.type == javax.swing.event.TableModelEvent.UPDATE) {
                val row = e.firstRow
                val lastRow = headersTableModel.rowCount - 1
                if (row == lastRow) {
                    val key = headersTableModel.getValueAt(lastRow, 0) as? String ?: ""
                    val value = headersTableModel.getValueAt(lastRow, 1) as? String ?: ""
                    if (key.isNotEmpty() || value.isNotEmpty()) {
                        ApplicationManager.getApplication().invokeLater {
                            headersTableModel.addRow(arrayOf("", ""))
                        }
                    }
                }
            }
        }
        
        paramsTableModel.addTableModelListener { e ->
            if (isSyncingParams) return@addTableModelListener
            if (e.type == javax.swing.event.TableModelEvent.UPDATE) {
                val row = e.firstRow
                val lastRow = paramsTableModel.rowCount - 1
                if (row == lastRow) {
                    val key = paramsTableModel.getValueAt(lastRow, 0) as? String ?: ""
                    val value = paramsTableModel.getValueAt(lastRow, 1) as? String ?: ""
                    if (key.isNotEmpty() || value.isNotEmpty()) {
                        ApplicationManager.getApplication().invokeLater {
                            paramsTableModel.addRow(arrayOf("", ""))
                        }
                    }
                }
                syncTableToUrl()
            }
        }

        // --- Request Panel ---
        reqTabs.addTab("Params", JBScrollPane(paramsTable))
        reqTabs.addTab("Body", JBScrollPane(reqBodyArea))
        reqTabs.addTab("Headers", JBScrollPane(headersTable))
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

        // --- Editor Splitter (Request/Response) ---
        val editorSplitter = OnePixelSplitter(true, 0.5f).apply {
            firstComponent = requestPanel
            secondComponent = responsePanel
        }

        // --- Sidebar Panel ---
        val sidebarPanel = JPanel(BorderLayout()).apply {
            val titleLabel = JBLabel("History").apply {
                border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
            }
            add(titleLabel, BorderLayout.NORTH)
            add(JBScrollPane(historyList), BorderLayout.CENTER)
        }

        // --- Main Splitter (Sidebar/Editor) ---
        val mainSplitter = OnePixelSplitter(false, 0.2f).apply {
            firstComponent = sidebarPanel
            secondComponent = editorSplitter
        }

        panel.add(mainSplitter, BorderLayout.CENTER)
    }

    private fun syncUrlToTable() {
        if (isSyncingParams) return
        isSyncingParams = true
        try {
            val urlStr = urlField.text
            val queryIndex = urlStr.indexOf('?')
            if (queryIndex != -1 && queryIndex < urlStr.length - 1) {
                val query = urlStr.substring(queryIndex + 1)
                val pairs = query.split('&')
                paramsTableModel.rowCount = 0
                for (pair in pairs) {
                    val idx = pair.indexOf('=')
                    val key = if (idx != -1) URLDecoder.decode(pair.substring(0, idx), "UTF-8") else pair
                    val value = if (idx != -1) URLDecoder.decode(pair.substring(idx + 1), "UTF-8") else ""
                    paramsTableModel.addRow(arrayOf(key, value))
                }
                paramsTableModel.addRow(arrayOf("", ""))
            } else if (queryIndex == -1 || queryIndex == urlStr.length - 1) {
                // If query is empty, just keep one empty row if not already there
                if (paramsTableModel.rowCount > 1 || (paramsTableModel.rowCount == 1 && (paramsTableModel.getValueAt(0, 0) as String).isNotEmpty())) {
                    paramsTableModel.rowCount = 0
                    paramsTableModel.addRow(arrayOf("", ""))
                }
            }
        } catch (e: Exception) {
            // Ignore malformed URLs during typing
        } finally {
            isSyncingParams = false
        }
    }

    private fun syncTableToUrl() {
        if (isSyncingParams) return
        isSyncingParams = true
        try {
            if (paramsTable.isEditing) {
                paramsTable.cellEditor.stopCellEditing()
            }
            val baseUrl = urlField.text.substringBefore('?')
            val sb = StringBuilder(baseUrl)
            var first = true
            for (i in 0 until paramsTableModel.rowCount) {
                val key = paramsTableModel.getValueAt(i, 0) as? String ?: ""
                val value = paramsTableModel.getValueAt(i, 1) as? String ?: ""
                if (key.isNotEmpty()) {
                    sb.append(if (first) "?" else "&")
                    sb.append(URLEncoder.encode(key, "UTF-8"))
                    sb.append("=")
                    sb.append(URLEncoder.encode(value, "UTF-8"))
                    first = false
                }
            }
            urlField.text = sb.toString()
        } catch (e: Exception) {
        } finally {
            isSyncingParams = false
        }
    }

    private fun updateEnvCombo(settings: ApiFirstSettings) {
        envCombo.removeAllItems()
        envCombo.addItem("No Environment")
        settings.environments.forEach { envCombo.addItem(it.name) }
        
        if (settings.activeEnvironmentName != null && settings.environments.any { it.name == settings.activeEnvironmentName }) {
            envCombo.selectedItem = settings.activeEnvironmentName
        } else {
            envCombo.selectedIndex = 0
        }
    }

    private fun setupListeners() {
        val settings = ApiFirstSettings.getInstance(project)
        
        envCombo.addActionListener {
            val selected = envCombo.selectedItem as? String
            settings.activeEnvironmentName = if (selected == "No Environment") null else selected
        }
        
        manageEnvButton.addActionListener {
            val dialog = ManageEnvironmentsDialog(project, settings)
            if (dialog.showAndGet()) {
                updateEnvCombo(settings)
            }
        }

        urlField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { syncUrlToTable() }
            override fun removeUpdate(e: DocumentEvent?) { syncUrlToTable() }
            override fun changedUpdate(e: DocumentEvent?) { syncUrlToTable() }
        })

        historyList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val index = historyList.selectedIndex
                if (index in 0 until savedRequestsList.size) {
                    val req = savedRequestsList[index]
                    methodCombo.selectedItem = req.method
                    urlField.text = req.url
                    reqBodyArea.text = req.body ?: ""
                    reqScriptArea.text = req.postResponseScript ?: ""
                    
                    headersTableModel.rowCount = 0
                    req.headers.forEach { (k, v) ->
                        headersTableModel.addRow(arrayOf(k, v))
                    }
                    headersTableModel.addRow(arrayOf("", ""))
                }
            }
        }

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

    private fun getHeadersFromTable(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        // Stop cell editing if currently editing so the value is saved to the model
        if (headersTable.isEditing) {
            headersTable.cellEditor.stopCellEditing()
        }
        for (i in 0 until headersTableModel.rowCount) {
            val key = headersTableModel.getValueAt(i, 0) as? String ?: ""
            val value = headersTableModel.getValueAt(i, 1) as? String ?: ""
            if (key.isNotBlank()) {
                map[key.trim()] = value.trim()
            }
        }
        return map
    }
    
    private fun prettyPrintJson(jsonStr: String): String {
        return try {
            val jsonElement = JsonParser.parseString(jsonStr)
            val gson = GsonBuilder().setPrettyPrinting().create()
            gson.toJson(jsonElement)
        } catch (e: JsonSyntaxException) {
            // If it's not valid JSON, return original string
            jsonStr
        } catch (e: Exception) {
            jsonStr
        }
    }

    private fun executeRequest() {
        val method = methodCombo.selectedItem as String
        val url = urlField.text.trim()
        
        if (url.isEmpty()) return
        
        val headers = getHeadersFromTable()
        val body = reqBodyArea.text.takeIf { it.isNotBlank() }
        val script = reqScriptArea.text.takeIf { it.isNotBlank() }
        
        statusLabel.text = "Sending $method request..."
        sendButton.isEnabled = false
        
        val settings = ApiFirstSettings.getInstance(project)
        val activeVariables = settings.getActiveVariables()
        
        // Save to History
        val savedReq = ApiFirstSettings.SavedRequest(
            name = "$method $url",
            method = method,
            url = url,
            body = body,
            headers = headers.toMutableMap(),
            postResponseScript = script
        )
        settings.history.add(savedReq)
        savedRequestsList.add(0, savedReq) // Add to top of local list
        historyListModel.add(0, "${savedReq.method} ${savedReq.url}") // Add to top of UI list

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val request = ApiRequest(method, url, headers, body)
                val response = executor.execute(request, activeVariables)
                
                // Execute Post-Response Script
                if (script != null) {
                    scriptEngine.execute(script, response.body)
                }
                
                val formattedHeaders = response.headers.entries.joinToString("\n") { 
                    "${it.key}: ${it.value.joinToString(", ")}" 
                }
                
                val prettyBody = prettyPrintJson(response.body)

                ApplicationManager.getApplication().invokeLater {
                    responseArea.text = prettyBody
                    // Scroll to top
                    responseArea.caretPosition = 0
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
