package com.jakumjirak

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.swagger.v3.oas.models.OpenAPI
import io.ktor.http.HttpStatusCode

/**
 * Embedded Mock Server using Ktor.
 */
class MockServer {
    private var server: NettyApplicationEngine? = null

    fun start(port: Int, openApi: OpenAPI) {
        stop() // Ensure previous instance is stopped
        
        server = embeddedServer(Netty, port = port) {
            routing {
                openApi.paths?.forEach { (path, pathItem) ->
                    val cleanPath = path.replace("{", ":").replace("}", "")
                    
                    pathItem.readOperationsMap().forEach { (method, operation) ->
                        route(cleanPath, io.ktor.http.HttpMethod.parse(method.name)) {
                            handle {
                                // Basic logic: return 200 and a dummy JSON
                                call.respondText(
                                    """{"message": "Mocked response for ${operation.operationId ?: path}"}""",
                                    io.ktor.http.ContentType.Application.Json
                                )
                            }
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    fun isRunning(): Boolean = server != null
}
