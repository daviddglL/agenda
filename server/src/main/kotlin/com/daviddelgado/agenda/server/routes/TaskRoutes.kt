package com.daviddelgado.agenda.server.routes

import com.daviddelgado.agenda.server.dto.BulkDeleteRequest
import com.daviddelgado.agenda.server.dto.DeletedCountResponse
import com.daviddelgado.agenda.server.dto.ErrorResponse
import com.daviddelgado.agenda.server.dto.TaskDto
import com.daviddelgado.agenda.server.dto.validationError
import com.daviddelgado.agenda.server.realtime.TaskEventBroadcaster
import com.daviddelgado.agenda.server.repository.TaskRepository
import com.daviddelgado.agenda.server.security.requireUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.util.getOrFail
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame

fun Route.taskRoutes(taskRepository: TaskRepository) {
    authenticate("auth-jwt") {
        taskCrudRoutes(taskRepository)
        taskRealtimeRoute()
    }
}

private fun Route.taskCrudRoutes(taskRepository: TaskRepository) {
    get("/tasks") {
        call.respond(taskRepository.listForUser(call.requireUserId()))
    }

    post("/tasks") {
        val userId = call.requireUserId()
        val dto = call.receive<TaskDto>()
        dto.validationError()?.let { reason ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(reason))
            return@post
        }
        call.respond(HttpStatusCode.Created, taskRepository.create(userId, dto))
        TaskEventBroadcaster.notifyTasksChanged(userId)
    }

    put("/tasks/{id}") {
        val userId = call.requireUserId()
        val taskId = call.parameters.getOrFail("id")
        val dto = call.receive<TaskDto>()
        dto.validationError()?.let { reason ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(reason))
            return@put
        }

        if (!taskRepository.update(userId, taskId, dto)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Tarea no encontrada"))
            return@put
        }
        call.respond(dto.copy(id = taskId))
        TaskEventBroadcaster.notifyTasksChanged(userId)
    }

    patch("/tasks/{id}/toggle-completed") {
        val userId = call.requireUserId()
        val taskId = call.parameters.getOrFail("id")

        if (!taskRepository.toggleCompleted(userId, taskId)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Tarea no encontrada"))
            return@patch
        }
        call.respond(HttpStatusCode.NoContent)
        TaskEventBroadcaster.notifyTasksChanged(userId)
    }

    delete("/tasks/{id}") {
        val userId = call.requireUserId()
        val taskId = call.parameters.getOrFail("id")

        if (!taskRepository.deleteById(userId, taskId)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Tarea no encontrada"))
            return@delete
        }
        call.respond(HttpStatusCode.NoContent)
        TaskEventBroadcaster.notifyTasksChanged(userId)
    }

    // Borrado conjunto: varias tareas elegidas a la vez (p.ej. seleccion multiple en la UI).
    post("/tasks/bulk-delete") {
        val userId = call.requireUserId()
        val request = call.receive<BulkDeleteRequest>()
        val deleted = taskRepository.deleteByIds(userId, request.ids)
        call.respond(HttpStatusCode.OK, DeletedCountResponse(deleted))
        TaskEventBroadcaster.notifyTasksChanged(userId)
    }

    // Borrado conjunto total: todas las tareas del usuario de una vez.
    delete("/tasks") {
        val userId = call.requireUserId()
        val deleted = taskRepository.deleteAllForUser(userId)
        call.respond(HttpStatusCode.OK, DeletedCountResponse(deleted))
        TaskEventBroadcaster.notifyTasksChanged(userId)
    }
}

/**
 * Tiempo real (punto 3 de markdown.md): el cliente se conecta aqui con su Bearer access
 * token y recibe un aviso "tasks_changed" cada vez que sus tareas cambian desde otro
 * dispositivo o sesion. El cliente responde re-sincronizando (syncTasks()); no se manda el
 * estado completo por el socket, solo la senal de que hay novedades.
 */
private fun Route.taskRealtimeRoute() {
    webSocket("/tasks/ws") {
        val userId = call.requireUserId()
        TaskEventBroadcaster.register(userId, this)
        try {
            for (frame in incoming) {
                // No se espera nada del cliente; solo se mantiene la conexion abierta.
                if (frame is Frame.Close) break
            }
        } finally {
            TaskEventBroadcaster.unregister(userId, this)
        }
    }
}
