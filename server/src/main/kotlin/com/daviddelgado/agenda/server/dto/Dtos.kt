package com.daviddelgado.agenda.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class AuthResponse(
    val userId: String,
    val name: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class UserResponse(val id: String, val name: String, val email: String)

/**
 * Todos los campos pedidos para una tarea. `date`/`time` van en formato ISO-8601
 * ("yyyy-MM-dd" / "HH:mm") para no atar el contrato de red a ningun tipo de fecha
 * especifico del cliente. `category`/`priority`/`reminderFrequency` son los `.name`
 * de los enums cerrados que el cliente ofrece para elegir (ver core:domain).
 */
@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val description: String = "",
    val date: String,
    val time: String? = null,
    val durationMinutes: Int? = null,
    val category: String,
    val priority: String,
    val reminderFrequency: String,
    val incrementAmount: Int? = null,
    val incrementEveryValue: Int? = null,
    val incrementEveryUnit: String? = null,
    val isCompleted: Boolean = false,
)

@Serializable
data class BulkDeleteRequest(val ids: List<String>)

/** Respuesta de los borrados conjuntos: cuantas tareas se han borrado de verdad. */
@Serializable
data class DeletedCountResponse(val deleted: Int)

@Serializable
data class ErrorResponse(val message: String)

private const val MAX_TITLE_LENGTH = 200
private const val MAX_DESCRIPTION_LENGTH = 2000

/** Devuelve el motivo por el que la tarea no es valida, o null si lo es. */
fun TaskDto.validationError(): String? {
    if (title.isBlank()) return "El titulo no puede estar vacio"
    if (title.length > MAX_TITLE_LENGTH) return "El titulo no puede superar $MAX_TITLE_LENGTH caracteres"
    if (description.length > MAX_DESCRIPTION_LENGTH) {
        return "La descripcion no puede superar $MAX_DESCRIPTION_LENGTH caracteres"
    }
    if (runCatching { java.time.LocalDate.parse(date) }.isFailure) return "Fecha invalida"
    if (time != null && runCatching { java.time.LocalTime.parse(time) }.isFailure) return "Hora invalida"
    return null
}
