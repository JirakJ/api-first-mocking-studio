package com.jakumjirak

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
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
import com.google.gson.*
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.io.File
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
    
    // Sidebar
    private val historyListModel = DefaultListModel<String>()
    private val historyList = JBList(historyListModel)
    private val savedRequestsList = mutableListOf<ApiFirstSettings.SavedRequest>()
    private val collectionsRoot = DefaultMutableTreeNode("Collections")
    private val collectionsTreeModel = DefaultTreeModel(collectionsRoot)
    private val collectionsTree = Tree(collectionsTreeModel)
    
    // Top Bar
    private val methodCombo = ComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"))
    private val urlField = JBTextField("https://httpbin.org/get")
    private val sendButton = JButton("Send")
    private val envCombo = ComboBox<String>()
    private val manageEnvButton = JButton(AllIcons.General.Settings)
    private val saveToCollectionButton = JButton(AllIcons.Actions.MenuSaveall)
    
    // Request Tabs
    private val paramsTableModel = DefaultTableModel(arrayOf("Key", "Value"), 0)
    private val paramsTable = JBTable(paramsTableModel)
    private val headersTableModel = DefaultTableModel(arrayOf("Key", "Value"), 0)
    private val headersTable = JBTable(headersTableModel)
    private val authTypeCombo = ComboBox(arrayOf("None", "Bearer Token", "Basic Auth", "API Key"))
    private val authFieldsPanel = JPanel(VerticalLayout(5))
    private val authFields = mutableMapOf<String, JBTextField>()
    
    // Body Panel
    private val bodyTypeCombo = ComboBox(arrayOf("None", "Raw", "Form-Data", "x-www-form-urlencoded"))
    private val bodyCardLayout = CardLayout()
    private val bodyCards = JPanel(bodyCardLayout)
    private val reqBodyArea = JBTextArea()
    private val formDataTableModel = DefaultTableModel(arrayOf("Type", "Key", "Value"), 0)
    private val formDataTable = JBTable(formDataTableModel)
    
    private val assertionsTableModel = DefaultTableModel(arrayOf("Type", "Expected Value"), 0)
    private val assertionsTable = JBTable(assertionsTableModel)
    private val snippetLanguageCombo = ComboBox(arrayOf("cURL", "Python (Requests)", "JavaScript (Fetch)"))
    private val snippetTextArea = JBTextArea()
    private val reqPreScriptArea = JBTextArea("// Pre-request logic")
    private val reqScriptArea = JBTextArea("// Post-response logic")
    private val reqTabs = JBTabbedPane()
    
    // Response
    private val responseArea = JBTextArea(); private val resHeadersArea = JBTextArea(); private val testResultsArea = JBTextArea()
    private val responseTree = Tree(DefaultTreeModel(DefaultMutableTreeNode("Response")))
    private val statusLabel = JBLabel("Ready")
    
    private val executor = ApiRequestExecutor(); private val preScriptEngine = PreRequestScriptEngine(project)
    private val scriptEngine = ResponseScriptEngine(project); private val assertionEngine = AssertionEngine()
    private val jsonTreeBuilder = JsonTreeModelBuilder(); private val codeGenerator = CodeGenerator()
    private val importer = Importer()
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
        formDataTableModel.addRow(arrayOf("Text", "", ""))

        // Setup Form Data Table with Type Column
        val typeColumn = formDataTable.columnModel.getColumn(0)
        typeColumn.cellEditor = DefaultCellEditor(ComboBox(arrayOf("Text", "File")))

        bodyCards.add(JPanel(), "None"); bodyCards.add(JBScrollPane(reqBodyArea), "Raw")
        bodyCards.add(JBScrollPane(formDataTable), "Form-Data"); bodyCards.add(JBScrollPane(formDataTable), "x-www-form-urlencoded")
        
        val bodyPanel = JPanel(BorderLayout()).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(bodyTypeCombo) }, BorderLayout.NORTH)
            add(bodyCards, BorderLayout.CENTER)
        }

        reqTabs.addTab("Params", JBScrollPane(paramsTable)); reqTabs.addTab("Auth", createAuthPanel()); reqTabs.addTab("Pre-Script", JBScrollPane(reqPreScriptArea))
        reqTabs.addTab("Body", bodyPanel); reqTabs.addTab("Headers", JBScrollPane(headersTable)); reqTabs.addTab("Tests", JBScrollPane(assertionsTable))
        reqTabs.addTab("Snippets", createSnippetPanel()); reqTabs.addTab("Post-Script", JBScrollPane(reqScriptArea))

        val responsePanel = JBTabbedPane().apply {
            addTab("Body", JBScrollPane(responseArea)); addTab("Tree", JBScrollPane(responseTree))
            addTab("Headers", JBScrollPane(resHeadersArea)); addTab("Test Results", JBScrollPane(testResultsArea))
        }
        
        val mainSplitter = OnePixelSplitter(false, 0.2f).apply {
            val sidebar = JBTabbedPane().apply { addTab("History", JBScrollPane(historyList)); addTab("Collections", createCollectionsPanel(settings)) }
            firstComponent = sidebar
            secondComponent = OnePixelSplitter(true, 0.5f).apply {
                firstComponent = JPanel(BorderLayout()).apply { add(topBar, BorderLayout.NORTH); add(reqTabs, BorderLayout.CENTER) }
                secondComponent = JPanel(BorderLayout()).apply { add(responsePanel, BorderLayout.CENTER); add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(statusLabel) }, BorderLayout.SOUTH) }
            }
        }
        panel.add(mainSplitter, BorderLayout.CENTER)
        
        responseArea.isEditable = false; resHeadersArea.isEditable = false; testResultsArea.isEditable = false
        responseArea.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
        resHeadersArea.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
        testResultsArea.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
    }

    private fun createAuthPanel() = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(JLabel("Type: ")); add(authTypeCombo) }, BorderLayout.NORTH)
        add(authFieldsPanel, BorderLayout.CENTER)
    }

    private fun createSnippetPanel() = JPanel(BorderLayout()).apply {
        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(JLabel("Language: ")); add(snippetLanguageCombo) }, BorderLayout.NORTH)
        snippetTextArea.isEditable = false; snippetTextArea.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
        add(JBScrollPane(snippetTextArea), BorderLayout.CENTER)
    }

    private fun createCollectionsPanel(settings: ApiFirstSettings) = JPanel(BorderLayout()).apply {
        val decorator = ToolbarDecorator.createDecorator(collectionsTree).setAddAction {
            val name = Messages.showInputDialog(project, "Collection Name:", "New", null)
            if (!name.isNullOrBlank()) { settings.collections.add(ApiFirstSettings.Collection(name)); updateCollectionsTree(settings) }
        }.setRemoveAction {
            val node = collectionsTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObj = node?.userObject
            if (userObj is ApiFirstSettings.Collection) settings.collections.remove(userObj)
            else if (userObj is ApiFirstSettings.SavedRequest) (node.parent as? DefaultMutableTreeNode)?.let { (it.userObject as? ApiFirstSettings.Collection)?.requests?.remove(userObj) }
            updateCollectionsTree(settings)
        }
        
        decorator.addExtraAction(object : AnAction("Import OpenAPI", "Import from OpenAPI file", AllIcons.Actions.Download) {
            override fun actionPerformed(e: AnActionEvent) {
                val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                val file = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null).choose(project).firstOrNull()
                if (file != null) {
                    val collection = importer.importOpenApi(file.path)
                    if (collection != null) {
                        settings.collections.add(collection); updateCollectionsTree(settings)
                        Messages.showInfoMessage(project, "Imported ${collection.requests.size} requests.", "Success")
                    } else {
                        Messages.showErrorDialog(project, "Failed to parse OpenAPI file.", "Error")
                    }
                }
            }
        })
        
        decorator.addExtraAction(object : AnAction("Import Collection", "Import collection from JSON", AllIcons.ToolbarDecorator.Import) {
            override fun actionPerformed(e: AnActionEvent) {
                val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
                val file = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null).choose(project).firstOrNull()
                if (file != null) {
                    try {
                        val json = File(file.path).readText()
                        val gson = GsonBuilder().create()
                        val collection = gson.fromJson(json, ApiFirstSettings.Collection::class.java)
                        
                        if (collection != null && collection.name.isNotEmpty()) {
                            settings.collections.add(collection)
                            updateCollectionsTree(settings)
                            Messages.showInfoMessage(project, "Successfully imported collection '${collection.name}' with ${collection.requests.size} requests.", "Import Complete")
                        } else {
                            Messages.showErrorDialog(project, "The selected file does not appear to be a valid exported Collection.", "Import Error")
                        }
                    } catch (ex: Exception) {
                        Messages.showErrorDialog(project, "Error reading or parsing file: ${ex.message}", "Import Error")
                    }
                }
            }
        })
        
        decorator.addExtraAction(object : AnAction("Export Collection", "Export to JSON", AllIcons.ToolbarDecorator.Export) {
            override fun actionPerformed(e: AnActionEvent) {
                val node = collectionsTree.lastSelectedPathComponent as? DefaultMutableTreeNode
                val userObj = node?.userObject
                if (userObj !is ApiFirstSettings.Collection) {
                    Messages.showInfoMessage(project, "Please select a Collection folder to export.", "Export Collection")
                    return
                }
                
                val descriptor = FileSaverDescriptor("Export Collection", "Save collection as JSON", "json")
                val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
                val wrapper = dialog.save(null as com.intellij.openapi.vfs.VirtualFile?, "${userObj.name.replace(" ", "_")}.json")
                
                if (wrapper != null) {
                    try {
                        val file = wrapper.file
                        val gson = GsonBuilder().setPrettyPrinting().create()
                        val json = gson.toJson(userObj)
                        file.writeText(json)
                        Messages.showInfoMessage(project, "Collection exported successfully.", "Export Complete")
                    } catch (ex: Exception) {
                        Messages.showErrorDialog(project, "Error exporting collection: ${ex.message}", "Export Error")
                    }
                }
            }
        })
        
        add(decorator.createPanel(), BorderLayout.CENTER)
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
        bodyTypeCombo.addActionListener { bodyCardLayout.show(bodyCards, bodyTypeCombo.selectedItem as String); refreshSnippets() }
        snippetLanguageCombo.addActionListener { refreshSnippets() }
        
        saveToCollectionButton.addActionListener {
            val names = settings.collections.map { it.name }
            if (names.isEmpty()) return@addActionListener Messages.showInfoMessage(project, "Create collection first", "Save")
            
            val step = object : BaseListPopupStep<String>("Select Collection", names) {
                override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice && selectedValue != null) {
                        val collectionIndex = settings.collections.indexOfFirst { it.name == selectedValue }
                        if (collectionIndex != -1) {
                            ApplicationManager.getApplication().invokeLater {
                                val reqName = Messages.showInputDialog(project, "Request Name:", "Save", null)
                                if (!reqName.isNullOrBlank()) {
                                    settings.collections[collectionIndex].requests.add(createSavedRequest(reqName))
                                    updateCollectionsTree(settings)
                                }
                            }
                        }
                    }
                    return FINAL_CHOICE
                }
            }
            JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(saveToCollectionButton)
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
                if (model.getValueAt(e.firstRow, 1).toString().isNotEmpty() || model.getValueAt(e.firstRow, 2).toString().isNotEmpty()) ApplicationManager.getApplication().invokeLater { model.addRow(arrayOf("Text", "", "")) }
            }
            refreshSnippets()
        }
        
        formDataTableModel.addTableModelListener { e ->
            if (e.type == javax.swing.event.TableModelEvent.UPDATE && e.column == 0) { // Type changed
                val row = e.firstRow
                val type = formDataTableModel.getValueAt(row, 0) as String
                if (type == "File") {
                    val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                    val file = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null).choose(project).firstOrNull()
                    if (file != null) {
                        formDataTableModel.setValueAt(file.path, row, 2)
                    }
                }
            }
            autoRow(e)
        }
        
        headersTableModel.addTableModelListener { e ->
            val model = e.source as DefaultTableModel
            if (e.type == javax.swing.event.TableModelEvent.UPDATE && e.firstRow == model.rowCount - 1) {
                if (model.getValueAt(e.firstRow, 0).toString().isNotEmpty() || model.getValueAt(e.firstRow, 1).toString().isNotEmpty()) ApplicationManager.getApplication().invokeLater { model.addRow(arrayOf("", "")) }
            }
            refreshSnippets()
        }
        paramsTableModel.addTableModelListener { if (!isSyncingParams) { 
            val model = it.source as DefaultTableModel
            if (it.type == javax.swing.event.TableModelEvent.UPDATE && it.firstRow == model.rowCount - 1) {
                if (model.getValueAt(it.firstRow, 0).toString().isNotEmpty() || model.getValueAt(it.firstRow, 1).toString().isNotEmpty()) ApplicationManager.getApplication().invokeLater { model.addRow(arrayOf("", "")) }
            }
            syncTableToUrl() 
        } }
        assertionsTableModel.addTableModelListener { e ->
            val model = e.source as DefaultTableModel
            if (e.type == javax.swing.event.TableModelEvent.UPDATE && e.firstRow == model.rowCount - 1) {
                if (model.getValueAt(e.firstRow, 0).toString().isNotEmpty() || model.getValueAt(e.firstRow, 1).toString().isNotEmpty()) ApplicationManager.getApplication().invokeLater { model.addRow(arrayOf("", "")) }
            }
            refreshSnippets()
        }
    }

    private fun createSavedRequest(name: String): ApiFirstSettings.SavedRequest {
        val assertions = mutableListOf<ApiFirstSettings.Assertion>()
        for (i in 0 until assertionsTableModel.rowCount) {
            val type = assertionsTableModel.getValueAt(i, 0) as? String ?: ""; val expected = assertionsTableModel.getValueAt(i, 1) as? String ?: ""
            if (type.isNotEmpty()) assertions.add(ApiFirstSettings.Assertion(type, "equals", expected))
        }
        val formData = mutableListOf<ApiFirstSettings.FormDataParam>()
        for (i in 0 until formDataTableModel.rowCount) {
            val type = formDataTableModel.getValueAt(i, 0) as? String ?: "Text"
            val k = formDataTableModel.getValueAt(i, 1) as? String ?: ""
            val v = formDataTableModel.getValueAt(i, 2) as? String ?: ""
            if (k.isNotEmpty()) formData.add(ApiFirstSettings.FormDataParam(k, v, type))
        }
        return ApiFirstSettings.SavedRequest(
            name = name, method = methodCombo.selectedItem as String, url = urlField.text.trim(),
            bodyType = bodyTypeCombo.selectedItem as String, body = reqBodyArea.text.takeIf { it.isNotBlank() },
            formData = formData, headers = getHeadersFromTable().toMutableMap(),
            preRequestScript = reqPreScriptArea.text.takeIf { it.isNotBlank() }, postResponseScript = reqScriptArea.text.takeIf { it.isNotBlank() },
            assertions = assertions, authType = authTypeCombo.selectedItem as String, authParams = authFields.mapValues { it.value.text }.toMutableMap()
        )
    }

    private fun loadRequestToEditor(req: ApiFirstSettings.SavedRequest) {
        methodCombo.selectedItem = req.method; urlField.text = req.url
        bodyTypeCombo.selectedItem = req.bodyType; reqBodyArea.text = req.body ?: ""
        formDataTableModel.rowCount = 0; req.formData.forEach { formDataTableModel.addRow(arrayOf(it.type, it.key, it.value)) }; formDataTableModel.addRow(arrayOf("Text", "", ""))
        reqPreScriptArea.text = req.preRequestScript ?: ""; reqScriptArea.text = req.postResponseScript ?: ""
        headersTableModel.rowCount = 0; req.headers.forEach { (k, v) -> headersTableModel.addRow(arrayOf(k, v)) }; headersTableModel.addRow(arrayOf("", ""))
        assertionsTableModel.rowCount = 0; req.assertions.forEach { assertionsTableModel.addRow(arrayOf(it.type, it.expectedValue)) }; assertionsTableModel.addRow(arrayOf("", ""))
        authTypeCombo.selectedItem = req.authType; updateAuthFields(); req.authParams.forEach { (k, v) -> authFields[k]?.text = v }; syncUrlToTable(); refreshSnippets()
    }

    private fun refreshSnippets() {
        val vars = ApiFirstSettings.getInstance(project).getActiveVariables()
        val req = createSavedRequest("Temp")
        snippetTextArea.text = when (snippetLanguageCombo.selectedItem as String) {
            "cURL" -> codeGenerator.generateCurl(req, vars)
            "Python (Requests)" -> codeGenerator.generatePython(req, vars)
            "JavaScript (Fetch)" -> codeGenerator.generateJsFetch(req, vars)
            else -> ""
        }
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
                val response = executor.execute(ApiRequest(method, url, getHeadersFromTable(), reqData.bodyType, reqData.body, reqData.formData, authTypeCombo.selectedItem as String, authFields.mapValues { it.value.text }), settings.getActiveVariables())
                reqScriptArea.text.takeIf { it.isNotBlank() }?.let { scriptEngine.execute(it, response.body) }
                val results = assertionEngine.verify(response, reqData.assertions)
                ApplicationManager.getApplication().invokeLater {
                    responseArea.text = try { GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(response.body)) } catch (e: Exception) { response.body }
                    responseTree.model = jsonTreeBuilder.build(response.body); resHeadersArea.text = response.headers.entries.joinToString("\n") { "${it.key}: ${it.value.joinToString(", ")}" }
                    testResultsArea.text = if (results.isEmpty()) "No tests defined." else results.joinToString("\n") { "${if (it.success) "✅" else "❌"} ${it.assertion.type}: Expected [${it.assertion.expectedValue}], Actual [${it.actualValue}]" }
                    statusLabel.text = "Status: ${response.statusCode} | Time: ${response.durationMs}ms | Tests: ${results.count { it.success }}/${results.size} passed"
                }
            } catch (e: Exception) { ApplicationManager.getApplication().invokeLater { responseArea.text = "Error: ${e.message}" } }
        }
    }

    fun getContent(): JPanel = panel
}
