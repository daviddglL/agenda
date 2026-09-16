package com.daviddelgado.agenda.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

/** Token FCM de este dispositivo, para `POST /users/me/fcm-token` (ver ReminderJob en :server). */
@Serializable
data class FcmTokenRequest(val token: String)

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

/** Cuerpo de error comun del backend (ver `ErrorResponse` del modulo :server). */
@Serializable
data class ErrorResponse(val message: String)
