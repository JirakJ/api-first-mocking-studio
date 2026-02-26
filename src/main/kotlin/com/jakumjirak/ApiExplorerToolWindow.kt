package com.jakumjirak

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ui.JBUI

class ApiExplorerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val explorer = ApiExplorerToolWindow(project)
        val content = ContentFactory.getInstance().createContent(explorer.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ApiExplorerToolWindow(private val project: Project) {
    private val splitter = OnePixelSplitter(false, 0.3f)
    private val tree = Tree()
    private val responseArea = JBTextArea()
    private val statusLabel = JBLabel("Ready")
    private val panel: JPanel = JPanel(BorderLayout())
    
    private var currentOpenApi: io.swagger.v3.oas.models.OpenAPI? = null
    private val executor = ApiRequestExecutor()
    private val scriptEngine = ResponseScriptEngine(project)

    init {
        setupTree()
        setupResponsePanel()
        
        val toolbar = setupToolbar()
        
        val leftPanel = JPanel(BorderLayout())
        leftPanel.add(JBScrollPane(tree), BorderLayout.CENTER)
        
        val rightPanel = JPanel(BorderLayout())
        rightPanel.add(JBScrollPane(responseArea), BorderLayout.CENTER)
        rightPanel.add(statusLabel, BorderLayout.SOUTH)
        
        splitter.firstComponent = leftPanel
        splitter.secondComponent = rightPanel
        
        panel.add(toolbar, BorderLayout.NORTH)
        panel.add(splitter, BorderLayout.CENTER)
    }

    private fun setupTree() {
        tree.emptyText.text = "No API loaded"
    }

    private fun setupResponsePanel() {
        responseArea.isEditable = false
        // Fixed background access
        responseArea.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
    }

    private fun setupToolbar(): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            add(GenerateHttpClientAction(project) { currentOpenApi })
            add(object : com.intellij.openapi.actionSystem.AnAction("Execute Selected") {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    executeSelectedRequest()
                }
            })
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("ApiExplorer", actionGroup, true)
        toolbar.targetComponent = panel
        return toolbar.component
    }

    private fun executeSelectedRequest() {
        val node = tree.lastSelectedPathComponent as? javax.swing.tree.DefaultMutableTreeNode ?: return
        val userObject = node.userObject.toString()
        
        if (userObject.contains(":")) {
            val method = userObject.substringBefore(":").trim()
            val path = (node.parent as javax.swing.tree.DefaultMutableTreeNode).userObject.toString()
            
            val settings = ApiFirstSettings.getInstance(project)
            val variables = settings.variables
            
            statusLabel.text = "Executing $method $path..."
            
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val request = ApiRequest(method, "https://postman-echo.com$path") 
                    val response = executor.execute(request, variables)
                    
                    // Scripting simulation: if path contains auth, extract token
                    if (path.contains("login") || path.contains("auth") || response.body.contains("token")) {
                        scriptEngine.execute("auth_token=$.token", response.body)
                    }
                    
                    ApplicationManager.getApplication().invokeLater {
                        responseArea.text = "Status: ${response.statusCode}\n" +
                                "Time: ${response.durationMs}ms\n\n" +
                                "Body:\n${response.body}"
                        statusLabel.text = "Completed in ${response.durationMs}ms"
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        responseArea.text = "Error: ${e.message}"
                        statusLabel.text = "Failed"
                    }
                }
            }
        }
    }

    fun updateTree(model: javax.swing.tree.TreeModel, openApi: io.swagger.v3.oas.models.OpenAPI) {
        this.currentOpenApi = openApi
        tree.model = model
    }

    fun getContent(): JPanel = panel
}
