package com.daviddelgado.agenda.server.api

import com.daviddelgado.agenda.server.dto.AuthResponse
import com.daviddelgado.agenda.server.dto.LoginRequest
import com.daviddelgado.agenda.server.dto.RefreshRequest
import com.daviddelgado.agenda.server.dto.RegisterRequest
import com.daviddelgado.agenda.server.dto.UserResponse
import com.daviddelgado.agenda.server.repository.UserRepository
import com.daviddelgado.agenda.server.security.PasswordHasher
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthRoutesTest {
    @Test
    fun elRegistroDevuelve201ConLosDosTokens() =
        withApi { client ->
            val response =
                client.post("/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(RegisterRequest("David", "nuevo@test.com", "secreta123"))
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val session = response.body<AuthResponse>()
            assertEquals("nuevo@test.com", session.email)
            assertTrue(session.accessToken.isNotBlank())
            assertTrue(session.refreshToken.isNotBlank())
        }

    @Test
    fun noSePuedeRegistrarDosVecesElMismoEmail() =
        withApi { client ->
            client.registrarUsuario(email = "repetido@test.com")

            val response =
                client.post("/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(RegisterRequest("Otro", "repetido@test.com", "secreta123"))
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
        }

    @Test
    fun unaContrasenaDeMenosDeSeisCaracteresSeRechaza() =
        withApi { client ->
            val response =
                client.post("/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(RegisterRequest("David", "corta@test.com", "12345"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun elLoginConContrasenaIncorrectaDevuelve401() =
        withApi { client ->
            client.registrarUsuario(email = "login@test.com", password = "secreta123")

            val response =
                client.post("/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest("login@test.com", "equivocada"))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun elAccessTokenDaAccesoAlPerfilPropio() =
        withApi { client ->
            val session = client.registrarUsuario(name = "David", email = "perfil@test.com")

            val me = client.get("/users/me") { bearerAuth(session.accessToken) }.body<UserResponse>()

            assertEquals(session.userId, me.id)
            assertEquals("David", me.name)
        }

    @Test
    fun sinTokenElPerfilResponde401() =
        withApi { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me").status)
        }

    @Test
    fun elRefreshTokenCanjeaUnParDeTokensNuevo() =
        withApi { client ->
            val session = client.registrarUsuario()

            val renovada =
                client.post("/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(session.refreshToken))
                }.body<AuthResponse>()

            assertEquals(session.userId, renovada.userId)
            assertTrue(renovada.accessToken.isNotBlank())
            // El access token nuevo tambien sirve para las rutas protegidas.
            assertEquals(HttpStatusCode.OK, client.get("/users/me") { bearerAuth(renovada.accessToken) }.status)
        }

    @Test
    fun unAccessTokenNoSirveComoRefreshToken() =
        withApi { client ->
            val session = client.registrarUsuario()

            val response =
                client.post("/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(session.accessToken))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun dosUsuariosDistintosTienenIdentidadesDistintas() =
        withApi { client ->
            val primero = client.registrarUsuario(email = "uno@test.com")
            val segundo = client.registrarUsuario(email = "dos@test.com")

            assertNotEquals(primero.userId, segundo.userId)
        }

    @Test
    fun registrarseConEmailSinArrobaResponde400() =
        withApi { client ->
            val response =
                client.post("/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(RegisterRequest(name = "David", email = "no-es-un-email", password = "secreta123"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun trasVariosLoginsFallidosSeguidosElServidorResponde429() =
        withApi { client ->
            client.registrarUsuario(email = "limite@test.com", password = "secreta123")

            val intentoQueDeberiaBloquear =
                (1..15).map {
                    client.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(LoginRequest(email = "limite@test.com", password = "incorrecta"))
                    }
                }.last()

            assertEquals(HttpStatusCode.TooManyRequests, intentoQueDeberiaBloquear.status)
        }

    @Test
    fun trasCambiarLaContrasenaElRefreshTokenAntiguoDejaDeServir() =
        withApi { client ->
            val session = client.registrarUsuario(email = "version@test.com")

            UserRepository().updatePassword(session.userId, PasswordHasher.hash("nueva123"))

            val response =
                client.post("/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(session.refreshToken))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
