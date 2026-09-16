package com.daviddelgado.agenda.feature.tasks

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.repository.TaskRepository
import com.daviddelgado.agenda.domain.usecase.DeleteAllTasksUseCase
import com.daviddelgado.agenda.domain.usecase.DeleteTaskUseCase
import com.daviddelgado.agenda.domain.usecase.DeleteTasksUseCase
import com.daviddelgado.agenda.domain.usecase.GenerateTaskRepetitionsUseCase
import com.daviddelgado.agenda.domain.usecase.ObserveTaskChangesUseCase
import com.daviddelgado.agenda.domain.usecase.ObserveTasksUseCase
import com.daviddelgado.agenda.domain.usecase.SyncTasksUseCase
import com.daviddelgado.agenda.domain.usecase.ToggleTaskCompletionUseCase
import com.daviddelgado.agenda.domain.usecase.UpsertTaskUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * TaskRepository falso propio de este test instrumentado, en memoria y sin red (analogo al
 * de TasksViewModelTest.kt en commonTest, que este source set no hereda por defecto).
 */
private class FakeTaskRepository(initial: List<Task> = emptyList()) : TaskRepository {
    private val tasks = MutableStateFlow(initial)
    private val remoteChanges = MutableSharedFlow<Unit>()

    override fun observeTasks(date: LocalDate?): Flow<List<Task>> =
        tasks.map { list -> if (date == null) list else list.filter { it.date == date } }

    override suspend fun getTask(id: String): Task? = tasks.value.firstOrNull { it.id == id }

    override suspend fun upsertTask(task: Task) {
        tasks.value = tasks.value.filterNot { it.id == task.id } + task
    }

    override suspend fun deleteTask(id: String) {
        tasks.value = tasks.value.filterNot { it.id == id }
    }

    override suspend fun deleteTasks(ids: List<String>) {
        tasks.value = tasks.value.filterNot { it.id in ids }
    }

    override suspend fun deleteAllTasks() {
        tasks.value = emptyList()
    }

    override suspend fun toggleCompleted(id: String) {
        tasks.value = tasks.value.map { if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it }
    }

    override suspend fun syncTasks(): Result<Unit> = Result.success(Unit)

    override fun observeRemoteChanges(): Flow<Unit> = remoteChanges
}

/**
 * Tests de UI de Compose (punto 5 de markdown.md) para TasksScreen: corren en un
 * dispositivo/emulador real montando la pantalla con un ViewModel real + este fake.
 */
class TasksScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val hoy = Clock.System.todayIn(TimeZone.currentSystemDefault())

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
    fun sinTareasSeMuestraElEstadoVacio() {
        composeRule.setContent { TasksScreen(viewModel = viewModelCon(FakeTaskRepository())) }

        composeRule.onNodeWithText("No hay tareas para este dia").assertIsDisplayed()
    }

    @Test
    fun unaTareaExistenteApareceEnLaLista() {
        val repository = FakeTaskRepository(listOf(Task(id = "t-1", title = "Comprar pan", date = hoy)))

        composeRule.setContent { TasksScreen(viewModel = viewModelCon(repository)) }

        composeRule.onNodeWithText("Comprar pan").assertIsDisplayed()
    }

    @Test
    fun elBotonNuevaTareaAbreElFormularioConElCampoTitulo() {
        composeRule.setContent { TasksScreen(viewModel = viewModelCon(FakeTaskRepository())) }

        composeRule.onNodeWithContentDescription("Nueva tarea").performClick()

        composeRule.onNodeWithText("Nueva tarea").assertIsDisplayed()
        composeRule.onNodeWithText("Titulo").assertIsDisplayed()
    }

    @Test
    fun crearUnaTareaDesdeElFormularioLaMuestraEnLaLista() {
        composeRule.setContent { TasksScreen(viewModel = viewModelCon(FakeTaskRepository())) }

        composeRule.onNodeWithContentDescription("Nueva tarea").performClick()
        composeRule.onNodeWithText("Titulo").performTextInput("Flexiones")
        composeRule.onNodeWithText("Guardar").performClick()

        composeRule.onNodeWithText("Flexiones").assertIsDisplayed()
    }

    @Test
    fun guardarSinTituloMuestraElErrorYNoCierraElFormulario() {
        composeRule.setContent { TasksScreen(viewModel = viewModelCon(FakeTaskRepository())) }

        composeRule.onNodeWithContentDescription("Nueva tarea").performClick()
        composeRule.onNodeWithText("Guardar").performClick()

        composeRule.onNodeWithText("El titulo no puede estar vacio").assertIsDisplayed()
        composeRule.onNodeWithText("Nueva tarea").assertIsDisplayed()
    }

    @Test
    fun alAbrirConUnaFechaInicialMuestraLasTareasDeEseDiaYAvisaDeConsumirla() {
        val otroDia = hoy.plus(3, DateTimeUnit.DAY)
        val repository =
            FakeTaskRepository(
                listOf(
                    Task(id = "t-1", title = "Comprar pan", date = hoy),
                    Task(id = "t-2", title = "Cita medico", date = otroDia),
                ),
            )
        var fechaConsumida = false

        composeRule.setContent {
            TasksScreen(
                initialDate = otroDia,
                onDateConsumed = { fechaConsumida = true },
                viewModel = viewModelCon(repository),
            )
        }

        composeRule.onNodeWithText("Cita medico").assertIsDisplayed()
        composeRule.onAllNodesWithText("Comprar pan").assertCountEquals(0)
        composeRule.runOnIdle { assertTrue(fechaConsumida) }
    }

    @Test
    fun activarIncrementalHaceQueElFormularioSeaDeslizable() {
        composeRule.setContent { TasksScreen(viewModel = viewModelCon(FakeTaskRepository())) }

        composeRule.onNodeWithContentDescription("Nueva tarea").performClick()
        // Antes del fix el formulario no tenia ningun contenedor deslizable: al activar el
        // interruptor, los campos de incremento (cantidad, cada cuanto, unidad) se salian del
        // alto fijo del dialogo y el recorte se veia como una caja superpuesta con la
        // anterior en vez de poder deslizarse hasta ellos (verificado a mano en el emulador).
        composeRule.onNodeWithContentDescription("Tarea incremental").performClick()

        val nodo = composeRule.onNodeWithTag("taskFormScroll").fetchSemanticsNode()
        assertTrue(
            "el formulario deberia tener un contenedor con accion de scroll",
            nodo.config.contains(SemanticsActions.ScrollBy),
        )
    }

    @Test
    fun escribirEnElBuscadorOcultaLasTareasQueNoCoinciden() {
        val tareas =
            listOf(
                Task(id = "1", title = "Comprar pan", date = hoy),
                Task(id = "2", title = "Gimnasio", date = hoy),
            )
        composeRule.setContent { TasksScreen(viewModel = viewModelCon(FakeTaskRepository(tareas))) }

        composeRule.onNodeWithText("Buscar por titulo").performTextInput("gimna")

        composeRule.onNodeWithText("Comprar pan").assertDoesNotExist()
        composeRule.onNodeWithText("Gimnasio").assertIsDisplayed()
    }
}
