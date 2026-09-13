package com.daviddelgado.agenda.network

import com.daviddelgado.agenda.network.dto.ErrorResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException

/**
 * Error devuelto por el backend con un mensaje ya legible para el usuario (el campo
 * `message` de `ErrorResponse` del modulo :server). Se lanza en lugar de la excepcion
 * cruda de Ktor para que la UI pueda mostrar "Email o contrasena incorrectos" en vez de
 * "Client request invalid: 401 Unauthorized".
 */
class ApiException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)

/**
 * Envuelve una llamada HTTP: traduce las respuestas de error (4xx/5xx, que con
 * `expectSuccess = true` llegan como [ResponseException]) a [ApiException].
 */
suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (exception: ResponseException) {
        val status = exception.response.status
        val serverMessage = runCatching { exception.response.body<ErrorResponse>().message }.getOrNull()
        throw ApiException(status.value, serverMessage ?: "Error del servidor (${status.value})")
    }
