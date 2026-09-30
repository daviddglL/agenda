package com.daviddelgado.agenda.core.data.session

private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_FCM_TOKEN = "fcm_token"

/** Guarda los tokens en almacenamiento cifrado (EncryptedSharedPreferences / Keychain). */
class TokenProviderImpl(private val secureStorage: SecureStorage) : TokenProvider {
    override suspend fun accessToken(): String? = secureStorage.getString(KEY_ACCESS_TOKEN)

    override suspend fun refreshToken(): String? = secureStorage.getString(KEY_REFRESH_TOKEN)

    override fun saveTokens(
        accessToken: String,
        refreshToken: String,
    ) {
        secureStorage.putString(KEY_ACCESS_TOKEN, accessToken)
        secureStorage.putString(KEY_REFRESH_TOKEN, refreshToken)
    }

    override suspend fun fcmToken(): String? = secureStorage.getString(KEY_FCM_TOKEN)

    override fun saveFcmToken(token: String) {
        secureStorage.putString(KEY_FCM_TOKEN, token)
    }

    override fun clear() {
        secureStorage.remove(KEY_ACCESS_TOKEN)
        secureStorage.remove(KEY_REFRESH_TOKEN)
        secureStorage.remove(KEY_FCM_TOKEN)
    }
}
