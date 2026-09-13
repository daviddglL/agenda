package com.daviddelgado.agenda.server.routes

import com.daviddelgado.agenda.server.dto.AuthResponse
import com.daviddelgado.agenda.server.dto.ErrorResponse
import com.daviddelgado.agenda.server.dto.LoginRequest
import com.daviddelgado.agenda.server.dto.RefreshRequest
import com.daviddelgado.agenda.server.dto.RegisterRequest
import com.daviddelgado.agenda.server.repository.UserRepository
import com.daviddelgado.agenda.server.security.JwtConfig
import com.daviddelgado.agenda.server.security.PasswordHasher
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.authRoutes(
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    post("/auth/register") {
        val request = call.receive<RegisterRequest>()

        if (request.name.isBlank() || request.email.isBlank() || request.password.length < 6) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Datos invalidos: contrasena minima 6 caracteres"))
            return@post
        }
        if (userRepository.findByEmail(request.email) != null) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("Ya existe una cuenta con ese email"))
            return@post
        }

        val user = userRepository.create(request.name, request.email, PasswordHasher.hash(request.password))
        call.respond(
            HttpStatusCode.Created,
            AuthResponse(
                userId = user.id,
                name = user.name,
                email = user.email,
                accessToken = jwtConfig.generateAccessToken(user.id),
                refreshToken = jwtConfig.generateRefreshToken(user.id),
            ),
        )
    }

    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val user = userRepository.findByEmail(request.email)

        if (user == null || !PasswordHasher.matches(request.password, user.passwordHash)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Email o contrasena incorrectos"))
            return@post
        }

        call.respond(
            AuthResponse(
                userId = user.id,
                name = user.name,
                email = user.email,
                accessToken = jwtConfig.generateAccessToken(user.id),
                refreshToken = jwtConfig.generateRefreshToken(user.id),
            ),
        )
    }

    post("/auth/refresh") {
        val request = call.receive<RefreshRequest>()
        val userId = jwtConfig.verifyRefreshToken(request.refreshToken)
        val user = userId?.let(userRepository::findById)

        if (user == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Refresh token invalido o caducado"))
            return@post
        }

        call.respond(
            AuthResponse(
                userId = user.id,
                name = user.name,
                email = user.email,
                accessToken = jwtConfig.generateAccessToken(user.id),
                refreshToken = jwtConfig.generateRefreshToken(user.id),
            ),
        )
    }
}
