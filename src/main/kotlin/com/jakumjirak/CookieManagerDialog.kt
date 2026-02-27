package com.jakumjirak

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel

class CookieManagerDialog(private val project: Project, private val executor: ApiRequestExecutor) : DialogWrapper(project) {

    private val tableModel = DefaultTableModel(arrayOf("Domain", "Name", "Value", "Path", "Secure", "HttpOnly"), 0)
    private val table = JBTable(tableModel)

    init {
        title = "Cookie Manager"
        init()
        loadCookies()
    }

    private fun loadCookies() {
        tableModel.rowCount = 0
        executor.getCookies().forEach { cookie ->
            tableModel.addRow(arrayOf(
                cookie.domain ?: "",
                cookie.name ?: "",
                cookie.value ?: "",
                cookie.path ?: "",
                cookie.secure.toString(),
                cookie.isHttpOnly.toString()
            ))
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        table.setDefaultEditor(Any::class.java, null) // Make table read-only for now
        panel.add(JBScrollPane(table), BorderLayout.CENTER)
        
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val clearBtn = JButton("Clear All Cookies")
        clearBtn.addActionListener {
            executor.clearCookies()
            loadCookies()
        }
        buttonPanel.add(clearBtn)
        
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        panel.preferredSize = java.awt.Dimension(600, 300)
        return panel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
}
