package com.daviddelgado.agenda.feature.tasks

import androidx.lifecycle.viewModelScope
import com.daviddelgado.agenda.common.mvi.MviViewModel
import com.daviddelgado.agenda.common.util.randomEntityId
import com.daviddelgado.agenda.domain.model.IncrementConfig
import com.daviddelgado.agenda.domain.model.IncrementUnit
import com.daviddelgado.agenda.domain.model.ReminderFrequency
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.model.TaskCategory
import com.daviddelgado.agenda.domain.model.TaskPriority
import com.daviddelgado.agenda.domain.usecase.DeleteAllTasksUseCase
import com.daviddelgado.agenda.domain.usecase.DeleteTaskUseCase
import com.daviddelgado.agenda.domain.usecase.DeleteTasksUseCase
import com.daviddelgado.agenda.domain.usecase.ObserveTaskChangesUseCase
import com.daviddelgado.agenda.domain.usecase.ObserveTasksUseCase
import com.daviddelgado.agenda.domain.usecase.SyncTasksUseCase
import com.daviddelgado.agenda.domain.usecase.ToggleTaskCompletionUseCase
import com.daviddelgado.agenda.domain.usecase.UpsertTaskUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Resultado de interpretar el campo de texto libre "HH:mm" del formulario. */
private sealed interface TimeParseResult {
    data object Empty : TimeParseResult

    data class Parsed(val time: LocalTime) : TimeParseResult

    data object Invalid : TimeParseResult
}

/** Resultado de interpretar los campos de incremento del formulario. */
private sealed interface IncrementParseResult {
    data object NotIncremental : IncrementParseResult

    data class Valid(val config: IncrementConfig) : IncrementParseResult

    data object Invalid : IncrementParseResult
}

class TasksViewModel(
    private val observeTasksUseCase: ObserveTasksUseCase,
    private val upsertTaskUseCase: UpsertTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val deleteTasksUseCase: DeleteTasksUseCase,
    private val deleteAllTasksUseCase: DeleteAllTasksUseCase,
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase,
    private val syncTasksUseCase: SyncTasksUseCase,
    private val observeTaskChangesUseCase: ObserveTaskChangesUseCase,
) : MviViewModel<TasksState, TasksIntent, TasksEffect>(
        TasksState(selectedDate = Clock.System.todayIn(TimeZone.currentSystemDefault())),
    ) {
    init {
        observeTasks()
        // Lo local se muestra ya; la sincronizacion con el servidor va detras y si falla
        // (sin red o servidor caido) la pantalla sigue usable con los datos de Room.
        sync(notifyErrors = false)
        // Tiempo real: si otro dispositivo cambia las tareas, el servidor avisa por
        // WebSocket y aqui se responde con una resincronizacion silenciosa.
        observeTaskChangesUseCase()
            .onEach { sync(notifyErrors = false) }
            .launchIn(viewModelScope)
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onIntent(intent: TasksIntent) {
        when (intent) {
            is TasksIntent.SelectDate -> {
                setState { copy(selectedDate = intent.date) }
                observeTasks()
            }
            is TasksIntent.ToggleCompleted -> viewModelScope.launch { toggleTaskCompletionUseCase(intent.taskId) }
            TasksIntent.Refresh -> sync(notifyErrors = true)
            TasksIntent.OpenNewTaskForm -> openFormForNewTask()
            is TasksIntent.OpenEditTaskForm -> openFormForEdit(intent.task)
            TasksIntent.DismissForm -> setState { copy(isFormVisible = false) }
            is TasksIntent.FormTitleChanged -> setState { copy(formTitle = intent.value, formError = null) }
            is TasksIntent.FormDescriptionChanged -> setState { copy(formDescription = intent.value) }
            is TasksIntent.FormCategoryChanged -> setState { copy(formCategory = intent.value) }
            is TasksIntent.FormPriorityChanged -> setState { copy(formPriority = intent.value) }
            is TasksIntent.FormReminderChanged -> setState { copy(formReminderFrequency = intent.value) }
            is TasksIntent.FormTimeChanged -> setState { copy(formTime = intent.value, formError = null) }
            is TasksIntent.FormDurationChanged -> setState { copy(formDurationMinutes = intent.value) }
            is TasksIntent.FormIncrementToggled -> setState { copy(formIsIncremental = intent.enabled) }
            is TasksIntent.FormIncrementAmountChanged -> setState { copy(formIncrementAmount = intent.value) }
            is TasksIntent.FormIncrementEveryValueChanged ->
                setState { copy(formIncrementEveryValue = intent.value) }
            is TasksIntent.FormIncrementEveryUnitChanged ->
                setState { copy(formIncrementEveryUnit = intent.value) }
            TasksIntent.SaveTask -> saveTask()
            is TasksIntent.RequestDelete -> setState { copy(taskPendingDelete = intent.task) }
            TasksIntent.CancelDelete -> setState { copy(taskPendingDelete = null) }
            TasksIntent.ConfirmDelete -> confirmDelete()
            is TasksIntent.EnterSelectionMode ->
                setState { copy(isSelectionMode = true, selectedTaskIds = setOf(intent.taskId)) }
            is TasksIntent.ToggleTaskSelection -> toggleSelection(intent.taskId)
            TasksIntent.SelectAll -> setState { copy(selectedTaskIds = tasks.map { it.id }.toSet()) }
            TasksIntent.ExitSelectionMode -> setState { copy(isSelectionMode = false, selectedTaskIds = emptySet()) }
            TasksIntent.RequestBulkDelete -> setState { copy(isBulkDeletePending = true) }
            TasksIntent.CancelBulkDelete -> setState { copy(isBulkDeletePending = false) }
            TasksIntent.ConfirmBulkDelete -> confirmBulkDelete()
        }
    }

    private fun observeTasks() {
        observeTasksUseCase(currentState.selectedDate)
            .onEach { tasks -> setState { copy(tasks = tasks, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    /** @param notifyErrors true cuando el usuario ha pedido el refresco y espera respuesta. */
    private fun sync(notifyErrors: Boolean) {
        viewModelScope.launch {
            setState { copy(isSyncing = true) }
            syncTasksUseCase()
                .onFailure { error ->
                    if (notifyErrors) {
                        sendEffect(TasksEffect.ShowError(error.message ?: "No se pudo sincronizar"))
                    }
                }
            setState { copy(isSyncing = false) }
        }
    }

    private fun openFormForNewTask() {
        setState {
            copy(
                isFormVisible = true,
                editingTaskId = null,
                formTitle = "",
                formDescription = "",
                formCategory = TaskCategory.OTRO,
                formPriority = TaskPriority.MEDIA,
                formReminderFrequency = ReminderFrequency.NINGUNO,
                formTime = "",
                formDurationMinutes = "",
                formIsIncremental = false,
                formIncrementAmount = "",
                formIncrementEveryValue = "",
                formIncrementEveryUnit = IncrementUnit.REPETICIONES,
                formError = null,
            )
        }
    }

    private fun openFormForEdit(task: Task) {
        setState {
            copy(
                isFormVisible = true,
                editingTaskId = task.id,
                formTitle = task.title,
                formDescription = task.description,
                formCategory = task.category,
                formPriority = task.priority,
                formReminderFrequency = task.reminderFrequency,
                formTime = task.time?.toString() ?: "",
                formDurationMinutes = task.durationMinutes?.toString() ?: "",
                formIsIncremental = task.isIncremental,
                formIncrementAmount = task.increment?.amount?.toString() ?: "",
                formIncrementEveryValue = task.increment?.everyValue?.toString() ?: "",
                formIncrementEveryUnit = task.increment?.everyUnit ?: IncrementUnit.REPETICIONES,
                formError = null,
            )
        }
    }

    private fun saveTask() {
        val state = currentState
        if (state.formTitle.isBlank()) {
            setState { copy(formError = "El titulo no puede estar vacio") }
            return
        }

        val time =
            when (val result = parseOptionalTime(state.formTime)) {
                TimeParseResult.Invalid -> {
                    setState { copy(formError = "La hora debe tener el formato HH:mm") }
                    return
                }
                TimeParseResult.Empty -> null
                is TimeParseResult.Parsed -> result.time
            }

        val duration = state.formDurationMinutes.takeIf { it.isNotBlank() }?.toIntOrNull()
        if (state.formDurationMinutes.isNotBlank() && duration == null) {
            setState { copy(formError = "La duracion debe ser un numero de minutos") }
            return
        }

        val increment =
            when (val result = parseIncrement(state)) {
                IncrementParseResult.Invalid -> {
                    setState { copy(formError = "Rellena cantidad y cadencia del incremento") }
                    return
                }
                IncrementParseResult.NotIncremental -> null
                is IncrementParseResult.Valid -> result.config
            }

        val task =
            Task(
                id = state.editingTaskId ?: randomEntityId(),
                title = state.formTitle,
                description = state.formDescription,
                date = state.selectedDate,
                time = time,
                durationMinutes = duration,
                category = state.formCategory,
                priority = state.formPriority,
                reminderFrequency = state.formReminderFrequency,
                increment = increment,
                isCompleted = state.tasks.firstOrNull { it.id == state.editingTaskId }?.isCompleted ?: false,
            )

        viewModelScope.launch {
            upsertTaskUseCase(task)
            setState { copy(isFormVisible = false) }
        }
    }

    private fun parseOptionalTime(raw: String): TimeParseResult {
        if (raw.isBlank()) return TimeParseResult.Empty
        val normalized = if (raw.length == 5) "$raw:00" else raw
        val parsed = runCatching { LocalTime.parse(normalized) }.getOrNull()
        return parsed?.let { TimeParseResult.Parsed(it) } ?: TimeParseResult.Invalid
    }

    private fun parseIncrement(state: TasksState): IncrementParseResult {
        if (!state.formIsIncremental) return IncrementParseResult.NotIncremental
        val amount = state.formIncrementAmount.toIntOrNull() ?: return IncrementParseResult.Invalid
        val everyValue = state.formIncrementEveryValue.toIntOrNull() ?: return IncrementParseResult.Invalid
        return IncrementParseResult.Valid(
            IncrementConfig(amount = amount, everyValue = everyValue, everyUnit = state.formIncrementEveryUnit),
        )
    }

    private fun confirmDelete() {
        val task = currentState.taskPendingDelete ?: return
        viewModelScope.launch {
            deleteTaskUseCase(task.id)
            setState { copy(taskPendingDelete = null) }
        }
    }

    private fun toggleSelection(taskId: String) {
        setState {
            val newSelection = if (taskId in selectedTaskIds) selectedTaskIds - taskId else selectedTaskIds + taskId
            copy(selectedTaskIds = newSelection, isSelectionMode = newSelection.isNotEmpty())
        }
    }

    private fun confirmBulkDelete() {
        val state = currentState
        viewModelScope.launch {
            if (state.selectedTaskIds.size == state.tasks.size && state.tasks.isNotEmpty()) {
                deleteAllTasksUseCase()
            } else {
                deleteTasksUseCase(state.selectedTaskIds.toList())
            }
            setState { copy(isBulkDeletePending = false, isSelectionMode = false, selectedTaskIds = emptySet()) }
        }
    }
}
