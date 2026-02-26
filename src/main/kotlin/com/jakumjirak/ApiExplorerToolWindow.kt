package com.jakumjirak

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.*
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.net.URLDecoder
import java.net.URLEncoder
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

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
    
    private val collectionsRoot = DefaultMutableTreeNode("Collections")
    private val collectionsTreeModel = DefaultTreeModel(collectionsRoot)
    private val collectionsTree = Tree(collectionsTreeModel)
    
    // Top Panel Components
    private val methodCombo = ComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"))
    private val urlField = JBTextField("https://httpbin.org/get")
    private val sendButton = JButton("Send")
    private val envCombo = ComboBox<String>()
    private val manageEnvButton = JButton(AllIcons.General.Settings)
    private val saveToCollectionButton = JButton(AllIcons.Actions.MenuSaveall)
    
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
        
        // Load Collections
        updateCollectionsTree(settings)
        
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
            
            val rightControls = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
                add(saveToCollectionButton)
                add(sendButton)
            }
            add(rightControls, BorderLayout.EAST)
        }

        // Setup Tables
        headersTableModel.addRow(arrayOf("Accept", "application/json"))
        headersTableModel.addRow(arrayOf("Content-Type", "application/json"))
        headersTableModel.addRow(arrayOf("", ""))
        paramsTableModel.addRow(arrayOf("", ""))

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
            val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(statusLabel) }
            add(statusPanel, BorderLayout.SOUTH)
        }

        // --- Editor Splitter ---
        val editorSplitter = OnePixelSplitter(true, 0.5f).apply {
            firstComponent = requestPanel
            secondComponent = responsePanel
        }

        // --- Sidebar Panel ---
        val sidebarTabs = JBTabbedPane()
        sidebarTabs.addTab("History", JBScrollPane(historyList))
        
        val collectionsPanel = JPanel(BorderLayout()).apply {
            val decorator = ToolbarDecorator.createDecorator(collectionsTree)
                .setAddAction {
                    val name = Messages.showInputDialog(project, "Collection Name:", "New Collection", null)
                    if (!name.isNullOrBlank()) {
                        settings.collections.add(ApiFirstSettings.Collection(name))
                        updateCollectionsTree(settings)
                    }
                }
                .setRemoveAction {
                    val node = collectionsTree.lastSelectedPathComponent as? DefaultMutableTreeNode
                    val userObj = node?.userObject
                    if (userObj is ApiFirstSettings.Collection) {
                        settings.collections.remove(userObj)
                        updateCollectionsTree(settings)
                    } else if (userObj is ApiFirstSettings.SavedRequest) {
                        val parentNode = node.parent as? DefaultMutableTreeNode
                        val parentCollection = parentNode?.userObject as? ApiFirstSettings.Collection
                        parentCollection?.requests?.remove(userObj)
                        updateCollectionsTree(settings)
                    }
                }
                .createPanel()
            add(decorator, BorderLayout.CENTER)
        }
        sidebarTabs.addTab("Collections", collectionsPanel)

        // --- Main Splitter ---
        val mainSplitter = OnePixelSplitter(false, 0.2f).apply {
            firstComponent = sidebarTabs
            secondComponent = editorSplitter
        }

        panel.add(mainSplitter, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        val settings = ApiFirstSettings.getInstance(project)
        
        envCombo.addActionListener {
            val selected = envCombo.selectedItem as? String
            settings.activeEnvironmentName = if (selected == "No Environment") null else selected
        }
        
        manageEnvButton.addActionListener {
            val dialog = ManageEnvironmentsDialog(project, settings)
            if (dialog.showAndGet()) { updateEnvCombo(settings) }
        }

        saveToCollectionButton.addActionListener {
            val collectionNames = settings.collections.map { it.name }.toTypedArray()
            if (collectionNames.isEmpty()) {
                Messages.showInfoMessage(project, "Create a collection first.", "Save")
                return@addActionListener
            }
            val selectedIndex = Messages.showChooseDialog(project, "Select Collection:", "Save Request", null, collectionNames, collectionNames[0])
            if (selectedIndex != -1) {
                val requestName = Messages.showInputDialog(project, "Request Name:", "Save", null)
                if (!requestName.isNullOrBlank()) {
                    val savedReq = ApiFirstSettings.SavedRequest(
                        name = requestName, method = methodCombo.selectedItem as String, url = urlField.text.trim(),
                        body = reqBodyArea.text.takeIf { it.isNotBlank() },
                        headers = getHeadersFromTable().toMutableMap(),
                        postResponseScript = reqScriptArea.text.takeIf { it.isNotBlank() }
                    )
                    settings.collections[selectedIndex].requests.add(savedReq)
                    updateCollectionsTree(settings)
                }
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
                if (index in 0 until savedRequestsList.size) { loadRequestToEditor(savedRequestsList[index]) }
            }
        }

        collectionsTree.addTreeSelectionListener {
            val node = collectionsTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            (node?.userObject as? ApiFirstSettings.SavedRequest)?.let { loadRequestToEditor(it) }
        }

        sendButton.addActionListener { executeRequest() }
        
        headersTableModel.addTableModelListener { e ->
            if (e.type == javax.swing.event.TableModelEvent.UPDATE && e.firstRow == headersTableModel.rowCount - 1) {
                val k = headersTableModel.getValueAt(e.firstRow, 0) as? String ?: ""
                val v = headersTableModel.getValueAt(e.firstRow, 1) as? String ?: ""
                if (k.isNotEmpty() || v.isNotEmpty()) {
                    ApplicationManager.getApplication().invokeLater { headersTableModel.addRow(arrayOf("", "")) }
                }
            }
        }

        paramsTableModel.addTableModelListener { e ->
            if (isSyncingParams) return@addTableModelListener
            if (e.type == javax.swing.event.TableModelEvent.UPDATE) {
                if (e.firstRow == paramsTableModel.rowCount - 1) {
                    val k = paramsTableModel.getValueAt(e.firstRow, 0) as? String ?: ""
                    val v = paramsTableModel.getValueAt(e.firstRow, 1) as? String ?: ""
                    if (k.isNotEmpty() || v.isNotEmpty()) {
                        ApplicationManager.getApplication().invokeLater { paramsTableModel.addRow(arrayOf("", "")) }
                    }
                }
                syncTableToUrl()
            }
        }
    }

    private fun loadRequestToEditor(req: ApiFirstSettings.SavedRequest) {
        methodCombo.selectedItem = req.method
        urlField.text = req.url
        reqBodyArea.text = req.body ?: ""
        reqScriptArea.text = req.postResponseScript ?: ""
        headersTableModel.rowCount = 0
        req.headers.forEach { (k, v) -> headersTableModel.addRow(arrayOf(k, v)) }
        headersTableModel.addRow(arrayOf("", ""))
        syncUrlToTable()
    }

    private fun syncUrlToTable() {
        if (isSyncingParams) return
        isSyncingParams = true
        try {
            val urlStr = urlField.text
            val queryIndex = urlStr.indexOf('?')
            if (queryIndex != -1) {
                paramsTableModel.rowCount = 0
                urlStr.substring(queryIndex + 1).split('&').forEach { pair ->
                    val idx = pair.indexOf('=')
                    val key = if (idx != -1) URLDecoder.decode(pair.substring(0, idx), "UTF-8") else pair
                    val value = if (idx != -1) URLDecoder.decode(pair.substring(idx + 1), "UTF-8") else ""
                    paramsTableModel.addRow(arrayOf(key, value))
                }
                paramsTableModel.addRow(arrayOf("", ""))
            }
        } catch (e: Exception) { } finally { isSyncingParams = false }
    }

    private fun syncTableToUrl() {
        if (isSyncingParams) return
        isSyncingParams = true
        try {
            val baseUrl = urlField.text.substringBefore('?')
            val sb = StringBuilder(baseUrl)
            var first = true
            for (i in 0 until paramsTableModel.rowCount) {
                val k = paramsTableModel.getValueAt(i, 0) as? String ?: ""
                val v = paramsTableModel.getValueAt(i, 1) as? String ?: ""
                if (k.isNotEmpty()) {
                    sb.append(if (first) "?" else "&").append(URLEncoder.encode(k, "UTF-8")).append("=").append(URLEncoder.encode(v, "UTF-8"))
                    first = false
                }
            }
            urlField.text = sb.toString()
        } catch (e: Exception) { } finally { isSyncingParams = false }
    }

    private fun updateCollectionsTree(settings: ApiFirstSettings) {
        collectionsRoot.removeAllChildren()
        settings.collections.forEach { coll ->
            val colNode = DefaultMutableTreeNode(coll)
            coll.requests.forEach { req -> colNode.add(DefaultMutableTreeNode(req)) }
            collectionsRoot.add(colNode)
        }
        collectionsTreeModel.reload()
    }

    private fun updateEnvCombo(settings: ApiFirstSettings) {
        envCombo.removeAllItems()
        envCombo.addItem("No Environment")
        settings.environments.forEach { envCombo.addItem(it.name) }
        envCombo.selectedItem = settings.activeEnvironmentName ?: "No Environment"
    }

    private fun getHeadersFromTable(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until headersTableModel.rowCount) {
            val k = headersTableModel.getValueAt(i, 0) as? String ?: ""
            val v = headersTableModel.getValueAt(i, 1) as? String ?: ""
            if (k.isNotBlank()) map[k.trim()] = v.trim()
        }
        return map
    }

    private fun executeRequest() {
        val method = methodCombo.selectedItem as String
        val url = urlField.text.trim()
        if (url.isEmpty()) return
        val settings = ApiFirstSettings.getInstance(project)
        val savedReq = ApiFirstSettings.SavedRequest(name = "$method $url", method = method, url = url, body = reqBodyArea.text.takeIf { it.isNotBlank() }, headers = getHeadersFromTable().toMutableMap(), postResponseScript = reqScriptArea.text.takeIf { it.isNotBlank() })
        settings.history.add(savedReq)
        savedRequestsList.add(0, savedReq)
        historyListModel.add(0, "$method $url")
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val response = executor.execute(ApiRequest(method, url, getHeadersFromTable(), reqBodyArea.text.takeIf { it.isNotBlank() }), settings.getActiveVariables())
                reqScriptArea.text.takeIf { it.isNotBlank() }?.let { scriptEngine.execute(it, response.body) }
                ApplicationManager.getApplication().invokeLater {
                    responseArea.text = try { GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(response.body)) } catch (e: Exception) { response.body }
                    resHeadersArea.text = response.headers.entries.joinToString("\n") { "${it.key}: ${it.value.joinToString(", ")}" }
                    statusLabel.text = "Status: ${response.statusCode} | Time: ${response.durationMs}ms"
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater { responseArea.text = "Error: ${e.message}" }
            }
        }
    }

    fun getContent(): JPanel = panel
}
