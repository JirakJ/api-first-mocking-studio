package com.jakumjirak

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.ui.ToolbarDecorator
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel
import com.intellij.openapi.ui.Messages
import javax.swing.event.ListSelectionListener

class ManageEnvironmentsDialog(private val project: Project, private val settings: ApiFirstSettings) : DialogWrapper(project) {

    private val envListModel = DefaultListModel<String>()
    private val envList = JBList(envListModel)
    
    private val varsTableModel = DefaultTableModel(arrayOf("Key", "Value"), 0)
    private val varsTable = JBTable(varsTableModel)
    
    private var currentSelectedEnv: ApiFirstSettings.Environment? = null

    init {
        title = "Manage Environments"
        init()
        loadData()
        setupListeners()
    }

    private fun loadData() {
        envListModel.clear()
        settings.environments.forEach { envListModel.addElement(it.name) }
        if (envListModel.size() > 0) {
            envList.selectedIndex = 0
        }
    }

    private fun setupListeners() {
        envList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                saveCurrentVars()
                val selectedName = envList.selectedValue
                currentSelectedEnv = settings.environments.find { it.name == selectedName }
                loadVarsForSelected()
            }
        }

        // Add empty row automatically
        varsTableModel.addTableModelListener { e ->
            if (e.type == javax.swing.event.TableModelEvent.UPDATE) {
                val row = e.firstRow
                val lastRow = varsTableModel.rowCount - 1
                if (row == lastRow && lastRow >= 0) {
                    val key = varsTableModel.getValueAt(lastRow, 0) as? String ?: ""
                    val value = varsTableModel.getValueAt(lastRow, 1) as? String ?: ""
                    if (key.isNotEmpty() || value.isNotEmpty()) {
                        SwingUtilities.invokeLater {
                            varsTableModel.addRow(arrayOf("", ""))
                        }
                    }
                }
            }
        }
    }

    private fun loadVarsForSelected() {
        varsTableModel.rowCount = 0
        currentSelectedEnv?.variables?.forEach { (k, v) ->
            varsTableModel.addRow(arrayOf(k, v))
        }
        varsTableModel.addRow(arrayOf("", "")) // Empty row for new entries
    }

    private fun saveCurrentVars() {
        if (varsTable.isEditing) {
            varsTable.cellEditor.stopCellEditing()
        }
        currentSelectedEnv?.let { env ->
            val newVars = mutableMapOf<String, String>()
            for (i in 0 until varsTableModel.rowCount) {
                val k = varsTableModel.getValueAt(i, 0)?.toString()?.trim() ?: ""
                val v = varsTableModel.getValueAt(i, 1)?.toString()?.trim() ?: ""
                if (k.isNotEmpty()) {
                    newVars[k] = v
                }
            }
            env.variables = newVars
        }
    }

    override fun createCenterPanel(): JComponent {
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        
        // Left Panel - Environment List
        val envDecorator = ToolbarDecorator.createDecorator(envList)
            .setAddAction {
                val name = Messages.showInputDialog(project, "Environment Name:", "New Environment", null)
                if (!name.isNullOrBlank() && settings.environments.none { it.name == name }) {
                    settings.environments.add(ApiFirstSettings.Environment(name))
                    envListModel.addElement(name)
                    envList.setSelectedValue(name, true)
                }
            }
            .setRemoveAction {
                val selected = envList.selectedValue
                if (selected != null) {
                    settings.environments.removeIf { it.name == selected }
                    envListModel.removeElement(selected)
                    if (settings.activeEnvironmentName == selected) {
                        settings.activeEnvironmentName = null
                    }
                }
            }
            .createPanel()
            
        val leftPanel = JPanel(BorderLayout())
        leftPanel.add(JLabel("Environments"), BorderLayout.NORTH)
        leftPanel.add(envDecorator, BorderLayout.CENTER)
        
        // Right Panel - Variables Table
        val varsDecorator = ToolbarDecorator.createDecorator(varsTable).createPanel()
        val rightPanel = JPanel(BorderLayout())
        rightPanel.add(JLabel("Variables"), BorderLayout.NORTH)
        rightPanel.add(varsDecorator, BorderLayout.CENTER)

        splitPane.leftComponent = leftPanel
        splitPane.rightComponent = rightPanel
        splitPane.dividerLocation = 200

        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = java.awt.Dimension(600, 400)
        mainPanel.add(splitPane, BorderLayout.CENTER)

        return mainPanel
    }

    override fun doOKAction() {
        saveCurrentVars()
        super.doOKAction()
    }
}
