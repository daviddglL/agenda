package com.daviddelgado.agenda.server.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/** Comprueba el canal de tiempo real de `/tasks/ws` (punto 3 de markdown.md). */
class TaskRealtimeTest {
    @Test
    fun crearUnaTareaAvisaPorWebSocketAQuienEstaEscuchando() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken
            // client.webSocket(...) siempre devuelve Unit (es un DSL, no una funcion que
            // devuelva el resultado del bloque), asi que el mensaje se captura en una
            // variable externa en vez de intentar devolverlo desde el bloque.
            var mensajeRecibido: String? = null

            withTimeout(WEBSOCKET_TEST_TIMEOUT_MILLIS) {
                client.webSocket("/tasks/ws", request = { bearerAuth(token) }) {
                    // El registro del listener es async en el servidor; un pequeno margen
                    // evita la carrera con el POST /tasks que se lanza justo despues.
                    val creada =
                        async {
                            delay(WEBSOCKET_REGISTRATION_GRACE_MILLIS)
                            client.crearTareaDePrueba(token)
                        }
                    mensajeRecibido = (incoming.receive() as Frame.Text).readText()
                    creada.await()
                }
            }

            assertEquals("tasks_changed", mensajeRecibido)
        }

    @Test
    fun sinTokenLaConexionWebSocketNoSeAcepta() =
        withApi { client ->
            val error =
                runCatching {
                    client.webSocket("/tasks/ws") {
                        incoming.receive()
                    }
                }
            assertEquals(true, error.isFailure)
        }
}

private const val WEBSOCKET_TEST_TIMEOUT_MILLIS = 5_000L
private const val WEBSOCKET_REGISTRATION_GRACE_MILLIS = 200L

private suspend fun HttpClient.crearTareaDePrueba(token: String) {
    post("/tasks") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(tareaDeEjemplo(id = "tarea-realtime"))
    }
}
