package com.daviddelgado.agenda.server.routes

import com.daviddelgado.agenda.server.dto.ErrorResponse
import com.daviddelgado.agenda.server.dto.FcmTokenRequest
import com.daviddelgado.agenda.server.dto.UserResponse
import com.daviddelgado.agenda.server.dto.validationError
import com.daviddelgado.agenda.server.repository.FcmTokenRepository
import com.daviddelgado.agenda.server.repository.UserRepository
import com.daviddelgado.agenda.server.security.requireUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.pipeline.PipelineContext

fun Route.userRoutes(
    userRepository: UserRepository,
    fcmTokenRepository: FcmTokenRepository,
) {
    authenticate("auth-jwt") {
        get("/users/me") {
            val user = userRepository.findById(call.requireUserId())

            if (user == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Usuario no encontrado"))
                return@get
            }
            call.respond(UserResponse(user.id, user.name, user.email))
        }

        // Borrado conjunto: elimina la cuenta y, en cascada (ON DELETE CASCADE), todas sus tareas.
        delete("/users/me") {
            userRepository.delete(call.requireUserId())
            call.respond(HttpStatusCode.NoContent)
        }

        // Recordatorios push (Task 9): el cliente Android manda aqui su token FCM tras el
        // login y cada vez que Firebase se lo renueva (ver AgendaFirebaseMessagingService).
        post("/users/me/fcm-token") {
            val request = call.receive<FcmTokenRequest>()
            if (respondIfInvalid(request)) return@post
            fcmTokenRepository.upsert(call.requireUserId(), request.token)
            call.respond(HttpStatusCode.NoContent)
        }

        // Al hacer logout el cliente borra su asociacion con este usuario (ver
        // AuthRepositoryImpl.logout()): sin esto, el mismo dispositivo podia quedar recibiendo
        // los recordatorios de dos usuarios distintos tras cerrar sesion e iniciar con otro.
        delete("/users/me/fcm-token") {
            val request = call.receive<FcmTokenRequest>()
            if (respondIfInvalid(request)) return@delete
            fcmTokenRepository.delete(call.requireUserId(), request.token)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/**
 * Si `request` no es valida, responde 400 con el motivo y devuelve true (el llamador debe
 * cortar con `return@post` sin tocar el repositorio). Si es valida, no responde nada y
 * devuelve false.
 */
private suspend fun PipelineContext<Unit, ApplicationCall>.respondIfInvalid(request: FcmTokenRequest): Boolean {
    val reason = request.validationError() ?: return false
    call.respond(HttpStatusCode.BadRequest, ErrorResponse(reason))
    return true
}
