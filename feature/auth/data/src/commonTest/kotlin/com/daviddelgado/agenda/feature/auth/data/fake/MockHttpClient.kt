package com.daviddelgado.agenda.feature.auth.data.fake

import com.daviddelgado.agenda.network.NetworkConfig
import com.daviddelgado.agenda.network.TokenProvider
import com.daviddelgado.agenda.network.installAgendaPlugins
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

const val TEST_BASE_URL = "http://localhost:8080/"

/**
 * HttpClient con el motor simulado de Ktor pero con **los plugins reales** de la app
 * ([installAgendaPlugins]): asi los tests ejercitan de verdad el JSON, el header Bearer,
 * el `expectSuccess` y el refresco automatico de token.
 */
fun mockHttpClient(
    tokenProvider: TokenProvider = FakeTokenProvider(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient =
    HttpClient(MockEngine(handler)) {
        installAgendaPlugins(NetworkConfig(baseUrl = TEST_BASE_URL), tokenProvider)
    }

/** Respuesta JSON del backend simulado. */
fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData =
    respond(
        content = body,
        status = status,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )
