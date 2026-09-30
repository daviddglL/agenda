package com.daviddelgado.agenda.core.data.networking

import com.daviddelgado.agenda.core.data.networking.dto.AuthResponse
import com.daviddelgado.agenda.core.data.networking.dto.RefreshRequest
import com.daviddelgado.agenda.core.data.session.TokenProvider
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Ruta del endpoint de refresco del modulo :server (`POST /auth/refresh`). */
internal const val REFRESH_TOKEN_PATH = "auth/refresh"

/**
 * Plugins comunes a Android e iOS: JSON, logging, refresco automatico de token y WebSockets.
 * El engine (OkHttp/Darwin) y el pinning SSL se configuran por plataforma, ver [createHttpClient].
 */
fun HttpClientConfig<*>.installAgendaPlugins(
    config: NetworkConfig,
    tokenProvider: TokenProvider,
) {
    // url() (y no host()) para poder incluir esquema y puerto: "http://10.0.2.2:8080/".
    defaultRequest { url(config.baseUrl) }

    // Un 4xx/5xx debe ser un error explicito (ResponseException -> ApiException), no un
    // cuerpo que luego falle al deserializarse en un sitio cualquiera.
    expectSuccess = true

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            },
        )
    }

    install(Auth) {
        bearer {
            loadTokens {
                tokenProvider.accessToken()?.let { access ->
                    BearerTokens(access, tokenProvider.refreshToken() ?: "")
                }
            }
            // Ante un 401, Ktor llama aqui una sola vez: canjeamos el refresh token por un
            // par nuevo en el servidor y lo persistimos. Si el refresco falla (caducado o
            // revocado) se limpian los tokens y la peticion queda como no autenticada.
            refreshTokens {
                val refresh = tokenProvider.refreshToken()
                if (refresh.isNullOrBlank()) {
                    null
                } else {
                    runCatching {
                        client.post(REFRESH_TOKEN_PATH) {
                            markAsRefreshTokenRequest()
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequest(refresh))
                        }.body<AuthResponse>()
                    }.fold(
                        onSuccess = { response ->
                            tokenProvider.saveTokens(response.accessToken, response.refreshToken)
                            BearerTokens(response.accessToken, response.refreshToken)
                        },
                        onFailure = {
                            tokenProvider.clear()
                            null
                        },
                    )
                }
            }
        }
    }

    install(Logging) { level = LogLevel.INFO }
    install(WebSockets)
}

/** Cada plataforma construye su HttpClient con el engine nativo y aplica el certificate pinning. */
expect fun createHttpClient(
    config: NetworkConfig,
    tokenProvider: TokenProvider,
): io.ktor.client.HttpClient
