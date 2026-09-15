package com.daviddelgado.agenda.feature.calendar

import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.repository.TaskRepository
import com.daviddelgado.agenda.domain.usecase.ObserveTasksUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Solo se necesita la lectura de tareas para pintar los dias con tareas del calendario. */
private class FakeTaskRepository(tasks: List<Task>) : TaskRepository {
    private val state = MutableStateFlow(tasks)

    override fun observeTasks(date: LocalDate?): Flow<List<Task>> =
        state.map { list -> if (date == null) list else list.filter { it.date == date } }

    override suspend fun getTask(id: String): Task? = state.value.firstOrNull { it.id == id }

    override suspend fun upsertTask(task: Task) = Unit

    override suspend fun deleteTask(id: String) = Unit

    override suspend fun deleteTasks(ids: List<String>) = Unit

    override suspend fun deleteAllTasks() = Unit

    override suspend fun toggleCompleted(id: String) = Unit

    override suspend fun syncTasks(): Result<Unit> = Result.success(Unit)

    override fun observeRemoteChanges(): Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val hoy = Clock.System.todayIn(TimeZone.currentSystemDefault())

    @BeforeTest
    fun prepararDispatcherPrincipal() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun restaurarDispatcherPrincipal() {
        Dispatchers.resetMain()
    }

    private fun viewModelCon(tasks: List<Task> = emptyList()) =
        CalendarViewModel(ObserveTasksUseCase(FakeTaskRepository(tasks)))

    @Test
    fun elCalendarioArrancaEnElDiaDeHoy() =
        runTest(dispatcher) {
            val viewModel = viewModelCon()

            assertEquals(hoy, viewModel.currentState.selectedDate)
            assertEquals(hoy, viewModel.currentState.visibleMonth)
        }

    @Test
    fun seCuentanLasTareasDeCadaDia() =
        runTest(dispatcher) {
            val otroDia = hoy.plus(3, DateTimeUnit.DAY)
            val viewModel =
                viewModelCon(
                    listOf(
                        Task(id = "1", title = "Comprar pan", date = hoy),
                        Task(id = "2", title = "Gimnasio", date = otroDia),
                        Task(id = "3", title = "Leer", date = otroDia),
                    ),
                )

            assertEquals(mapOf(hoy to 1, otroDia to 2), viewModel.currentState.taskCountsByDate)
        }

    @Test
    fun pasarAlMesSiguienteYVolverDejaElMismoMes() =
        runTest(dispatcher) {
            val viewModel = viewModelCon()

            viewModel.onIntent(CalendarIntent.NextMonth)
            assertEquals(hoy.plus(1, DateTimeUnit.MONTH), viewModel.currentState.visibleMonth)

            viewModel.onIntent(CalendarIntent.PreviousMonth)
            assertEquals(hoy, viewModel.currentState.visibleMonth)
        }

    @Test
    fun elegirUnDiaLoSeleccionaYPideAbrirlo() =
        runTest(dispatcher) {
            val viewModel = viewModelCon()
            val efectos = mutableListOf<CalendarEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)
            val dia = hoy.plus(2, DateTimeUnit.DAY)

            viewModel.onIntent(CalendarIntent.SelectDate(dia))

            assertEquals(dia, viewModel.currentState.selectedDate)
            assertEquals(listOf<CalendarEffect>(CalendarEffect.OpenDay(dia)), efectos)
        }
}
