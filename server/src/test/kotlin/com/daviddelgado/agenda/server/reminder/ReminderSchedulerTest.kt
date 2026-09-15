package com.daviddelgado.agenda.server.reminder

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderSchedulerTest {
    private val fecha = LocalDate.of(2026, 9, 20)
    private val hora = LocalTime.of(9, 0)
    private val momentoDeLaTarea = fecha.atTime(hora).toInstant(ZoneOffset.UTC)

    @Test
    fun sinRecordatorioNuncaEstaListo() {
        assertFalse(
            ReminderScheduler.isDue(
                "NINGUNO",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = null,
                now = momentoDeLaTarea,
            ),
        )
    }

    @Test
    fun unaTareaCompletadaNuncaEstaLista() {
        assertFalse(
            ReminderScheduler.isDue(
                "DIARIO",
                fecha,
                hora,
                isCompleted = true,
                lastSentAt = null,
                now = momentoDeLaTarea,
            ),
        )
    }

    @Test
    fun antesDeLaHoraDeLaTareaNoEstaListo() {
        assertFalse(
            ReminderScheduler.isDue(
                "UNA_VEZ",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = null,
                now = momentoDeLaTarea.minusSeconds(60),
            ),
        )
    }

    @Test
    fun enSuHoraLaPrimeraVezEstaListo() {
        assertTrue(
            ReminderScheduler.isDue(
                "UNA_VEZ",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = null,
                now = momentoDeLaTarea,
            ),
        )
    }

    @Test
    fun unaVezNoSeRepiteTrasEnviarse() {
        assertFalse(
            ReminderScheduler.isDue(
                "UNA_VEZ",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = momentoDeLaTarea.plusSeconds(86_400),
            ),
        )
    }

    @Test
    fun personalizadoSeComportaComoUnaVezPorAhora() {
        assertFalse(
            ReminderScheduler.isDue(
                "PERSONALIZADO",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = momentoDeLaTarea.plusSeconds(86_400),
            ),
        )
    }

    @Test
    fun diarioSeRepiteExactamenteCadaDia() {
        val unDiaDespues = momentoDeLaTarea.plusSeconds(86_400)
        val docHorasDespues = momentoDeLaTarea.plusSeconds(43_200)

        assertTrue(
            ReminderScheduler.isDue(
                "DIARIO",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = unDiaDespues,
            ),
        )
        assertFalse(
            ReminderScheduler.isDue(
                "DIARIO",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = docHorasDespues,
            ),
        )
    }

    @Test
    fun semanalSeRepiteCadaSieteDias() {
        val seisDiasDespues = momentoDeLaTarea.plusSeconds(6 * 86_400L)
        val sieteDiasDespues = momentoDeLaTarea.plusSeconds(7 * 86_400L)

        assertFalse(
            ReminderScheduler.isDue(
                "SEMANAL",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = seisDiasDespues,
            ),
        )
        assertTrue(
            ReminderScheduler.isDue(
                "SEMANAL",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = sieteDiasDespues,
            ),
        )
    }

    @Test
    fun mensualSeRepiteUnMesDespues() {
        val unMesDespues = fecha.plusMonths(1).atTime(hora).toInstant(ZoneOffset.UTC)

        assertTrue(
            ReminderScheduler.isDue(
                "MENSUAL",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = unMesDespues,
            ),
        )
    }

    @Test
    fun sinHoraUsaMedianocheComoMomentoDeAviso() {
        val medianoche = fecha.atTime(LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC)

        assertTrue(
            ReminderScheduler.isDue(
                "UNA_VEZ",
                fecha,
                taskTime = null,
                isCompleted = false,
                lastSentAt = null,
                now = medianoche,
            ),
        )
    }
}
