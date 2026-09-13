package com.daviddelgado.agenda.network.api

import com.daviddelgado.agenda.network.apiCall
import com.daviddelgado.agenda.network.dto.AuthResponse
import com.daviddelgado.agenda.network.dto.LoginRequest
import com.daviddelgado.agenda.network.dto.RegisterRequest
import com.daviddelgado.agenda.network.dto.UserResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** Endpoints de `/auth` y `/users` del modulo :server. */
class AuthApi(private val client: HttpClient) {
    suspend fun login(
        email: String,
        password: String,
    ): AuthResponse =
        apiCall {
            client.post("auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }.body()
        }

    suspend fun register(
        name: String,
        email: String,
        password: String,
    ): AuthResponse =
        apiCall {
            client.post("auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(name, email, password))
            }.body()
        }

    suspend fun me(): UserResponse = apiCall { client.get("users/me").body() }

    /** Borra la cuenta; el servidor borra sus tareas en cascada. */
    suspend fun deleteAccount() {
        apiCall { client.delete("users/me") }
    }

    /**
     * El plugin Bearer de Ktor cachea el token que leyo la primera vez. Tras un login,
     * registro o logout hay que hacer que lo olvide; si no, la primera peticion protegida
     * viajaria con el token viejo (o sin token) y se gastaria un 401 + refresco inutil.
     */
    fun forgetCachedTokens() {
        client.pluginOrNull(Auth)
            ?.providers
            ?.filterIsInstance<BearerAuthProvider>()
            ?.forEach { it.clearToken() }
    }
}
