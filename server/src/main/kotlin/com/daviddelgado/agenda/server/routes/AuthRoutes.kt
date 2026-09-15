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
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

fun Route.authRoutes(
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    rateLimit(RateLimitName("auth")) {
        post("/auth/register") { handleRegister(call, userRepository, jwtConfig) }
        post("/auth/login") { handleLogin(call, userRepository, jwtConfig) }
    }

    post("/auth/refresh") { handleRefresh(call, userRepository, jwtConfig) }
}

private suspend fun handleRegister(
    call: ApplicationCall,
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    val request = call.receive<RegisterRequest>()

    if (call.respondIfInvalidRegisterRequest(request)) {
        return
    }

    if (userRepository.findByEmail(request.email) != null) {
        call.respond(HttpStatusCode.Conflict, ErrorResponse("Ya existe una cuenta con ese email"))
        return
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

private suspend fun handleLogin(
    call: ApplicationCall,
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    val request = call.receive<LoginRequest>()
    val user = userRepository.findByEmail(request.email)

    if (user == null || !PasswordHasher.matches(request.password, user.passwordHash)) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Email o contrasena incorrectos"))
        return
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

private suspend fun handleRefresh(
    call: ApplicationCall,
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    val request = call.receive<RefreshRequest>()
    val userId = jwtConfig.verifyRefreshToken(request.refreshToken)
    val user = userId?.let(userRepository::findById)

    if (user == null) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Refresh token invalido o caducado"))
        return
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

private suspend fun ApplicationCall.respondIfInvalidRegisterRequest(request: RegisterRequest): Boolean {
    val emailValido = emailRegex.matches(request.email)
    val isInvalid = request.name.isBlank() || !emailValido || request.password.length < 6
    if (isInvalid) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Datos invalidos: email y contrasena minima 6"))
    }
    return isInvalid
}
