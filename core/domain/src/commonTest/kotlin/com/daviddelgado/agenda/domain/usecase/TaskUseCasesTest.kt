package com.daviddelgado.agenda.domain.usecase

import com.daviddelgado.agenda.domain.model.IncrementConfig
import com.daviddelgado.agenda.domain.model.IncrementUnit
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.model.TaskCategory
import com.daviddelgado.agenda.domain.model.TaskPriority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val HOY = LocalDate(2026, 9, 13)
private val MANANA = LocalDate(2026, 9, 14)

private fun tarea(
    id: String,
    date: LocalDate = HOY,
    isCompleted: Boolean = false,
) = Task(id = id, title = "Tarea $id", date = date, isCompleted = isCompleted)

class TaskUseCasesTest {
    @Test
    fun observarTareasSinFechaDevuelveTodas() =
        runTest {
            val repository = FakeTaskRepository(listOf(tarea("1"), tarea("2", MANANA)))

            val tareas = ObserveTasksUseCase(repository)().first()

            assertEquals(listOf("1", "2"), tareas.map { it.id })
        }

    @Test
    fun observarTareasConFechaFiltraPorEseDia() =
        runTest {
            val repository = FakeTaskRepository(listOf(tarea("1"), tarea("2", MANANA)))

            val tareas = ObserveTasksUseCase(repository)(MANANA).first()

            assertEquals(listOf("2"), tareas.map { it.id })
        }

    @Test
    fun guardarUnaTareaLaAnadeYVolverAGuardarlaLaActualiza() =
        runTest {
            val repository = FakeTaskRepository()
            val upsert = UpsertTaskUseCase(repository)

            upsert(tarea("1"))
            upsert(tarea("1").copy(title = "Titulo nuevo"))

            assertEquals(1, repository.tasks.value.size)
            assertEquals("Titulo nuevo", repository.tasks.value.single().title)
        }

    @Test
    fun guardarUnaTareaIncrementalConservaSuConfiguracion() =
        runTest {
            val repository = FakeTaskRepository()
            val incremental =
                tarea("1").copy(
                    category = TaskCategory.SALUD,
                    priority = TaskPriority.ALTA,
                    increment = IncrementConfig(amount = 5, everyValue = 2, everyUnit = IncrementUnit.SEMANAS),
                )

            UpsertTaskUseCase(repository)(incremental)

            val guardada = repository.tasks.value.single()
            assertTrue(guardada.isIncremental)
            assertEquals(IncrementConfig(5, 2, IncrementUnit.SEMANAS), guardada.increment)
        }

    @Test
    fun borrarUnaTareaSoloQuitaEsa() =
        runTest {
            val repository = FakeTaskRepository(listOf(tarea("1"), tarea("2")))

            DeleteTaskUseCase(repository)("1")

            assertEquals(listOf("2"), repository.tasks.value.map { it.id })
        }

    @Test
    fun borradoConjuntoQuitaSoloLasTareasElegidas() =
        runTest {
            val repository = FakeTaskRepository(listOf(tarea("1"), tarea("2"), tarea("3")))

            DeleteTasksUseCase(repository)(listOf("1", "3"))

            assertEquals(listOf("2"), repository.tasks.value.map { it.id })
        }

    @Test
    fun borradoConjuntoTotalVaciaLaLista() =
        runTest {
            val repository = FakeTaskRepository(listOf(tarea("1"), tarea("2")))

            DeleteAllTasksUseCase(repository)()

            assertTrue(repository.tasks.value.isEmpty())
        }

    @Test
    fun alternarCompletadaCambiaSoloEsaTarea() =
        runTest {
            val repository = FakeTaskRepository(listOf(tarea("1"), tarea("2")))
            val toggle = ToggleTaskCompletionUseCase(repository)

            toggle("1")

            assertTrue(repository.tasks.value.first { it.id == "1" }.isCompleted)
            assertFalse(repository.tasks.value.first { it.id == "2" }.isCompleted)

            toggle("1")
            assertFalse(repository.tasks.value.first { it.id == "1" }.isCompleted)
        }

    @Test
    fun sincronizarDelegaEnElRepositorioYPropagaElError() =
        runTest {
            val repository = FakeTaskRepository()
            val sync = SyncTasksUseCase(repository)

            assertTrue(sync().isSuccess)

            repository.syncResult = Result.failure(IllegalStateException("sin red"))
            val fallo = sync()

            assertTrue(fallo.isFailure)
            assertEquals("sin red", fallo.exceptionOrNull()?.message)
            assertEquals(2, repository.syncCount)
        }
}
