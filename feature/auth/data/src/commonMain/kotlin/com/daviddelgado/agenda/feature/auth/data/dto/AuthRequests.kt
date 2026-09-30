package com.daviddelgado.agenda.feature.auth.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

/** Token FCM de este dispositivo, para `POST /users/me/fcm-token` (ver ReminderJob en :server). */
@Serializable
data class FcmTokenRequest(val token: String)

/** Cuerpo de `POST /auth/forgot-password` (ver `ForgotPasswordRequest` del modulo :server). */
@Serializable
data class ForgotPasswordRequest(val email: String)

/** Cuerpo de `POST /auth/reset-password` (ver `ResetPasswordRequest` del modulo :server). */
@Serializable
data class ResetPasswordRequest(val email: String, val code: String, val newPassword: String)
