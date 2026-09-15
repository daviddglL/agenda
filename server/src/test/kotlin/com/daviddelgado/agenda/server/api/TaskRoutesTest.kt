package com.daviddelgado.agenda.server.api

import com.daviddelgado.agenda.server.dto.BulkDeleteRequest
import com.daviddelgado.agenda.server.dto.DeletedCountResponse
import com.daviddelgado.agenda.server.dto.LoginRequest
import com.daviddelgado.agenda.server.dto.TaskDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskRoutesTest {
    @Test
    fun sinTokenLasTareasResponden401() =
        withApi { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.get("/tasks").status)
        }

    @Test
    fun seCreaYSeRecuperaUnaTareaConTodosSusCampos() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken

            val creada = client.crearTarea(token, tareaDeEjemplo(id = "tarea-1"))
            val tareas = client.tareas(token)

            assertEquals("tarea-1", creada.id)
            assertEquals(listOf("tarea-1"), tareas.map { it.id })
            val guardada = tareas.single()
            assertEquals("Flexiones", guardada.title)
            assertEquals("2026-09-13", guardada.date)
            assertEquals("SALUD", guardada.category)
            assertEquals("ALTA", guardada.priority)
            assertEquals("DIARIO", guardada.reminderFrequency)
            // Incremento progresivo: +5 cada 2 semanas.
            assertEquals(5, guardada.incrementAmount)
            assertEquals(2, guardada.incrementEveryValue)
            assertEquals("SEMANAS", guardada.incrementEveryUnit)
        }

    @Test
    fun crearDosVecesLaMismaTareaNoLaDuplica() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken

            // Simula el reintento de la subida de una tarea creada sin red: mismo id.
            client.crearTarea(token, tareaDeEjemplo(id = "tarea-1", title = "Flexiones"))
            client.crearTarea(token, tareaDeEjemplo(id = "tarea-1", title = "Flexiones corregidas"))

            val tareas = client.tareas(token)
            assertEquals(1, tareas.size)
            assertEquals("Flexiones corregidas", tareas.single().title)
        }

    @Test
    fun elServidorGeneraElIdSiElClienteNoLoManda() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken

            val creada = client.crearTarea(token, tareaDeEjemplo(id = ""))

            assertTrue(creada.id.isNotBlank())
        }

    @Test
    fun sePuedeEditarUnaTareaExistente() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken
            client.crearTarea(token, tareaDeEjemplo(id = "tarea-1"))

            val response =
                client.put("/tasks/tarea-1") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(tareaDeEjemplo(id = "tarea-1", title = "Dominadas"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Dominadas", client.tareas(token).single().title)
        }

    @Test
    fun editarUnaTareaQueNoExisteDevuelve404() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken

            val response =
                client.put("/tasks/fantasma") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(tareaDeEjemplo(id = "fantasma"))
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun alternarCompletadaCambiaElEstado() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken
            client.crearTarea(token, tareaDeEjemplo(id = "tarea-1"))

            client.patch("/tasks/tarea-1/toggle-completed") { bearerAuth(token) }
            assertTrue(client.tareas(token).single().isCompleted)

            client.patch("/tasks/tarea-1/toggle-completed") { bearerAuth(token) }
            assertFalse(client.tareas(token).single().isCompleted)
        }

    @Test
    fun borradoConjuntoDeVariasTareasElegidas() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken
            listOf("t1", "t2", "t3").forEach { client.crearTarea(token, tareaDeEjemplo(id = it)) }

            val borradas =
                client.post("/tasks/bulk-delete") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(BulkDeleteRequest(listOf("t1", "t3")))
                }.body<DeletedCountResponse>()

            assertEquals(2, borradas.deleted)
            assertEquals(listOf("t2"), client.tareas(token).map { it.id })
        }

    @Test
    fun borradoConjuntoDeTodasLasTareas() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken
            listOf("t1", "t2").forEach { client.crearTarea(token, tareaDeEjemplo(id = it)) }

            val borradas = client.delete("/tasks") { bearerAuth(token) }.body<DeletedCountResponse>()

            assertEquals(2, borradas.deleted)
            assertTrue(client.tareas(token).isEmpty())
        }

    @Test
    fun cadaUsuarioSoloVeYBorraSusPropiasTareas() =
        withApi { client ->
            val primero = client.registrarUsuario(email = "uno@test.com").accessToken
            val segundo = client.registrarUsuario(email = "dos@test.com").accessToken
            client.crearTarea(primero, tareaDeEjemplo(id = "del-primero"))

            assertTrue(client.tareas(segundo).isEmpty())
            // El segundo usuario no puede borrar la tarea del primero.
            assertEquals(
                HttpStatusCode.NotFound,
                client.delete("/tasks/del-primero") { bearerAuth(segundo) }.status,
            )
            assertEquals(1, client.tareas(primero).size)
        }

    @Test
    fun crearUnaTareaConTituloVacioResponde400() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken

            val response =
                client.post("/tasks") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(tareaDeEjemplo(id = "tarea-1", title = "   "))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun crearUnaTareaConTituloDemasiadoLargoResponde400() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken

            val response =
                client.post("/tasks") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(tareaDeEjemplo(id = "tarea-1", title = "a".repeat(201)))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun crearUnaTareaConFechaInvalidaResponde400() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken

            val response =
                client.post("/tasks") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(tareaDeEjemplo(id = "tarea-1").copy(date = "no-es-una-fecha"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun borrarLaCuentaBorraSusTareasEnCascadaYElLoginFalla() =
        withApi { client ->
            val session = client.registrarUsuario(email = "adios@test.com", password = "secreta123")
            client.crearTarea(session.accessToken, tareaDeEjemplo(id = "tarea-1"))

            val borrado = client.delete("/users/me") { bearerAuth(session.accessToken) }
            assertEquals(HttpStatusCode.NoContent, borrado.status)

            // El token sigue firmado pero el usuario ya no existe: sin tareas y sin login.
            assertTrue(client.tareas(session.accessToken).isEmpty())
            val login =
                client.post("/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest("adios@test.com", "secreta123"))
                }
            assertEquals(HttpStatusCode.Unauthorized, login.status)
        }
}

private suspend fun HttpClient.crearTarea(
    token: String,
    task: TaskDto,
): TaskDto =
    post("/tasks") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(task)
    }.body()

private suspend fun HttpClient.tareas(token: String): List<TaskDto> = get("/tasks") { bearerAuth(token) }.body()
