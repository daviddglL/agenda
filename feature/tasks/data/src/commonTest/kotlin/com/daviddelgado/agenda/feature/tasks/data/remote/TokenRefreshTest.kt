package com.daviddelgado.agenda.feature.tasks.data.remote

import com.daviddelgado.agenda.feature.tasks.data.fake.FakeTokenProvider
import com.daviddelgado.agenda.feature.tasks.data.fake.mockHttpClient
import com.daviddelgado.agenda.feature.tasks.data.fake.respondJson
import com.daviddelgado.agenda.network.ApiException
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TOKENS_NUEVOS_JSON = """
{
  "userId": "u-1",
  "name": "David",
  "email": "david@test.com",
  "accessToken": "access-nuevo",
  "refreshToken": "refresh-nuevo"
}
"""

/**
 * Comprueba el refresco automatico de token del cliente Ktor (punto 3 de markdown.md)
 * contra el endpoint `POST /auth/refresh` del servidor.
 */
class TokenRefreshTest {
    @Test
    fun unaRespuesta401RefrescaElTokenYReintentaLaPeticion() =
        runTest {
            val tokens = FakeTokenProvider(access = "access-caducado", refresh = "refresh-valido")
            val llamadas = mutableListOf<Pair<String, String?>>()
            var primerIntento = true

            val client =
                mockHttpClient(tokens) { request ->
                    llamadas += request.url.encodedPath to request.headers[HttpHeaders.Authorization]
                    when {
                        request.url.encodedPath.endsWith("/auth/refresh") -> respondJson(TOKENS_NUEVOS_JSON)
                        primerIntento -> {
                            primerIntento = false
                            respondError(HttpStatusCode.Unauthorized)
                        }
                        else -> respondJson("[]")
                    }
                }

            val tareas = TaskApi(client).getAll()

            assertTrue(tareas.isEmpty())
            // Se ha llamado al refresco y los tokens nuevos quedan guardados...
            assertTrue(llamadas.any { it.first.endsWith("/auth/refresh") })
            assertEquals("access-nuevo", tokens.savedAccessToken)
            assertEquals("refresh-nuevo", tokens.savedRefreshToken)
            // ...y el reintento ya viaja con el token nuevo.
            assertEquals("Bearer access-nuevo", llamadas.last().second)
        }

    @Test
    fun siElRefrescoTambienFallaSeCierraLaSesion() =
        runTest {
            val tokens = FakeTokenProvider(access = "access-caducado", refresh = "refresh-caducado")
            val client =
                mockHttpClient(tokens) { _ ->
                    respondJson("""{"message":"Token invalido o caducado"}""", HttpStatusCode.Unauthorized)
                }

            val error = assertFailsWith<ApiException> { TaskApi(client).getAll() }

            assertEquals(401, error.statusCode)
            assertTrue(tokens.cleared)
            assertNull(tokens.savedAccessToken)
        }

    @Test
    fun sinTokenGuardadoLaPeticionViajaSinHeaderDeAutorizacion() =
        runTest {
            val tokens = FakeTokenProvider()
            var header: String? = "sin-leer"
            val client =
                mockHttpClient(tokens) { request ->
                    header = request.headers[HttpHeaders.Authorization]
                    respondJson("[]")
                }

            TaskApi(client).getAll()

            assertNull(header)
        }
}
