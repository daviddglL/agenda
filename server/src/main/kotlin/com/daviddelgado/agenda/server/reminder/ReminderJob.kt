package com.daviddelgado.agenda.server.reminder

import com.daviddelgado.agenda.server.push.PushSender
import com.daviddelgado.agenda.server.repository.FcmTokenRepository
import com.daviddelgado.agenda.server.repository.TaskRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.coroutines.coroutineContext

private val logger: Logger = LoggerFactory.getLogger("ReminderJob")

/** Cada cuanto se revisa si hay recordatorios pendientes de enviar. */
const val REMINDER_CHECK_INTERVAL_MILLIS = 60_000L

/**
 * Bucle en segundo plano (arrancado solo desde `main()`, nunca en los tests que llaman a
 * `agendaModule` directamente) que revisa periodicamente que tareas necesitan un recordatorio
 * push ahora mismo (ver [ReminderScheduler]) y lo manda por [PushSender] a cada token FCM
 * registrado del usuario dueño de la tarea.
 */
suspend fun runReminderLoop(
    taskRepository: TaskRepository,
    fcmTokenRepository: FcmTokenRepository,
    pushSender: PushSender,
) {
    while (coroutineContext.isActive) {
        runCatching { checkAndSendReminders(taskRepository, fcmTokenRepository, pushSender) }
            .onFailure { logger.error("Fallo revisando recordatorios", it) }
        delay(REMINDER_CHECK_INTERVAL_MILLIS)
    }
}

/** Extraido de [runReminderLoop] para poder probarlo sin esperar al bucle real. */
fun checkAndSendReminders(
    taskRepository: TaskRepository,
    fcmTokenRepository: FcmTokenRepository,
    pushSender: PushSender,
    now: Instant = Instant.now(),
) {
    taskRepository.tasksPendingReminderCheck()
        .filter {
            ReminderScheduler.isDue(
                reminderFrequency = it.reminderFrequency,
                taskDate = it.date,
                taskTime = it.time,
                isCompleted = it.isCompleted,
                lastSentAt = it.lastReminderSentAt,
                now = now,
            )
        }
        .forEach { candidate ->
            // Si no se entrega ningun push de verdad (sin tokens registrados todavia, o sin
            // Firebase configurado y por tanto con NoOpPushSender, que siempre devuelve false),
            // NO se marca como enviado: para UNA_VEZ/PERSONALIZADO eso dejaria el recordatorio
            // marcado como entregado sin haberlo estado nunca, y como ReminderScheduler no vuelve
            // a avisar tras el primer envio, la tarea no avisaria jamas (ver hallazgo de revision).
            val entregado =
                fcmTokenRepository.tokensForUser(candidate.userId)
                    .map { token ->
                        pushSender.send(token = token, title = candidate.title, body = "Recordatorio de tarea")
                    }
                    .any { it }
            if (entregado) {
                taskRepository.markReminderSent(candidate.taskId, now)
            }
        }
}
