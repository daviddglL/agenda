package com.daviddelgado.agenda.network.api

import com.daviddelgado.agenda.network.WebSocketService
import com.daviddelgado.agenda.network.apiCall
import com.daviddelgado.agenda.network.dto.BulkDeleteRequest
import com.daviddelgado.agenda.network.dto.DeletedCountResponse
import com.daviddelgado.agenda.network.dto.TaskDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow

/** Ruta del canal de tiempo real del modulo :server (`webSocket("/tasks/ws")`). */
private const val TASKS_WEBSOCKET_PATH = "tasks/ws"

/** Endpoints de `/tasks` del modulo :server, incluido el borrado conjunto. */
class TaskApi(private val client: HttpClient) {
    private val webSocketService = WebSocketService(client)
    suspend fun getAll(): List<TaskDto> = apiCall { client.get("tasks").body() }

    /**
     * Crea la tarea. El servidor respeta el `id` que manda el cliente y, si ya existe, la
     * actualiza: asi reintentar la subida de una tarea creada sin red es idempotente y no
     * genera duplicados.
     */
    suspend fun create(task: TaskDto): TaskDto =
        apiCall {
            client.post("tasks") {
                contentType(ContentType.Application.Json)
                setBody(task)
            }.body()
        }

    suspend fun update(task: TaskDto): TaskDto =
        apiCall {
            client.put("tasks/${task.id}") {
                contentType(ContentType.Application.Json)
                setBody(task)
            }.body()
        }

    suspend fun toggleCompleted(taskId: String) {
        apiCall { client.patch("tasks/$taskId/toggle-completed") }
    }

    suspend fun delete(taskId: String) {
        apiCall { client.delete("tasks/$taskId") }
    }

    /** Borrado conjunto de varias tareas en una sola llamada. */
    suspend fun bulkDelete(ids: List<String>): Int =
        apiCall {
            client.post("tasks/bulk-delete") {
                contentType(ContentType.Application.Json)
                setBody(BulkDeleteRequest(ids))
            }.body<DeletedCountResponse>().deleted
        }

    /** Borrado conjunto total: todas las tareas del usuario. */
    suspend fun deleteAll(): Int = apiCall { client.delete("tasks").body<DeletedCountResponse>().deleted }

    /**
     * Flujo de avisos "tasks_changed" del servidor (tiempo real, punto 3 de markdown.md).
     * Una sola conexion; si se corta, termina el flujo (quien lo consuma decide si reconecta).
     */
    fun observeChanges(): Flow<String> = webSocketService.observe(TASKS_WEBSOCKET_PATH)
}
