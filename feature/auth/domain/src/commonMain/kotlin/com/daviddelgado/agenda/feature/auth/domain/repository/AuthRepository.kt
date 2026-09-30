package com.daviddelgado.agenda.feature.auth.domain.repository

import com.daviddelgado.agenda.feature.auth.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeCurrentUser(): Flow<User?>

    suspend fun login(
        email: String,
        password: String,
    ): Result<User>

    suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Result<User>

    suspend fun logout()

    /**
     * Recupera la sesion guardada al arrancar la app (token en almacenamiento cifrado).
     * Devuelve null si no habia token o si el servidor ya no lo acepta.
     */
    suspend fun restoreSession(): User?

    /** Borra la cuenta y, en cascada (servidor + local), todas sus tareas. */
    suspend fun deleteAccount(): Result<Unit>

    /** Manda al servidor el token FCM de este dispositivo para poder recibir recordatorios push. */
    suspend fun registerFcmToken(token: String): Result<Unit>

    /** Pide al servidor un codigo de recuperacion de contrasena para este email (si existe la cuenta). */
    suspend fun requestPasswordReset(email: String): Result<Unit>

    /** Cambia la contrasena usando el codigo recibido por email. */
    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit>
}
