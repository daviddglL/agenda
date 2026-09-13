package com.daviddelgado.agenda.data.fake

import com.daviddelgado.agenda.network.TokenProvider

/** TokenProvider en memoria: sustituye al almacenamiento cifrado del dispositivo. */
class FakeTokenProvider(
    private var access: String? = null,
    private var refresh: String? = null,
) : TokenProvider {
    var cleared = false
        private set

    val savedAccessToken: String? get() = access
    val savedRefreshToken: String? get() = refresh

    override suspend fun accessToken(): String? = access

    override suspend fun refreshToken(): String? = refresh

    override fun saveTokens(
        accessToken: String,
        refreshToken: String,
    ) {
        access = accessToken
        refresh = refreshToken
    }

    override fun clear() {
        access = null
        refresh = null
        cleared = true
    }
}
