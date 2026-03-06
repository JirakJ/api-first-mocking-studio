package com.jakumjirak

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.openapi.ui.Messages
import io.swagger.v3.oas.models.OpenAPI

class GenerateHttpClientAction(private val project: Project, private val openApiProvider: () -> OpenAPI?) : AnAction("Generate HTTP File") {
    override fun actionPerformed(e: AnActionEvent) {
        val openApi = openApiProvider() ?: return
        val content = HttpClientGenerator().generate(openApi)
        val file = LightVirtualFile("api-requests.http", content)
        FileEditorManager.getInstance(project).openFile(file, true)
    }
}

class ToggleMockServerAction(private val project: Project, private val openApiProvider: () -> OpenAPI?) : AnAction("Start Mock Server") {
    private val mockServer = MockServer()

    override fun actionPerformed(e: AnActionEvent) {
        if (mockServer.isRunning()) {
            mockServer.stop()
            e.presentation.text = "Start Mock Server"
            Messages.showInfoMessage(project, "Mock Server stopped", "API First")
        } else {
            val openApi = openApiProvider() ?: return
            mockServer.start(8080, openApi)
            e.presentation.text = "Stop Mock Server"
            Messages.showInfoMessage(project, "Mock Server started on port 8080", "API First")
        }
    }
}
