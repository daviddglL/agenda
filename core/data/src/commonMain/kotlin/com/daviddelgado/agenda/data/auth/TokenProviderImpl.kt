package com.daviddelgado.agenda.data.auth

import com.daviddelgado.agenda.data.secure.SecureStorage
import com.daviddelgado.agenda.network.TokenProvider

private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"

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

    override fun clear() {
        secureStorage.remove(KEY_ACCESS_TOKEN)
        secureStorage.remove(KEY_REFRESH_TOKEN)
    }
}
