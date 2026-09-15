package com.daviddelgado.agenda.server.reminder

import com.daviddelgado.agenda.server.db.DatabaseFactory
import com.daviddelgado.agenda.server.dto.TaskDto
import com.daviddelgado.agenda.server.push.PushSender
import com.daviddelgado.agenda.server.repository.FcmTokenRepository
import com.daviddelgado.agenda.server.repository.TaskRepository
import java.time.Instant
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderJobTest {
    private val taskRepository = TaskRepository()
    private val fcmTokenRepository = FcmTokenRepository()

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("jdbc:h2:mem:reminder-job-test-${Random.nextLong()};DB_CLOSE_DELAY=-1")
    }

    @Test
    fun mandaUnPushACadaTokenDeUnaTareaListaYMarcaElEnvio() {
        val userId = crearUsuarioConTarea(reminderFrequency = "UNA_VEZ", date = "2026-09-20", time = "09:00")
        fcmTokenRepository.upsert(userId, "token-1")
        fcmTokenRepository.upsert(userId, "token-2")
        val enviosRecibidos = mutableListOf<String>()
        val pushSender =
            PushSender { token, _, _ ->
                enviosRecibidos.add(token)
                true
            }
        val ahora = Instant.parse("2026-09-20T09:00:00Z")

        checkAndSendReminders(taskRepository, fcmTokenRepository, pushSender, now = ahora)

        assertEquals(setOf("token-1", "token-2"), enviosRecibidos.toSet())
        val actualizada = taskRepository.tasksPendingReminderCheck().single()
        assertEquals(ahora, actualizada.lastReminderSentAt)
    }

    @Test
    fun noMandaNadaSiAunNoEsLaHora() {
        val userId = crearUsuarioConTarea(reminderFrequency = "UNA_VEZ", date = "2026-09-20", time = "09:00")
        fcmTokenRepository.upsert(userId, "token-1")
        var seLlamo = false
        val pushSender =
            PushSender { _, _, _ ->
                seLlamo = true
                true
            }

        checkAndSendReminders(
            taskRepository,
            fcmTokenRepository,
            pushSender,
            now = Instant.parse("2026-09-20T08:00:00Z"),
        )

        assertTrue(!seLlamo)
        assertNull(taskRepository.tasksPendingReminderCheck().single().lastReminderSentAt)
    }

    @Test
    fun noMarcaComoEnviadoSiNoHayNingunTokenRegistrado() {
        crearUsuarioConTarea(reminderFrequency = "UNA_VEZ", date = "2026-09-20", time = "09:00")
        var seLlamo = false
        val pushSender =
            PushSender { _, _, _ ->
                seLlamo = true
                true
            }

        checkAndSendReminders(
            taskRepository,
            fcmTokenRepository,
            pushSender,
            now = Instant.parse("2026-09-20T09:00:00Z"),
        )

        assertTrue(!seLlamo)
        assertNull(taskRepository.tasksPendingReminderCheck().single().lastReminderSentAt)
    }

    @Test
    fun noMarcaComoEnviadoSiElEnvioFallaParaTodosLosTokens() {
        val userId = crearUsuarioConTarea(reminderFrequency = "UNA_VEZ", date = "2026-09-20", time = "09:00")
        fcmTokenRepository.upsert(userId, "token-1")
        val pushSender = PushSender { _, _, _ -> false }

        checkAndSendReminders(
            taskRepository,
            fcmTokenRepository,
            pushSender,
            now = Instant.parse("2026-09-20T09:00:00Z"),
        )

        assertNull(taskRepository.tasksPendingReminderCheck().single().lastReminderSentAt)
    }

    private fun crearUsuarioConTarea(
        reminderFrequency: String,
        date: String,
        time: String,
    ): String {
        val userRepository = com.daviddelgado.agenda.server.repository.UserRepository()
        val user = userRepository.create("David", "user-${Random.nextLong()}@test.com", "hash")
        taskRepository.create(
            user.id,
            TaskDto(
                id = "",
                title = "Tarea con recordatorio",
                date = date,
                time = time,
                category = "OTRO",
                priority = "MEDIA",
                reminderFrequency = reminderFrequency,
            ),
        )
        return user.id
    }
}
