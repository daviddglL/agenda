package com.daviddelgado.agenda.server

import com.daviddelgado.agenda.server.api.withApi
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CorsTest {
    @Test
    fun unOrigenNoConfiguradoEnAgendaCorsAllowedOriginsNoRecibeLaCabeceraDeCors() =
        withApi { client ->
            val response =
                client.get("/auth/login") {
                    header(HttpHeaders.Origin, "https://evil.example")
                }

            assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun unOrigenListadoEnAllowedOriginsSiRecibeLaCabeceraDeCors() =
        withApi(allowedOrigins = setOf("trusted.example")) { client ->
            val response =
                client.get("/auth/login") {
                    header(HttpHeaders.Origin, "https://trusted.example")
                }

            assertEquals("https://trusted.example", response.headers[HttpHeaders.AccessControlAllowOrigin])
        }
}
