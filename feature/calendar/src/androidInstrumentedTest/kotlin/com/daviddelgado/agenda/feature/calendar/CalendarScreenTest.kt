package com.daviddelgado.agenda.feature.calendar

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.repository.TaskRepository
import com.daviddelgado.agenda.domain.usecase.ObserveTasksUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Analoga a la de commonTest: solo necesita la lectura de tareas. */
private class FakeTaskRepository(initial: List<Task> = emptyList()) : TaskRepository {
    private val tasks = MutableStateFlow(initial)

    override fun observeTasks(date: LocalDate?): Flow<List<Task>> = tasks.map { it }

    override suspend fun getTask(id: String): Task? = null

    override suspend fun upsertTask(task: Task) = Unit

    override suspend fun deleteTask(id: String) = Unit

    override suspend fun deleteTasks(ids: List<String>) = Unit

    override suspend fun deleteAllTasks() = Unit

    override suspend fun toggleCompleted(id: String) = Unit

    override suspend fun syncTasks(): Result<Unit> = Result.success(Unit)

    override fun observeRemoteChanges(): Flow<Unit> = emptyFlow()
}

/**
 * Tests de UI de Compose para CalendarScreen (analogos a los de feature:tasks/login).
 * Cubren la cuadricula de dias: cada celda debe ocupar su propia columna, no superponerse
 * con las demas (bug real ya visto una vez en StreaksScreen, seccion 7 de ESTADO_PROYECTO.md).
 */
class CalendarScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val hoy = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun viewModelCon(tasks: List<Task> = emptyList()) =
        CalendarViewModel(ObserveTasksUseCase(FakeTaskRepository(tasks)))

    @Test
    fun elDiaUnoYElDiaDosDelMesNoSeSuperponenEnPantalla() {
        composeRule.setContent { CalendarScreen(onOpenDay = {}, viewModel = viewModelCon()) }

        val diaUno = composeRule.onNodeWithText("1").fetchSemanticsNode().boundsInRoot
        val diaDos = composeRule.onNodeWithText("2").fetchSemanticsNode().boundsInRoot

        assertFalse("los dias 1 y 2 no deberian ocupar el mismo hueco de la rejilla", diaUno.overlaps(diaDos))
    }

    @Test
    fun pulsarUnDiaConcretoSeleccionaEseDiaYNoOtro() {
        var diaAbierto: LocalDate? = null
        composeRule.setContent {
            CalendarScreen(onOpenDay = { diaAbierto = it }, viewModel = viewModelCon())
        }

        composeRule.onNodeWithText("5").performClick()

        assertEquals(LocalDate(hoy.year, hoy.month, 5), diaAbierto)
    }

    @Test
    fun unDiaConTareasMuestraCuantasTiene() {
        val tasks =
            listOf(
                Task(id = "1", title = "Comprar pan", date = hoy),
                Task(id = "2", title = "Gimnasio", date = hoy),
            )

        composeRule.setContent { CalendarScreen(onOpenDay = {}, viewModel = viewModelCon(tasks)) }

        composeRule.onNodeWithText("(2)").assertIsDisplayed()
    }
}
