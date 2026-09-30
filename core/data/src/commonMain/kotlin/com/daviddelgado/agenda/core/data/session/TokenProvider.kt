package com.daviddelgado.agenda.core.data.session

/**
 * Provee, persiste y limpia los tokens de autenticacion que usa el interceptor Bearer de
 * Ktor. La implementacion real (:core:data) los guarda en almacenamiento cifrado
 * (EncryptedSharedPreferences / Keychain).
 */
interface TokenProvider {
    suspend fun accessToken(): String?

    suspend fun refreshToken(): String?

    /** Guarda el par de tokens recien emitido por el servidor (login, registro o refresco). */
    fun saveTokens(
        accessToken: String,
        refreshToken: String,
    )

    /** Ultimo token FCM que este dispositivo registro en el servidor para este usuario. */
    suspend fun fcmToken(): String?

    fun saveFcmToken(token: String)

    /** Borra tokens de sesion y el token FCM guardado (logout/borrado de cuenta). */
    fun clear()
}
