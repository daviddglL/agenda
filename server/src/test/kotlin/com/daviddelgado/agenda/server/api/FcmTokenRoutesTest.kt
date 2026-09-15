package com.daviddelgado.agenda.server.api

import com.daviddelgado.agenda.server.dto.FcmTokenRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals

class FcmTokenRoutesTest {
    @Test
    fun sinTokenDeSesionResponde401() =
        withApi { client ->
            val response =
                client.post("/users/me/fcm-token") {
                    contentType(ContentType.Application.Json)
                    setBody(FcmTokenRequest("token-de-prueba"))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun registrarUnTokenFcmResponde204() =
        withApi { client ->
            val accessToken = client.registrarUsuario().accessToken

            val response =
                client.post("/users/me/fcm-token") {
                    bearerAuth(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(FcmTokenRequest("token-de-prueba"))
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
        }

    @Test
    fun registrarElMismoTokenDosVecesNoFalla() =
        withApi { client ->
            val accessToken = client.registrarUsuario().accessToken

            client.post("/users/me/fcm-token") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(FcmTokenRequest("token-repetido"))
            }
            val segunda =
                client.post("/users/me/fcm-token") {
                    bearerAuth(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(FcmTokenRequest("token-repetido"))
                }

            assertEquals(HttpStatusCode.NoContent, segunda.status)
        }
}
