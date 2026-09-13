package com.daviddelgado.agenda.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskTest {
    private val fecha = LocalDate(2026, 9, 13)

    @Test
    fun unaTareaSinConfiguracionDeIncrementoNoEsIncremental() {
        val tarea = Task(id = "1", title = "Comprar pan", date = fecha)

        assertFalse(tarea.isIncremental)
    }

    @Test
    fun unaTareaConConfiguracionDeIncrementoEsIncremental() {
        val tarea =
            Task(
                id = "1",
                title = "Flexiones",
                date = fecha,
                increment = IncrementConfig(amount = 5, everyValue = 2, everyUnit = IncrementUnit.SEMANAS),
            )

        assertTrue(tarea.isIncremental)
    }

    @Test
    fun losValoresPorDefectoSonLosDeLaSpec() {
        val tarea = Task(id = "1", title = "Comprar pan", date = fecha)

        assertEquals("", tarea.description)
        assertEquals(TaskCategory.OTRO, tarea.category)
        assertEquals(TaskPriority.MEDIA, tarea.priority)
        assertEquals(ReminderFrequency.NINGUNO, tarea.reminderFrequency)
        assertFalse(tarea.isCompleted)
        assertNull(tarea.time)
    }

    @Test
    fun laHoraYLaDuracionSonOpcionalesPeroSeConservan() {
        val tarea =
            Task(
                id = "1",
                title = "Gimnasio",
                date = fecha,
                time = LocalTime(7, 30),
                durationMinutes = 45,
            )

        assertEquals(LocalTime(7, 30), tarea.time)
        assertEquals(45, tarea.durationMinutes)
    }
}
