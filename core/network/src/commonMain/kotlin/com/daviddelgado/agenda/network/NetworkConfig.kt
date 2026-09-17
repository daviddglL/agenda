package com.daviddelgado.agenda.network

/**
 * @param baseUrl URL base del backend, terminada en "/" (p.ej. "http://10.0.2.2:8080/" para
 * el emulador de Android, que ve el localhost del PC en esa IP).
 * @param certificatePinsSha256 hashes SHA-256 (formato "sha256/xxxx=") de las claves publicas
 * del backend, usados para SSL Certificate Pinning (punto 4 de seguridad de markdown.md).
 */
data class NetworkConfig(
    val baseUrl: String,
    val certificatePinsSha256: List<String> = emptyList(),
) {
    /** Solo el host, sin esquema ni puerto ni ruta: es lo que espera el pinner de OkHttp. */
    val host: String
        get() = baseUrl.substringAfter("://").substringBefore("/").substringBefore(":")
}

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
