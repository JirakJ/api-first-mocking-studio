package com.jakumjirak

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import com.intellij.openapi.application.ApplicationManager

class WebSocketManager(private val onMessage: (String, Boolean) -> Unit, private val onStatusChange: (Boolean, String) -> Unit) {
    private var webSocket: WebSocket? = null
    private val client = HttpClient.newHttpClient()

    fun connect(url: String, headers: Map<String, String>) {
        try {
            val builder = client.newWebSocketBuilder()
            headers.forEach { (k, v) -> builder.header(k, v) }
            
            webSocket = builder.buildAsync(URI.create(url), object : WebSocket.Listener {
                override fun onOpen(webSocket: WebSocket) {
                    ApplicationManager.getApplication().invokeLater {
                        onStatusChange(true, "Connected to $url")
                    }
                    webSocket.request(1)
                }

                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    ApplicationManager.getApplication().invokeLater {
                        onMessage(data.toString(), false)
                    }
                    webSocket.request(1)
                    return null
                }

                override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
                    ApplicationManager.getApplication().invokeLater {
                        onStatusChange(false, "Disconnected ($statusCode): $reason")
                    }
                    return null
                }

                override fun onError(webSocket: WebSocket, error: Throwable) {
                    ApplicationManager.getApplication().invokeLater {
                        onStatusChange(false, "Error: ${error.message}")
                    }
                }
            }).join()
        } catch (e: Exception) {
            onStatusChange(false, "Connection failed: ${e.message}")
        }
    }

    fun sendMessage(message: String) {
        webSocket?.let {
            it.sendText(message, true)
            onMessage(message, true)
        } ?: run {
            onStatusChange(false, "Not connected")
        }
    }

    fun disconnect() {
        webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "User disconnected")
        webSocket = null
        onStatusChange(false, "Disconnected by user")
    }

    fun isConnected(): Boolean = webSocket != null
}
