package com.daviddelgado.agenda.feature.auth.data.remote

import com.daviddelgado.agenda.core.data.networking.apiCall
import com.daviddelgado.agenda.core.data.networking.dto.AuthResponse
import com.daviddelgado.agenda.core.data.networking.dto.UserResponse
import com.daviddelgado.agenda.feature.auth.data.dto.FcmTokenRequest
import com.daviddelgado.agenda.feature.auth.data.dto.ForgotPasswordRequest
import com.daviddelgado.agenda.feature.auth.data.dto.LoginRequest
import com.daviddelgado.agenda.feature.auth.data.dto.RegisterRequest
import com.daviddelgado.agenda.feature.auth.data.dto.ResetPasswordRequest
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

    suspend fun registerFcmToken(token: String) {
        apiCall {
            client.post("users/me/fcm-token") {
                contentType(ContentType.Application.Json)
                setBody(FcmTokenRequest(token))
            }
        }
    }

    /** Desasocia este token FCM del usuario actual (logout, ver AuthRepositoryImpl). */
    suspend fun unregisterFcmToken(token: String) {
        apiCall {
            client.delete("users/me/fcm-token") {
                contentType(ContentType.Application.Json)
                setBody(FcmTokenRequest(token))
            }
        }
    }

    suspend fun forgotPassword(email: String) {
        apiCall {
            client.post("auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest(email))
            }
        }
    }

    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ) {
        apiCall {
            client.post("auth/reset-password") {
                contentType(ContentType.Application.Json)
                setBody(ResetPasswordRequest(email, code, newPassword))
            }
        }
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
