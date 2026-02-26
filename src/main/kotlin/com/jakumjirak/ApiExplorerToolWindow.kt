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
import com.intellij.ui.components.panels.VerticalLayout
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
    
    private val authTypeCombo = ComboBox(arrayOf("None", "Bearer Token", "Basic Auth", "API Key"))
    private val authFieldsPanel = JPanel(VerticalLayout(5))
    private val authFields = mutableMapOf<String, JBTextField>()
    
    private val assertionsTableModel = DefaultTableModel(arrayOf("Type", "Expected Value"), 0)
    private val assertionsTable = JBTable(assertionsTableModel)
    
    private val snippetLanguageCombo = ComboBox(arrayOf("cURL", "Python (Requests)", "JavaScript (Fetch)"))
    private val snippetTextArea = JBTextArea()
    
    private val reqBodyArea = JBTextArea()
    private val reqPreScriptArea = JBTextArea("// Example: timestamp={{\$timestamp}}")
    private val reqScriptArea = JBTextArea("// Example: auth_token=$.token")
    private val reqTabs = JBTabbedPane()
    
    // Response Components
    private val responseArea = JBTextArea()
    private val resHeadersArea = JBTextArea()
    private val testResultsArea = JBTextArea()
    private val responseTree = Tree(DefaultTreeModel(DefaultMutableTreeNode("Response")))
    private val resTabs = JBTabbedPane()
    private val statusLabel = JBLabel("Ready")
    
    private val executor = ApiRequestExecutor()
    private val preScriptEngine = PreRequestScriptEngine(project)
    private val scriptEngine = ResponseScriptEngine(project)
    private val assertionEngine = AssertionEngine()
    private val jsonTreeBuilder = JsonTreeModelBuilder()
    private val codeGenerator = CodeGenerator()
    private var isSyncingParams = false

    init { setupUI(); setupListeners(); updateAuthFields(); refreshSnippets() }

    private fun setupUI() {
        val settings = ApiFirstSettings.getInstance(project)
        settings.history.reversed().forEach { req -> savedRequestsList.add(req); historyListModel.addElement("${req.method} ${req.url}") }
        updateCollectionsTree(settings); updateEnvCombo(settings)
        
        val topBar = JPanel(BorderLayout(5, 0)).apply {
            border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
            val left = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(envCombo); add(manageEnvButton); add(Box.createHorizontalStrut(5)); add(methodCombo) }
            add(left, BorderLayout.WEST); add(urlField, BorderLayout.CENTER)
            val right = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply { add(saveToCollectionButton); add(sendButton) }
            add(right, BorderLayout.EAST)
        }

        headersTableModel.addRow(arrayOf("Accept", "application/json")); headersTableModel.addRow(arrayOf("Content-Type", "application/json")); headersTableModel.addRow(arrayOf("", ""))
        paramsTableModel.addRow(arrayOf("", "")); assertionsTableModel.addRow(arrayOf("Status Code", "200")); assertionsTableModel.addRow(arrayOf("", ""))

        val authPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(JLabel("Type: ")); add(authTypeCombo) }, BorderLayout.NORTH)
            add(authFieldsPanel, BorderLayout.CENTER)
        }
        
        val snippetPanel = JPanel(BorderLayout()).apply {
            val top = JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(JLabel("Language: ")); add(snippetLanguageCombo) }
            add(top, BorderLayout.NORTH)
            snippetTextArea.isEditable = false
            snippetTextArea.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
            add(JBScrollPane(snippetTextArea), BorderLayout.CENTER)
        }

        reqTabs.addTab("Params", JBScrollPane(paramsTable)); reqTabs.addTab("Auth", authPanel); reqTabs.addTab("Pre-Script", JBScrollPane(reqPreScriptArea))
        reqTabs.addTab("Body", JBScrollPane(reqBodyArea)); reqTabs.addTab("Headers", JBScrollPane(headersTable)); reqTabs.addTab("Tests", JBScrollPane(assertionsTable))
        reqTabs.addTab("Snippets", snippetPanel); reqTabs.addTab("Post-Script", JBScrollPane(reqScriptArea))

        val requestPanel = JPanel(BorderLayout()).apply { add(topBar, BorderLayout.NORTH); add(reqTabs, BorderLayout.CENTER) }
        
        responseArea.isEditable = false; resHeadersArea.isEditable = false; testResultsArea.isEditable = false
        responseArea.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
        resHeadersArea.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
        testResultsArea.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
        
        resTabs.addTab("Body", JBScrollPane(responseArea)); resTabs.addTab("Tree", JBScrollPane(responseTree)); resTabs.addTab("Headers", JBScrollPane(resHeadersArea)); resTabs.addTab("Test Results", JBScrollPane(testResultsArea))

        val responsePanel = JPanel(BorderLayout()).apply { add(resTabs, BorderLayout.CENTER); add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(statusLabel) }, BorderLayout.SOUTH) }
        val editorSplitter = OnePixelSplitter(true, 0.5f).apply { firstComponent = requestPanel; secondComponent = responsePanel }
        val sidebarTabs = JBTabbedPane(); sidebarTabs.addTab("History", JBScrollPane(historyList))
        val collectionsPanel = JPanel(BorderLayout()).apply {
            add(ToolbarDecorator.createDecorator(collectionsTree).setAddAction {
                val name = Messages.showInputDialog(project, "Collection Name:", "New", null)
                if (!name.isNullOrBlank()) { settings.collections.add(ApiFirstSettings.Collection(name)); updateCollectionsTree(settings) }
            }.setRemoveAction {
                val node = collectionsTree.lastSelectedPathComponent as? DefaultMutableTreeNode
                val userObj = node?.userObject
                if (userObj is ApiFirstSettings.Collection) settings.collections.remove(userObj)
                else if (userObj is ApiFirstSettings.SavedRequest) (node.parent as? DefaultMutableTreeNode)?.let { (it.userObject as? ApiFirstSettings.Collection)?.requests?.remove(userObj) }
                updateCollectionsTree(settings)
            }.createPanel(), BorderLayout.CENTER)
        }
        sidebarTabs.addTab("Collections", collectionsPanel)
        panel.add(OnePixelSplitter(false, 0.2f).apply { firstComponent = sidebarTabs; secondComponent = editorSplitter }, BorderLayout.CENTER)
    }

    private fun updateAuthFields() {
        authFieldsPanel.removeAll(); authFields.clear()
        when (authTypeCombo.selectedItem as String) {
            "Bearer Token" -> addAuthField("token", "Token")
            "Basic Auth" -> { addAuthField("username", "Username"); addAuthField("password", "Password") }
            "API Key" -> { addAuthField("key", "Key"); addAuthField("value", "Value"); addAuthField("addTo", "Add to", "Header") }
        }
        authFieldsPanel.revalidate(); authFieldsPanel.repaint()
    }

    private fun addAuthField(id: String, label: String, default: String = "") {
        val p = JPanel(BorderLayout(5, 0)); p.add(JLabel("$label: "), BorderLayout.WEST)
        val tf = JBTextField(default); authFields[id] = tf; p.add(tf, BorderLayout.CENTER); authFieldsPanel.add(p)
    }

    private fun setupListeners() {
        val settings = ApiFirstSettings.getInstance(project)
        envCombo.addActionListener { settings.activeEnvironmentName = if (envCombo.selectedItem == "No Environment") null else envCombo.selectedItem as String; refreshSnippets() }
        manageEnvButton.addActionListener { if (ManageEnvironmentsDialog(project, settings).showAndGet()) updateEnvCombo(settings) }
        authTypeCombo.addActionListener { updateAuthFields(); refreshSnippets() }
        snippetLanguageCombo.addActionListener { refreshSnippets() }
        saveToCollectionButton.addActionListener {
            val names = settings.collections.map { it.name }.toTypedArray()
            if (names.isEmpty()) return@addActionListener Messages.showInfoMessage(project, "Create collection first", "Save")
            val sel = Messages.showChooseDialog(project, "Select Collection:", "Save", null, names, names[0])
            if (sel != -1) {
                val reqName = Messages.showInputDialog(project, "Request Name:", "Save", null)
                if (!reqName.isNullOrBlank()) { settings.collections[sel].requests.add(createSavedRequest(reqName)); updateCollectionsTree(settings) }
            }
        }
        urlField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { syncUrlToTable(); refreshSnippets() }
            override fun removeUpdate(e: DocumentEvent?) { syncUrlToTable(); refreshSnippets() }
            override fun changedUpdate(e: DocumentEvent?) { syncUrlToTable(); refreshSnippets() }
        })
        historyList.addListSelectionListener { if (!it.valueIsAdjusting) { val idx = historyList.selectedIndex; if (idx in 0 until savedRequestsList.size) loadRequestToEditor(savedRequestsList[idx]) } }
        collectionsTree.addTreeSelectionListener { val node = collectionsTree.lastSelectedPathComponent as? DefaultMutableTreeNode; (node?.userObject as? ApiFirstSettings.SavedRequest)?.let { loadRequestToEditor(it) } }
        sendButton.addActionListener { executeRequest() }
        val autoRow = { e: javax.swing.event.TableModelEvent ->
            val model = e.source as DefaultTableModel
            if (e.type == javax.swing.event.TableModelEvent.UPDATE && e.firstRow == model.rowCount - 1) {
                if (model.getValueAt(e.firstRow, 0).toString().isNotEmpty() || model.getValueAt(e.firstRow, 1).toString().isNotEmpty()) ApplicationManager.getApplication().invokeLater { model.addRow(arrayOf("", "")) }
            }
            refreshSnippets()
        }
        headersTableModel.addTableModelListener { autoRow(it) }; paramsTableModel.addTableModelListener { if (!isSyncingParams) { autoRow(it); syncTableToUrl() } }; assertionsTableModel.addTableModelListener { autoRow(it) }
    }

    private fun refreshSnippets() {
        val settings = ApiFirstSettings.getInstance(project)
        val request = createSavedRequest("Temporary")
        val vars = settings.getActiveVariables()
        snippetTextArea.text = when (snippetLanguageCombo.selectedItem as String) {
            "cURL" -> codeGenerator.generateCurl(request, vars)
            "Python (Requests)" -> codeGenerator.generatePython(request, vars)
            "JavaScript (Fetch)" -> codeGenerator.generateJsFetch(request, vars)
            else -> ""
        }
    }

    private fun createSavedRequest(name: String): ApiFirstSettings.SavedRequest {
        val assertions = mutableListOf<ApiFirstSettings.Assertion>()
        for (i in 0 until assertionsTableModel.rowCount) {
            val type = assertionsTableModel.getValueAt(i, 0) as? String ?: ""; val expected = assertionsTableModel.getValueAt(i, 1) as? String ?: ""
            if (type.isNotEmpty()) assertions.add(ApiFirstSettings.Assertion(type, "equals", expected))
        }
        return ApiFirstSettings.SavedRequest(
            name = name, method = methodCombo.selectedItem as String, url = urlField.text.trim(),
            body = reqBodyArea.text.takeIf { it.isNotBlank() }, headers = getHeadersFromTable().toMutableMap(),
            preRequestScript = reqPreScriptArea.text.takeIf { it.isNotBlank() }, postResponseScript = reqScriptArea.text.takeIf { it.isNotBlank() },
            assertions = assertions, authType = authTypeCombo.selectedItem as String, authParams = authFields.mapValues { it.value.text }.toMutableMap()
        )
    }

    private fun loadRequestToEditor(req: ApiFirstSettings.SavedRequest) {
        methodCombo.selectedItem = req.method; urlField.text = req.url; reqBodyArea.text = req.body ?: ""; reqPreScriptArea.text = req.preRequestScript ?: ""; reqScriptArea.text = req.postResponseScript ?: ""
        headersTableModel.rowCount = 0; req.headers.forEach { (k, v) -> headersTableModel.addRow(arrayOf(k, v)) }; headersTableModel.addRow(arrayOf("", ""))
        assertionsTableModel.rowCount = 0; req.assertions.forEach { assertionsTableModel.addRow(arrayOf(it.type, it.expectedValue)) }; assertionsTableModel.addRow(arrayOf("", ""))
        authTypeCombo.selectedItem = req.authType; updateAuthFields(); req.authParams.forEach { (k, v) -> authFields[k]?.text = v }; syncUrlToTable(); refreshSnippets()
    }

    private fun syncUrlToTable() {
        if (isSyncingParams) return
        isSyncingParams = true; try {
            val urlStr = urlField.text; val qIdx = urlStr.indexOf('?')
            if (qIdx != -1) {
                paramsTableModel.rowCount = 0; urlStr.substring(qIdx + 1).split('&').forEach { p ->
                    val i = p.indexOf('='); val k = if (i != -1) URLDecoder.decode(p.substring(0, i), "UTF-8") else p; val v = if (i != -1) URLDecoder.decode(p.substring(i + 1), "UTF-8") else ""
                    paramsTableModel.addRow(arrayOf(k, v))
                }; paramsTableModel.addRow(arrayOf("", ""))
            }
        } catch (e: Exception) { } finally { isSyncingParams = false }
    }

    private fun syncTableToUrl() {
        if (isSyncingParams) return
        isSyncingParams = true; try {
            val sb = StringBuilder(urlField.text.substringBefore('?')); var first = true
            for (i in 0 until paramsTableModel.rowCount) {
                val k = paramsTableModel.getValueAt(i, 0) as? String ?: ""; val v = paramsTableModel.getValueAt(i, 1) as? String ?: ""
                if (k.isNotEmpty()) { sb.append(if (first) "?" else "&").append(URLEncoder.encode(k, "UTF-8")).append("=").append(URLEncoder.encode(v, "UTF-8")); first = false }
            }
            urlField.text = sb.toString()
        } catch (e: Exception) { } finally { isSyncingParams = false }
    }

    private fun updateCollectionsTree(settings: ApiFirstSettings) {
        collectionsRoot.removeAllChildren(); settings.collections.forEach { coll -> val node = DefaultMutableTreeNode(coll); coll.requests.forEach { node.add(DefaultMutableTreeNode(it)) }; collectionsRoot.add(node) }; collectionsTreeModel.reload()
    }

    private fun updateEnvCombo(settings: ApiFirstSettings) {
        envCombo.removeAllItems(); envCombo.addItem("No Environment"); settings.environments.forEach { envCombo.addItem(it.name) }; envCombo.selectedItem = settings.activeEnvironmentName ?: "No Environment"
    }

    private fun getHeadersFromTable(): Map<String, String> {
        val map = mutableMapOf<String, String>(); for (i in 0 until headersTableModel.rowCount) { val k = headersTableModel.getValueAt(i, 0) as? String ?: ""; val v = headersTableModel.getValueAt(i, 1) as? String ?: ""; if (k.isNotBlank()) map[k.trim()] = v.trim() }
        return map
    }

    private fun executeRequest() {
        val method = methodCombo.selectedItem as String; val url = urlField.text.trim()
        if (url.isEmpty()) return
        val settings = ApiFirstSettings.getInstance(project); reqPreScriptArea.text.takeIf { it.isNotBlank() }?.let { preScriptEngine.execute(it) }
        val reqData = createSavedRequest("History Entry"); settings.history.add(reqData); savedRequestsList.add(0, reqData); historyListModel.add(0, "$method $url")
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val request = ApiRequest(method, url, getHeadersFromTable(), reqBodyArea.text.takeIf { it.isNotBlank() }, authTypeCombo.selectedItem as String, authFields.mapValues { it.value.text })
                val response = executor.execute(request, settings.getActiveVariables())
                reqScriptArea.text.takeIf { it.isNotBlank() }?.let { scriptEngine.execute(it, response.body) }
                val results = assertionEngine.verify(response, reqData.assertions)
                ApplicationManager.getApplication().invokeLater {
                    responseArea.text = try { GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(response.body)) } catch (e: Exception) { response.body }
                    responseTree.model = jsonTreeBuilder.build(response.body)
                    resHeadersArea.text = response.headers.entries.joinToString("\n") { "${it.key}: ${it.value.joinToString(", ")}" }
                    testResultsArea.text = if (results.isEmpty()) "No tests defined." else results.joinToString("\n") { "${if (it.success) "✅" else "❌"} ${it.assertion.type}: Expected [${it.assertion.expectedValue}], Actual [${it.actualValue}]" }
                    statusLabel.text = "Status: ${response.statusCode} | Time: ${response.durationMs}ms | Tests: ${results.count { it.success }}/${results.size} passed"
                }
            } catch (e: Exception) { ApplicationManager.getApplication().invokeLater { responseArea.text = "Error: ${e.message}" } }
        }
    }

    fun getContent(): JPanel = panel
}
