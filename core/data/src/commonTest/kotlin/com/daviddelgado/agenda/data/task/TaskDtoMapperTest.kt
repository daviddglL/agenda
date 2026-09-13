package com.daviddelgado.agenda.data.task

import com.daviddelgado.agenda.domain.model.IncrementConfig
import com.daviddelgado.agenda.domain.model.IncrementUnit
import com.daviddelgado.agenda.domain.model.ReminderFrequency
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.model.TaskCategory
import com.daviddelgado.agenda.domain.model.TaskPriority
import com.daviddelgado.agenda.network.dto.TaskDto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskDtoMapperTest {
    private val tarea =
        Task(
            id = "t-1",
            title = "Flexiones",
            description = "Rutina diaria",
            date = LocalDate(2026, 9, 13),
            time = LocalTime(7, 30),
            durationMinutes = 15,
            category = TaskCategory.SALUD,
            priority = TaskPriority.ALTA,
            reminderFrequency = ReminderFrequency.DIARIO,
            increment = IncrementConfig(amount = 5, everyValue = 2, everyUnit = IncrementUnit.SEMANAS),
            isCompleted = true,
        )

    @Test
    fun laTareaSobreviveAlViajeDominioDtoDominio() {
        assertEquals(tarea, tarea.toDto().toDomain())
    }

    @Test
    fun lasFechasViajanEnIso8601() {
        val dto = tarea.toDto()

        assertEquals("2026-09-13", dto.date)
        assertEquals("07:30", dto.time)
    }

    @Test
    fun elServidorPuedeMandarLaHoraConSegundos() {
        // java.time.LocalTime del servidor serializa "07:30:00" cuando hay segundos.
        val dto = tarea.toDto().copy(time = "07:30:00")

        assertEquals(LocalTime(7, 30), dto.toDomain().time)
    }

    @Test
    fun unaTareaSinHoraNiDuracionNiIncrementoViajaConNulls() {
        val simple = tarea.copy(time = null, durationMinutes = null, increment = null)

        val dto = simple.toDto()

        assertNull(dto.time)
        assertNull(dto.durationMinutes)
        assertNull(dto.incrementAmount)
        assertEquals(simple, dto.toDomain())
    }

    @Test
    fun unEnumDesconocidoCaeEnElValorPorDefectoYNoRompeLaSincronizacion() {
        val desconocido =
            TaskDto(
                id = "t-2",
                title = "Tarea de una version mas nueva",
                date = "2026-09-13",
                category = "CATEGORIA_FUTURA",
                priority = "PRIORIDAD_FUTURA",
                reminderFrequency = "FRECUENCIA_FUTURA",
            )

        val domain = desconocido.toDomain()

        assertEquals(TaskCategory.OTRO, domain.category)
        assertEquals(TaskPriority.MEDIA, domain.priority)
        assertEquals(ReminderFrequency.NINGUNO, domain.reminderFrequency)
    }

    @Test
    fun unIncrementoIncompletoDelServidorSeIgnora() {
        // Sin las tres columnas no hay configuracion de incremento valida.
        val dto = tarea.toDto().copy(incrementEveryUnit = null)

        assertNull(dto.toDomain().increment)
    }
}
