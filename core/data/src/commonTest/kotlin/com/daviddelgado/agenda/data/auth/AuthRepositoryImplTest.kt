package com.daviddelgado.agenda.data.auth

import com.daviddelgado.agenda.data.fake.FakePendingDeletionDao
import com.daviddelgado.agenda.data.fake.FakeTaskDao
import com.daviddelgado.agenda.data.fake.FakeTokenProvider
import com.daviddelgado.agenda.data.fake.mockHttpClient
import com.daviddelgado.agenda.data.fake.respondJson
import com.daviddelgado.agenda.database.TaskEntity
import com.daviddelgado.agenda.network.ApiException
import com.daviddelgado.agenda.network.api.AuthApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SESION_JSON = """
{
  "userId": "u-1",
  "name": "David",
  "email": "david@test.com",
  "accessToken": "access-nuevo",
  "refreshToken": "refresh-nuevo"
}
"""

private fun tareaLocal(id: String) =
    TaskEntity(
        id = id,
        title = "Tarea $id",
        description = "",
        dateEpochDay = 0,
        timeMinuteOfDay = null,
        durationMinutes = null,
        category = "OTRO",
        priority = "MEDIA",
        reminderFrequency = "NINGUNO",
        incrementAmount = null,
        incrementEveryValue = null,
        incrementEveryUnit = null,
        isCompleted = false,
    )

class AuthRepositoryImplTest {
    private val llamadas = mutableListOf<String>()

    private fun repositorio(
        tokenProvider: FakeTokenProvider = FakeTokenProvider(),
        dao: FakeTaskDao = FakeTaskDao(),
        pendingDeletionDao: FakePendingDeletionDao = FakePendingDeletionDao(),
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = SESION_JSON,
    ): AuthRepositoryImpl {
        val client =
            mockHttpClient(tokenProvider) { request ->
                llamadas += "${request.method.value} /${request.url.encodedPath.trimStart('/')}"
                respondJson(body, status)
            }
        return AuthRepositoryImpl(AuthApi(client), tokenProvider, dao, pendingDeletionDao)
    }

    @Test
    fun elLoginCorrectoGuardaLosTokensYPublicaElUsuario() =
        runTest {
            val tokens = FakeTokenProvider()
            val repository = repositorio(tokens)

            val resultado = repository.login("david@test.com", "secreta123")

            assertEquals("david@test.com", resultado.getOrNull()?.email)
            assertEquals("access-nuevo", tokens.savedAccessToken)
            assertEquals("refresh-nuevo", tokens.savedRefreshToken)
            assertEquals("u-1", repository.observeCurrentUser().first()?.id)
            assertEquals("POST /auth/login", llamadas.single())
        }

    @Test
    fun elRegistroCorrectoTambienAbreSesion() =
        runTest {
            val tokens = FakeTokenProvider()
            val repository = repositorio(tokens)

            val resultado = repository.register("David", "david@test.com", "secreta123")

            assertEquals("David", resultado.getOrNull()?.name)
            assertEquals("access-nuevo", tokens.savedAccessToken)
            assertEquals("POST /auth/register", llamadas.single())
        }

    @Test
    fun elLoginFallidoDevuelveElMensajeDelServidor() =
        runTest {
            val repository =
                repositorio(
                    status = HttpStatusCode.Unauthorized,
                    body = """{"message":"Email o contrasena incorrectos"}""",
                )

            val resultado = repository.login("david@test.com", "mala")

            val error = resultado.exceptionOrNull()
            assertIs<ApiException>(error)
            assertEquals(401, error.statusCode)
            assertEquals("Email o contrasena incorrectos", error.message)
            assertNull(repository.observeCurrentUser().first())
        }

    @Test
    fun cerrarSesionLimpiaTokensYDatosLocales() =
        runTest {
            val tokens = FakeTokenProvider("access", "refresh")
            val dao = FakeTaskDao(listOf(tareaLocal("t-1")))
            val pendingDeletionDao = FakePendingDeletionDao(listOf("borrada-offline"))
            val repository = repositorio(tokens, dao, pendingDeletionDao)

            repository.logout()

            assertTrue(tokens.cleared)
            assertTrue(dao.all.isEmpty())
            assertTrue(pendingDeletionDao.pendingIds.isEmpty())
            assertNull(repository.observeCurrentUser().first())
        }

    @Test
    fun borrarLaCuentaBorraServidorTokensYTareasLocales() =
        runTest {
            val tokens = FakeTokenProvider("access", "refresh")
            val dao = FakeTaskDao(listOf(tareaLocal("t-1"), tareaLocal("t-2")))
            val repository = repositorio(tokens, dao, status = HttpStatusCode.NoContent, body = "")

            val resultado = repository.deleteAccount()

            assertTrue(resultado.isSuccess)
            assertEquals("DELETE /users/me", llamadas.single())
            assertTrue(dao.all.isEmpty())
            assertTrue(tokens.cleared)
        }

    @Test
    fun borrarLaCuentaFallaSinBorrarLoLocalSiElServidorDaError() =
        runTest {
            val tokens = FakeTokenProvider("access", "refresh")
            val dao = FakeTaskDao(listOf(tareaLocal("t-1")))
            val repository = repositorio(tokens, dao, status = HttpStatusCode.InternalServerError, body = "{}")

            val resultado = repository.deleteAccount()

            assertTrue(resultado.isFailure)
            assertEquals(1, dao.all.size)
            assertTrue(!tokens.cleared)
        }

    @Test
    fun sinTokenGuardadoNoSeIntentaRecuperarLaSesion() =
        runTest {
            val repository = repositorio(FakeTokenProvider())

            assertNull(repository.restoreSession())
            assertTrue(llamadas.isEmpty())
        }

    @Test
    fun conTokenValidoLaSesionSeRecuperaAlArrancar() =
        runTest {
            val repository =
                repositorio(
                    FakeTokenProvider("access", "refresh"),
                    body = """{"id":"u-1","name":"David","email":"david@test.com"}""",
                )

            val usuario = repository.restoreSession()

            assertEquals("u-1", usuario?.id)
            assertEquals("GET /users/me", llamadas.single())
            assertEquals("David", repository.observeCurrentUser().first()?.name)
        }

    @Test
    fun conTokenCaducadoLaSesionNoSeRecupera() =
        runTest {
            val repository =
                repositorio(
                    FakeTokenProvider("caducado", "caducado"),
                    status = HttpStatusCode.Unauthorized,
                    body = """{"message":"Token invalido o caducado"}""",
                )

            assertNull(repository.restoreSession())
            assertNull(repository.observeCurrentUser().first())
        }
}
