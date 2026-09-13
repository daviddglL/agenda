package com.daviddelgado.agenda.server.security

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

/**
 * El userId (subject del JWT) dentro de una ruta protegida por `authenticate("auth-jwt")`.
 * Lanza si se usa fuera de una ruta autenticada, lo cual seria un error de programacion,
 * no un caso de negocio a manejar con nulls.
 */
fun ApplicationCall.requireUserId(): String =
    principal<JWTPrincipal>()?.payload?.subject
        ?: error("requireUserId() llamado fuera de una ruta autenticada con auth-jwt")
