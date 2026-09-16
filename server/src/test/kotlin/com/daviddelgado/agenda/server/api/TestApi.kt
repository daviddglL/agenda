package com.daviddelgado.agenda.server.api

import com.daviddelgado.agenda.server.agendaModule
import com.daviddelgado.agenda.server.dto.AuthResponse
import com.daviddelgado.agenda.server.dto.RegisterRequest
import com.daviddelgado.agenda.server.dto.TaskDto
import com.daviddelgado.agenda.server.email.EmailSender
import com.daviddelgado.agenda.server.email.NoOpEmailSender
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.random.Random

/**
 * Levanta la API real (rutas, JWT, Exposed) sobre una H2 **en memoria y distinta en cada
 * test**, para que los tests no compartan estado entre si ni toquen el fichero de
 * desarrollo `server/data/agenda.mv.db`. `emailSender` es sustituible para que los tests de
 * recuperacion de contrasena puedan capturar el codigo mandado sin credenciales SMTP reales.
 */
fun withApi(
    emailSender: EmailSender = NoOpEmailSender,
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) = testApplication {
    val databaseName = "agenda-test-${Random.nextLong()}"
    application { agendaModule("jdbc:h2:mem:$databaseName;DB_CLOSE_DELAY=-1", emailSender = emailSender) }
    val client =
        createClient {
            install(ContentNegotiation) { json() }
            install(WebSockets)
        }
    block(client)
}

/** Registra un usuario nuevo (email unico) y devuelve su sesion con los dos tokens. */
suspend fun HttpClient.registrarUsuario(
    name: String = "David",
    email: String = "usuario-${Random.nextLong()}@test.com",
    password: String = "secreta123",
): AuthResponse =
    post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(name, email, password))
    }.body()

/** Tarea de ejemplo con todos los campos del contrato, incluido el incremento progresivo. */
fun tareaDeEjemplo(
    id: String = "",
    title: String = "Flexiones",
    isCompleted: Boolean = false,
) = TaskDto(
    id = id,
    title = title,
    description = "Rutina diaria",
    date = "2026-09-13",
    time = "07:30",
    durationMinutes = 15,
    category = "SALUD",
    priority = "ALTA",
    reminderFrequency = "DIARIO",
    incrementAmount = 5,
    incrementEveryValue = 2,
    incrementEveryUnit = "SEMANAS",
    isCompleted = isCompleted,
)
