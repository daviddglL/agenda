package com.daviddelgado.agenda.data.auth

import com.daviddelgado.agenda.common.logging.AgendaLogger
import com.daviddelgado.agenda.database.PendingDeletionDao
import com.daviddelgado.agenda.database.TaskDao
import com.daviddelgado.agenda.domain.model.User
import com.daviddelgado.agenda.domain.repository.AuthRepository
import com.daviddelgado.agenda.network.TokenProvider
import com.daviddelgado.agenda.network.api.AuthApi
import com.daviddelgado.agenda.network.dto.AuthResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val LOG_TAG = "AuthRepository"

/**
 * Habla con `/auth` y `/users` del modulo :server a traves de [AuthApi] y guarda el par de
 * tokens en almacenamiento cifrado via [TokenProvider]. El usuario actual se expone como
 * [StateFlow] para que la navegacion reaccione al login/logout.
 */
class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenProvider: TokenProvider,
    private val taskDao: TaskDao,
    private val pendingDeletionDao: PendingDeletionDao,
) : AuthRepository {
    private val currentUser = MutableStateFlow<User?>(null)

    override fun observeCurrentUser(): StateFlow<User?> = currentUser

    override suspend fun login(
        email: String,
        password: String,
    ): Result<User> = runCatching { onAuthSuccess(authApi.login(email, password)) }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Result<User> = runCatching { onAuthSuccess(authApi.register(name, email, password)) }

    override suspend fun logout() {
        unregisterFcmTokenIfAny()
        tokenProvider.clear()
        authApi.forgetCachedTokens()
        currentUser.value = null
        // La base local es de la sesion que se cierra: no debe verla el siguiente usuario.
        clearLocalData()
    }

    override suspend fun deleteAccount(): Result<Unit> =
        runCatching {
            authApi.deleteAccount()
            // Borrado conjunto local: cascada servidor + Room en el mismo flujo.
            clearLocalData()
            tokenProvider.clear()
            authApi.forgetCachedTokens()
            currentUser.value = null
        }

    override suspend fun restoreSession(): User? {
        if (tokenProvider.accessToken() == null) return null
        return runCatching { authApi.me() }
            .map { User(it.id, it.name, it.email) }
            .onSuccess { currentUser.value = it }
            .getOrNull()
    }

    override suspend fun registerFcmToken(token: String): Result<Unit> =
        runCatching {
            authApi.registerFcmToken(token)
            // Guardado para poder desasociarlo del usuario actual al hacer logout (ver
            // unregisterFcmTokenIfAny): sin esto, el mismo dispositivo seguiria recibiendo
            // los recordatorios de este usuario despues de que otro inicie sesion en el.
            tokenProvider.saveFcmToken(token)
        }

    override suspend fun requestPasswordReset(email: String): Result<Unit> =
        runCatching { authApi.forgotPassword(email) }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> =
        runCatching {
            authApi.resetPassword(email, code, newPassword)
            // El reset ya invalido la sesion en el servidor (sube token_version): no tiene
            // sentido conservar tokens locales que van a dejar de servir.
            tokenProvider.clear()
            authApi.forgetCachedTokens()
            currentUser.value = null
        }

    /**
     * Mejor esfuerzo: si falla (sin red, servidor caido) el logout sigue igual, solo queda
     * en el log. El token seguiria asociado en el servidor hasta el proximo intento, pero
     * nunca bloquea que el usuario cierre sesion.
     */
    private suspend fun unregisterFcmTokenIfAny() {
        val token = tokenProvider.fcmToken() ?: return
        runCatching { authApi.unregisterFcmToken(token) }
            .onFailure { AgendaLogger.w(LOG_TAG, "No se pudo borrar el token FCM al cerrar sesion", it) }
    }

    private suspend fun clearLocalData() {
        taskDao.deleteAll()
        pendingDeletionDao.deleteAll()
    }

    private fun onAuthSuccess(response: AuthResponse): User {
        tokenProvider.saveTokens(response.accessToken, response.refreshToken)
        authApi.forgetCachedTokens()
        val user = User(response.userId, response.name, response.email)
        currentUser.value = user
        return user
    }
}
