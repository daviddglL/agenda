package com.daviddelgado.agenda.feature.tasks.data.mapper

import com.daviddelgado.agenda.feature.tasks.domain.model.IncrementConfig
import com.daviddelgado.agenda.feature.tasks.domain.model.IncrementUnit
import com.daviddelgado.agenda.feature.tasks.domain.model.ReminderFrequency
import com.daviddelgado.agenda.feature.tasks.domain.model.Task
import com.daviddelgado.agenda.feature.tasks.domain.model.TaskCategory
import com.daviddelgado.agenda.feature.tasks.domain.model.TaskPriority
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskMapperTest {
    private val completa =
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
    fun laTareaSobreviveAlViajeDominioEntidadDominio() {
        assertEquals(completa, completa.toEntity().toDomain())
    }

    @Test
    fun laHoraSeGuardaComoMinutosDesdeMedianoche() {
        val entity = completa.toEntity()

        assertEquals(7 * 60 + 30, entity.timeMinuteOfDay)
        assertEquals(LocalTime(7, 30), entity.toDomain().time)
    }

    @Test
    fun losEnumsSeGuardanComoTextoLegible() {
        val entity = completa.toEntity()

        assertEquals("SALUD", entity.category)
        assertEquals("ALTA", entity.priority)
        assertEquals("DIARIO", entity.reminderFrequency)
        assertEquals("SEMANAS", entity.incrementEveryUnit)
    }

    @Test
    fun unaTareaNoIncrementalGuardaLasTresColumnasDeIncrementoANull() {
        val entity = completa.copy(increment = null).toEntity()

        assertNull(entity.incrementAmount)
        assertNull(entity.incrementEveryValue)
        assertNull(entity.incrementEveryUnit)
        assertNull(entity.toDomain().increment)
        assertFalse(entity.toDomain().isIncremental)
    }

    @Test
    fun porDefectoLaTareaNoQuedaPendienteDeSincronizar() {
        assertFalse(completa.toEntity().pendingSync)
        assertTrue(completa.toEntity(pendingSync = true).pendingSync)
    }

    @Test
    fun laFechaViajaComoDiasDesdeLaEpoca() {
        val entity = completa.toEntity()

        assertEquals(LocalDate(2026, 9, 13).toEpochDays().toLong(), entity.dateEpochDay)
        assertEquals(LocalDate(2026, 9, 13), entity.toDomain().date)
    }
}
