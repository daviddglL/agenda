package com.daviddelgado.agenda.feature.tasks

import com.daviddelgado.agenda.domain.model.IncrementUnit
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.usecase.DeleteAllTasksUseCase
import com.daviddelgado.agenda.domain.usecase.DeleteTaskUseCase
import com.daviddelgado.agenda.domain.usecase.DeleteTasksUseCase
import com.daviddelgado.agenda.domain.usecase.GenerateTaskRepetitionsUseCase
import com.daviddelgado.agenda.domain.usecase.ObserveTaskChangesUseCase
import com.daviddelgado.agenda.domain.usecase.ObserveTasksUseCase
import com.daviddelgado.agenda.domain.usecase.SyncTasksUseCase
import com.daviddelgado.agenda.domain.usecase.ToggleTaskCompletionUseCase
import com.daviddelgado.agenda.domain.usecase.UpsertTaskUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val hoy = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val manana = hoy.plus(1, DateTimeUnit.DAY)

    @BeforeTest
    fun prepararDispatcherPrincipal() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun restaurarDispatcherPrincipal() {
        Dispatchers.resetMain()
    }

    private fun viewModelCon(repository: FakeTaskRepository) =
        TasksViewModel(
            observeTasksUseCase = ObserveTasksUseCase(repository),
            upsertTaskUseCase = UpsertTaskUseCase(repository),
            deleteTaskUseCase = DeleteTaskUseCase(repository),
            deleteTasksUseCase = DeleteTasksUseCase(repository),
            deleteAllTasksUseCase = DeleteAllTasksUseCase(repository),
            toggleTaskCompletionUseCase = ToggleTaskCompletionUseCase(repository),
            syncTasksUseCase = SyncTasksUseCase(repository),
            observeTaskChangesUseCase = ObserveTaskChangesUseCase(repository),
            generateTaskRepetitionsUseCase = GenerateTaskRepetitionsUseCase(),
        )

    @Test
    fun alAbrirLaPantallaSeMuestranLasTareasDeHoyYSeSincroniza() =
        runTest(dispatcher) {
            val repository =
                FakeTaskRepository(
                    listOf(
                        Task(id = "hoy", title = "Comprar pan", date = hoy),
                        Task(id = "manana", title = "Gimnasio", date = manana),
                    ),
                )

            val viewModel = viewModelCon(repository)

            assertEquals(listOf("hoy"), viewModel.currentState.tasks.map { it.id })
            assertEquals(1, repository.syncCount)
            assertFalse(viewModel.currentState.isSyncing)
        }

    @Test
    fun siLaSincronizacionDeArranqueFallaNoSeMolestaAlUsuario() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository()
            repository.syncResult = Result.failure(IllegalStateException("sin red"))
            val efectos = mutableListOf<TasksEffect>()

            val viewModel = viewModelCon(repository)
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            assertEquals(1, repository.syncCount)
            assertTrue(efectos.isEmpty())
        }

    @Test
    fun refrescarAManoAvisaSiLaSincronizacionFalla() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository()
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<TasksEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)
            repository.syncResult = Result.failure(IllegalStateException("sin red"))

            viewModel.onIntent(TasksIntent.Refresh)

            assertEquals(2, repository.syncCount)
            assertEquals(listOf<TasksEffect>(TasksEffect.ShowError("sin red")), efectos)
            assertFalse(viewModel.currentState.isSyncing)
        }

    @Test
    fun unAvisoDeCambioRemotoDisparaUnaResincronizacionSilenciosa() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository()
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<TasksEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)
            val syncsAlArrancar = repository.syncCount

            repository.remoteChanges.emit(Unit)

            assertEquals(syncsAlArrancar + 1, repository.syncCount)
            assertTrue(efectos.isEmpty())
        }

    @Test
    fun cambiarDeDiaMuestraLasTareasDeEseDia() =
        runTest(dispatcher) {
            val repository =
                FakeTaskRepository(
                    listOf(
                        Task(id = "hoy", title = "Comprar pan", date = hoy),
                        Task(id = "manana", title = "Gimnasio", date = manana),
                    ),
                )
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.SelectDate(manana))

            assertEquals(manana, viewModel.currentState.selectedDate)
            assertEquals(listOf("manana"), viewModel.currentState.tasks.map { it.id })
        }

    @Test
    fun guardarUnaTareaSinTituloNoGuardaNadaYMuestraElErrorEnElFormulario() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository()
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.OpenNewTaskForm)
            viewModel.onIntent(TasksIntent.SaveTask)

            assertTrue(repository.tasks.value.isEmpty())
            assertEquals("El titulo no puede estar vacio", viewModel.currentState.formError)
            assertTrue(viewModel.currentState.isFormVisible)
        }

    @Test
    fun guardarUnaTareaLaCreaEnElDiaSeleccionadoYCierraElFormulario() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository()
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.OpenNewTaskForm)
            viewModel.onIntent(TasksIntent.FormTitleChanged("Comprar pan"))
            viewModel.onIntent(TasksIntent.FormDescriptionChanged("En la panaderia"))
            viewModel.onIntent(TasksIntent.SaveTask)

            val guardada = repository.tasks.value.single()
            assertEquals("Comprar pan", guardada.title)
            assertEquals("En la panaderia", guardada.description)
            assertEquals(hoy, guardada.date)
            assertTrue(guardada.id.isNotBlank())
            assertFalse(viewModel.currentState.isFormVisible)
        }

    @Test
    fun abrirElFormularioLimpiaLoEscritoAntes() =
        runTest(dispatcher) {
            val viewModel = viewModelCon(FakeTaskRepository())

            viewModel.onIntent(TasksIntent.OpenNewTaskForm)
            viewModel.onIntent(TasksIntent.FormTitleChanged("Borrador"))
            viewModel.onIntent(TasksIntent.DismissForm)
            viewModel.onIntent(TasksIntent.OpenNewTaskForm)

            assertEquals("", viewModel.currentState.formTitle)
            assertEquals("", viewModel.currentState.formDescription)
        }

    @Test
    fun marcarCompletadaCambiaLaTarea() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository(listOf(Task(id = "t-1", title = "Comprar pan", date = hoy)))
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.ToggleCompleted("t-1"))

            assertTrue(repository.tasks.value.single().isCompleted)
        }

    @Test
    fun elBorradoPideConfirmacionAntesDeBorrar() =
        runTest(dispatcher) {
            val tarea = Task(id = "t-1", title = "Comprar pan", date = hoy)
            val repository = FakeTaskRepository(listOf(tarea))
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.RequestDelete(tarea))
            assertEquals(tarea, viewModel.currentState.taskPendingDelete)
            assertEquals(1, repository.tasks.value.size)

            viewModel.onIntent(TasksIntent.ConfirmDelete)
            assertTrue(repository.tasks.value.isEmpty())
            assertNull(viewModel.currentState.taskPendingDelete)
        }

    @Test
    fun cancelarElBorradoDejaLaTarea() =
        runTest(dispatcher) {
            val tarea = Task(id = "t-1", title = "Comprar pan", date = hoy)
            val repository = FakeTaskRepository(listOf(tarea))
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.RequestDelete(tarea))
            viewModel.onIntent(TasksIntent.CancelDelete)

            assertNull(viewModel.currentState.taskPendingDelete)
            assertEquals(1, repository.tasks.value.size)
        }

    @Test
    fun editarUnaTareaPrecargaTodosSusCamposEnElFormulario() =
        runTest(dispatcher) {
            val tarea =
                Task(
                    id = "t-1",
                    title = "Flexiones",
                    description = "Rutina",
                    date = hoy,
                    time = kotlinx.datetime.LocalTime(7, 30),
                    durationMinutes = 15,
                    category = com.daviddelgado.agenda.domain.model.TaskCategory.SALUD,
                    priority = com.daviddelgado.agenda.domain.model.TaskPriority.ALTA,
                    increment =
                        com.daviddelgado.agenda.domain.model.IncrementConfig(
                            5,
                            2,
                            com.daviddelgado.agenda.domain.model.IncrementUnit.SEMANAS,
                        ),
                )
            val viewModel = viewModelCon(FakeTaskRepository(listOf(tarea)))

            viewModel.onIntent(TasksIntent.OpenEditTaskForm(tarea))

            val state = viewModel.currentState
            assertEquals("t-1", state.editingTaskId)
            assertEquals("Flexiones", state.formTitle)
            assertEquals("07:30", state.formTime)
            assertEquals("15", state.formDurationMinutes)
            assertTrue(state.formIsIncremental)
            assertEquals("5", state.formIncrementAmount)
            assertEquals("2", state.formIncrementEveryValue)
        }

    @Test
    fun guardarConHoraInvalidaMuestraErrorYNoGuarda() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository()
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.OpenNewTaskForm)
            viewModel.onIntent(TasksIntent.FormTitleChanged("Gimnasio"))
            viewModel.onIntent(TasksIntent.FormTimeChanged("no-es-una-hora"))
            viewModel.onIntent(TasksIntent.SaveTask)

            assertTrue(repository.tasks.value.isEmpty())
            assertEquals("La hora debe tener el formato HH:mm", viewModel.currentState.formError)
        }

    @Test
    fun guardarUnaTareaIncrementalSinCantidadMuestraError() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository()
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.OpenNewTaskForm)
            viewModel.onIntent(TasksIntent.FormTitleChanged("Flexiones"))
            viewModel.onIntent(TasksIntent.FormIncrementToggled(true))
            viewModel.onIntent(TasksIntent.SaveTask)

            assertTrue(repository.tasks.value.isEmpty())
            assertEquals("Rellena cantidad y cadencia del incremento", viewModel.currentState.formError)
        }

    @Test
    fun guardarUnaTareaIncrementalCompletaGuardaSuConfiguracion() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository()
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.OpenNewTaskForm)
            viewModel.onIntent(TasksIntent.FormTitleChanged("Flexiones"))
            viewModel.onIntent(TasksIntent.FormTimeChanged("07:30"))
            viewModel.onIntent(TasksIntent.FormIncrementToggled(true))
            viewModel.onIntent(TasksIntent.FormIncrementAmountChanged("5"))
            viewModel.onIntent(TasksIntent.FormIncrementEveryValueChanged("2"))
            viewModel.onIntent(TasksIntent.FormIncrementEveryUnitChanged(IncrementUnit.SEMANAS))
            viewModel.onIntent(TasksIntent.SaveTask)

            val guardada = repository.tasks.value.first { it.date == hoy }
            assertEquals(kotlinx.datetime.LocalTime(7, 30), guardada.time)
            assertTrue(guardada.isIncremental)
            assertEquals(5, guardada.increment?.amount)
            assertEquals(2, guardada.increment?.everyValue)
        }

    @Test
    fun guardarUnaTareaIncrementalNuevaCreaLasCopiasConLasFechasYCamposCorrectos() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository()
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.OpenNewTaskForm)
            viewModel.onIntent(TasksIntent.FormTitleChanged("Flexiones"))
            viewModel.onIntent(TasksIntent.FormTimeChanged("07:30"))
            viewModel.onIntent(TasksIntent.FormIncrementToggled(true))
            viewModel.onIntent(TasksIntent.FormIncrementAmountChanged("3"))
            viewModel.onIntent(TasksIntent.FormIncrementEveryValueChanged("2"))
            viewModel.onIntent(TasksIntent.FormIncrementEveryUnitChanged(IncrementUnit.SEMANAS))
            viewModel.onIntent(TasksIntent.SaveTask)

            // La original + 3 copias = 4 tareas en total.
            assertEquals(4, repository.tasks.value.size)
            val copias = repository.tasks.value.filterNot { it.date == hoy }.sortedBy { it.date }
            assertEquals(
                listOf(hoy.plus(14, DateTimeUnit.DAY), hoy.plus(28, DateTimeUnit.DAY), hoy.plus(42, DateTimeUnit.DAY)),
                copias.map { it.date },
            )
            copias.forEach { copia ->
                assertEquals("Flexiones", copia.title)
                assertEquals(kotlinx.datetime.LocalTime(7, 30), copia.time)
                assertFalse(copia.isCompleted)
                assertNull(copia.increment)
                assertTrue(copia.id.isNotBlank())
            }
            // Cada copia tiene su propio id, distinto de la original y entre ellas.
            assertEquals(repository.tasks.value.size, repository.tasks.value.map { it.id }.toSet().size)
        }

    @Test
    fun editarUnaTareaIncrementalAlGuardarNoVuelveAGenerarCopias() =
        runTest(dispatcher) {
            val tarea =
                Task(
                    id = "t-1",
                    title = "Flexiones",
                    date = hoy,
                    increment =
                        com.daviddelgado.agenda.domain.model.IncrementConfig(
                            amount = 3,
                            everyValue = 1,
                            everyUnit = IncrementUnit.SEMANAS,
                        ),
                )
            val repository = FakeTaskRepository(listOf(tarea))
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.OpenEditTaskForm(tarea))
            viewModel.onIntent(TasksIntent.FormTitleChanged("Flexiones diarias"))
            viewModel.onIntent(TasksIntent.SaveTask)

            assertEquals(1, repository.tasks.value.size)
            assertEquals("Flexiones diarias", repository.tasks.value.single().title)
        }

    @Test
    fun editarUnaTareaConservaSuEstadoDeCompletada() =
        runTest(dispatcher) {
            val tarea = Task(id = "t-1", title = "Comprar pan", date = hoy, isCompleted = true)
            val repository = FakeTaskRepository(listOf(tarea))
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.OpenEditTaskForm(tarea))
            viewModel.onIntent(TasksIntent.FormTitleChanged("Comprar pan integral"))
            viewModel.onIntent(TasksIntent.SaveTask)

            val guardada = repository.tasks.value.single()
            assertEquals("Comprar pan integral", guardada.title)
            assertTrue(guardada.isCompleted)
        }

    @Test
    fun pulsacionLargaEntraEnModoSeleccionConEsaTareaMarcada() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository(listOf(Task(id = "t-1", title = "Comprar pan", date = hoy)))
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.EnterSelectionMode("t-1"))

            assertTrue(viewModel.currentState.isSelectionMode)
            assertEquals(setOf("t-1"), viewModel.currentState.selectedTaskIds)
        }

    @Test
    fun elBorradoConjuntoDeVariasSeleccionadasNoBorraElResto() =
        runTest(dispatcher) {
            val repository =
                FakeTaskRepository(
                    listOf(
                        Task(id = "t-1", title = "Uno", date = hoy),
                        Task(id = "t-2", title = "Dos", date = hoy),
                        Task(id = "t-3", title = "Tres", date = hoy),
                    ),
                )
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.EnterSelectionMode("t-1"))
            viewModel.onIntent(TasksIntent.ToggleTaskSelection("t-3"))
            viewModel.onIntent(TasksIntent.RequestBulkDelete)
            assertTrue(viewModel.currentState.isBulkDeletePending)

            viewModel.onIntent(TasksIntent.ConfirmBulkDelete)

            assertEquals(listOf("t-2"), repository.tasks.value.map { it.id })
            assertFalse(viewModel.currentState.isSelectionMode)
            assertTrue(viewModel.currentState.selectedTaskIds.isEmpty())
        }

    @Test
    fun seleccionarTodasYBorrarVaciaLaLista() =
        runTest(dispatcher) {
            val repository =
                FakeTaskRepository(
                    listOf(Task(id = "t-1", title = "Uno", date = hoy), Task(id = "t-2", title = "Dos", date = hoy)),
                )
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.EnterSelectionMode("t-1"))
            viewModel.onIntent(TasksIntent.SelectAll)
            viewModel.onIntent(TasksIntent.ConfirmBulkDelete)

            assertTrue(repository.tasks.value.isEmpty())
        }

    @Test
    fun salirDelModoSeleccionLimpiaLaSeleccion() =
        runTest(dispatcher) {
            val repository = FakeTaskRepository(listOf(Task(id = "t-1", title = "Comprar pan", date = hoy)))
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(TasksIntent.EnterSelectionMode("t-1"))
            viewModel.onIntent(TasksIntent.ExitSelectionMode)

            assertFalse(viewModel.currentState.isSelectionMode)
            assertTrue(viewModel.currentState.selectedTaskIds.isEmpty())
        }
}
