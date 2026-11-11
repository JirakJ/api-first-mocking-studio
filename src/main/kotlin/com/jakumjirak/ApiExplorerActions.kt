package com.jakumjirak

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.testFramework.LightVirtualFile
import io.swagger.v3.oas.models.OpenAPI
import java.io.IOException

private fun chooseOpenApi(project: Project): OpenAPI? {
    val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
        .withTitle("Select OpenAPI / Swagger Specification")
    val file = FileChooser.chooseFile(descriptor, project, null) ?: return null
    val openApi = OpenApiParser().parseFromFile(file.path)
    if (openApi == null) {
        Messages.showErrorDialog(project, "Failed to parse ${file.name} as an OpenAPI/Swagger specification.", "API First")
    }
    return openApi
}

/** Tools | API First | Generate HTTP Client File: writes a `.http` scratch with one request per operation. */
class GenerateHttpClientAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val openApi = chooseOpenApi(project) ?: return
        val file = LightVirtualFile("api-requests.http", HttpClientGenerator().generate(openApi))
        FileEditorManager.getInstance(project).openFile(file, true)
    }
}

/** Tools | API First | Start/Stop Mock Server: serves an OpenAPI spec on the port from Settings. */
class ToggleMockServerAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        if (project != null) {
            val running = MockServerService.getInstance(project).server.isRunning()
            e.presentation.text = if (running) "Stop Mock Server" else "Start Mock Server…"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val server = MockServerService.getInstance(project).server
        if (server.isRunning()) {
            server.stop()
            Messages.showInfoMessage(project, "Mock server stopped.", "API First")
            return
        }
        val openApi = chooseOpenApi(project) ?: return
        val port = ApiFirstPluginSettings.getInstance(project).mockServerPort
        try {
            server.start(port, openApi)
            Messages.showInfoMessage(project, "Mock server running on http://${MockServer.LOOPBACK}:$port", "API First")
        } catch (ex: IOException) {
            Messages.showErrorDialog(project, "Could not start the mock server on port $port: ${ex.message}", "API First")
        } catch (ex: IllegalArgumentException) {
            Messages.showErrorDialog(project, "Could not start the mock server: ${ex.message}", "API First")
        }
    }
}
