package com.daviddelgado.agenda.server.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Decide si una tarea necesita un recordatorio push ahora mismo. Logica pura (sin red ni
 * base de datos) para poder probarla con TDD normal; [runReminderLoop] (Task 9) es quien la
 * usa de verdad contra la base de datos y Firebase.
 *
 * El servidor no depende de `:core:domain` (modulo solo cliente), asi que aqui se repite el
 * catalogo cerrado de `ReminderFrequency` como `String` ("NINGUNO", "UNA_VEZ", "DIARIO",
 * "SEMANAL", "MENSUAL", "PERSONALIZADO"), tal cual se guarda en `Tasks.reminderFrequency`. Un
 * valor desconocido no dispara nada.
 */
object ReminderScheduler {
    fun isDue(
        reminderFrequency: String,
        taskDate: LocalDate,
        taskTime: LocalTime?,
        isCompleted: Boolean,
        lastSentAt: Instant?,
        now: Instant,
    ): Boolean {
        if (isCompleted || reminderFrequency == "NINGUNO") return false
        val proximoAviso = nextDueInstant(reminderFrequency, taskDate, taskTime, lastSentAt) ?: return false
        return !now.isBefore(proximoAviso)
    }

    private fun nextDueInstant(
        reminderFrequency: String,
        taskDate: LocalDate,
        taskTime: LocalTime?,
        lastSentAt: Instant?,
    ): Instant? {
        val horaAviso = taskTime ?: LocalTime.MIDNIGHT
        if (lastSentAt == null) return LocalDateTime.of(taskDate, horaAviso).toInstant(ZoneOffset.UTC)

        val ultimaFecha = lastSentAt.atZone(ZoneOffset.UTC).toLocalDate()
        val siguienteFecha =
            when (reminderFrequency) {
                "DIARIO" -> ultimaFecha.plusDays(1)
                "SEMANAL" -> ultimaFecha.plusWeeks(1)
                "MENSUAL" -> ultimaFecha.plusMonths(1)
                // UNA_VEZ / PERSONALIZADO: un unico aviso; sin un campo propio de intervalo
                // (ver ESTADO_PROYECTO.md), de momento no se repiten tras el primer envio.
                else -> return null
            }
        return LocalDateTime.of(siguienteFecha, horaAviso).toInstant(ZoneOffset.UTC)
    }
}
