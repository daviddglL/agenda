package com.daviddelgado.agenda.feature.tasks.domain.usecase

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val HOY = LocalDate(2026, 9, 13)

class GenerateTaskRepetitionsUseCaseTest {
    private val useCase = GenerateTaskRepetitionsUseCase()

    private fun idsSecuenciales(): () -> String {
        var siguiente = 0
        return { "copia-${siguiente++}" }
    }

    @Test
    fun sinConfiguracionDeIncrementoNoGeneraNada() {
        val tarea = Task(id = "orig", title = "Regar planta", date = HOY, increment = null)

        val copias = useCase(tarea, idsSecuenciales())

        assertTrue(copias.isEmpty())
    }

    @Test
    fun generaTantasCopiasComoIndicaAmount() {
        val tarea =
            Task(
                id = "orig",
                title = "Regar planta",
                date = HOY,
                increment = IncrementConfig(amount = 3, everyValue = 2, everyUnit = IncrementUnit.SEMANAS),
            )

        val copias = useCase(tarea, idsSecuenciales())

        assertEquals(3, copias.size)
    }

    @Test
    fun cadaCopiaSeFechaAIntervalosCrecientesDeLaCadenciaEnDias() {
        val tarea =
            Task(
                id = "orig",
                title = "Regar planta",
                date = HOY,
                increment = IncrementConfig(amount = 3, everyValue = 5, everyUnit = IncrementUnit.DIAS),
            )

        val copias = useCase(tarea, idsSecuenciales())

        assertEquals(
            listOf(HOY.plusDias(5), HOY.plusDias(10), HOY.plusDias(15)),
            copias.map { it.date },
        )
    }

    @Test
    fun cadaCopiaSeFechaAIntervalosCrecientesDeLaCadenciaEnSemanas() {
        val tarea =
            Task(
                id = "orig",
                title = "Regar planta",
                date = HOY,
                increment = IncrementConfig(amount = 2, everyValue = 2, everyUnit = IncrementUnit.SEMANAS),
            )

        val copias = useCase(tarea, idsSecuenciales())

        assertEquals(listOf(HOY.plusDias(14), HOY.plusDias(28)), copias.map { it.date })
    }

    @Test
    fun cadaCopiaSeFechaAIntervalosCrecientesDeLaCadenciaEnMeses() {
        val tarea =
            Task(
                id = "orig",
                title = "Regar planta",
                date = LocalDate(2026, 1, 15),
                increment = IncrementConfig(amount = 2, everyValue = 1, everyUnit = IncrementUnit.MESES),
            )

        val copias = useCase(tarea, idsSecuenciales())

        assertEquals(listOf(LocalDate(2026, 2, 15), LocalDate(2026, 3, 15)), copias.map { it.date })
    }

    @Test
    fun cadaCopiaLlevaUnIdNuevoYNoQuedaCompletadaNiIncremental() {
        val tarea =
            Task(
                id = "orig",
                title = "Regar planta",
                date = HOY,
                isCompleted = true,
                increment = IncrementConfig(amount = 2, everyValue = 1, everyUnit = IncrementUnit.DIAS),
            )

        val copias = useCase(tarea, idsSecuenciales())

        assertEquals(listOf("copia-0", "copia-1"), copias.map { it.id })
        copias.forEach {
            assertEquals(false, it.isCompleted)
            assertNull(it.increment)
        }
    }

    @Test
    fun cadaCopiaMantieneLasCaracteristicasBasicasDeLaOriginal() {
        val tarea =
            Task(
                id = "orig",
                title = "Regar planta",
                description = "En el balcon",
                date = HOY,
                time = LocalTime(9, 0),
                durationMinutes = 15,
                category = TaskCategory.HOGAR,
                priority = TaskPriority.ALTA,
                reminderFrequency = ReminderFrequency.DIARIO,
                increment = IncrementConfig(amount = 1, everyValue = 1, everyUnit = IncrementUnit.DIAS),
            )

        val copia = useCase(tarea, idsSecuenciales()).single()

        assertEquals(tarea.title, copia.title)
        assertEquals(tarea.description, copia.description)
        assertEquals(tarea.time, copia.time)
        assertEquals(tarea.durationMinutes, copia.durationMinutes)
        assertEquals(tarea.category, copia.category)
        assertEquals(tarea.priority, copia.priority)
        assertEquals(tarea.reminderFrequency, copia.reminderFrequency)
    }
}

private fun LocalDate.plusDias(dias: Int): LocalDate =
    kotlinx.datetime.LocalDate.fromEpochDays(
        this.toEpochDays() + dias,
    )
