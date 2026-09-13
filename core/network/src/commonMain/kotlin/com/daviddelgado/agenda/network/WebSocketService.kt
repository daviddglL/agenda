package com.daviddelgado.agenda.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Comunicacion bidireccional en tiempo real via Ktor WebSockets (punto 3 de markdown.md). */
class WebSocketService(private val client: HttpClient) {
    fun observe(path: String): Flow<String> =
        flow {
            client.webSocket(path = path) {
                for (frame in incoming) {
                    if (frame is Frame.Text) emit(frame.readText())
                }
            }
        }
}
