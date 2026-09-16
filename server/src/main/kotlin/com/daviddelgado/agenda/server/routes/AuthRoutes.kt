package com.daviddelgado.agenda.server.routes

import com.daviddelgado.agenda.server.dto.AuthResponse
import com.daviddelgado.agenda.server.dto.ErrorResponse
import com.daviddelgado.agenda.server.dto.ForgotPasswordRequest
import com.daviddelgado.agenda.server.dto.LoginRequest
import com.daviddelgado.agenda.server.dto.RefreshRequest
import com.daviddelgado.agenda.server.dto.RegisterRequest
import com.daviddelgado.agenda.server.dto.ResetPasswordRequest
import com.daviddelgado.agenda.server.dto.validationError
import com.daviddelgado.agenda.server.email.EmailSender
import com.daviddelgado.agenda.server.repository.PasswordResetRepository
import com.daviddelgado.agenda.server.repository.UserRepository
import com.daviddelgado.agenda.server.security.JwtConfig
import com.daviddelgado.agenda.server.security.PasswordHasher
import com.daviddelgado.agenda.server.security.sha256Hex
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.security.SecureRandom
import java.time.Instant

private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private const val RESET_CODE_EXPIRY_MINUTES = 15L
private const val RESET_CODE_MAX_ATTEMPTS = 5

fun Route.authRoutes(
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
    passwordResetRepository: PasswordResetRepository,
    emailSender: EmailSender,
) {
    rateLimit(RateLimitName("auth")) {
        post("/auth/register") { handleRegister(call, userRepository, jwtConfig) }
        post("/auth/login") { handleLogin(call, userRepository, jwtConfig) }
        post("/auth/forgot-password") {
            handleForgotPassword(call, userRepository, passwordResetRepository, emailSender)
        }
        post("/auth/reset-password") { handleResetPassword(call, userRepository, passwordResetRepository) }
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
            accessToken = jwtConfig.generateAccessToken(user.id, user.tokenVersion),
            refreshToken = jwtConfig.generateRefreshToken(user.id, user.tokenVersion),
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
            accessToken = jwtConfig.generateAccessToken(user.id, user.tokenVersion),
            refreshToken = jwtConfig.generateRefreshToken(user.id, user.tokenVersion),
        ),
    )
}

private suspend fun handleRefresh(
    call: ApplicationCall,
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    val request = call.receive<RefreshRequest>()
    val decoded = jwtConfig.verifyRefreshToken(request.refreshToken)
    val user = decoded?.userId?.let(userRepository::findById)

    if (user == null || decoded.tokenVersion != user.tokenVersion) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Refresh token invalido o caducado"))
        return
    }

    call.respond(
        AuthResponse(
            userId = user.id,
            name = user.name,
            email = user.email,
            accessToken = jwtConfig.generateAccessToken(user.id, user.tokenVersion),
            refreshToken = jwtConfig.generateRefreshToken(user.id, user.tokenVersion),
        ),
    )
}

/**
 * Siempre responde 204, exista o no la cuenta con ese email: no revela que emails estan
 * registrados (ver Global Constraints del plan).
 */
private suspend fun handleForgotPassword(
    call: ApplicationCall,
    userRepository: UserRepository,
    passwordResetRepository: PasswordResetRepository,
    emailSender: EmailSender,
) {
    val request = call.receive<ForgotPasswordRequest>()
    val user = userRepository.findByEmail(request.email)

    if (user != null) {
        val code = generateResetCode()
        passwordResetRepository.createOrReplace(
            userId = user.id,
            codeHash = sha256Hex(code),
            expiresAt = Instant.now().plusSeconds(RESET_CODE_EXPIRY_MINUTES * 60),
        )
        emailSender.send(
            to = user.email,
            subject = "Recupera tu contrasena en Agenda",
            body =
                "Tu codigo para restablecer la contrasena es: $code\n\n" +
                    "Caduca en 15 minutos. Si no lo has pedido tu, ignora este correo.",
        )
    }
    call.respond(HttpStatusCode.NoContent)
}

private suspend fun handleResetPassword(
    call: ApplicationCall,
    userRepository: UserRepository,
    passwordResetRepository: PasswordResetRepository,
) {
    val request = call.receive<ResetPasswordRequest>()
    if (call.respondIfInvalidResetPasswordRequest(request)) return

    val user = userRepository.findByEmail(request.email)
    val storedCode = user?.let { passwordResetRepository.find(it.id) }

    val esValido =
        user != null &&
            storedCode != null &&
            storedCode.attempts < RESET_CODE_MAX_ATTEMPTS &&
            Instant.now().isBefore(storedCode.expiresAt) &&
            storedCode.codeHash == sha256Hex(request.code)

    if (!esValido) {
        if (user != null && storedCode != null) passwordResetRepository.incrementAttempts(user.id)
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Codigo invalido o caducado"))
        return
    }

    userRepository.updatePassword(user.id, PasswordHasher.hash(request.newPassword))
    passwordResetRepository.delete(user.id)
    call.respond(HttpStatusCode.NoContent)
}

private fun generateResetCode(): String = "%06d".format(SecureRandom().nextInt(1_000_000))

private suspend fun ApplicationCall.respondIfInvalidRegisterRequest(request: RegisterRequest): Boolean {
    val emailValido = emailRegex.matches(request.email)
    val isInvalid = request.name.isBlank() || !emailValido || request.password.length < 6
    if (isInvalid) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Datos invalidos: email y contrasena minima 6"))
    }
    return isInvalid
}

private suspend fun ApplicationCall.respondIfInvalidResetPasswordRequest(request: ResetPasswordRequest): Boolean {
    val reason = request.validationError() ?: return false
    respond(HttpStatusCode.BadRequest, ErrorResponse(reason))
    return true
}
