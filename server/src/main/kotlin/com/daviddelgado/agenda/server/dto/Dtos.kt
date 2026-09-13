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
