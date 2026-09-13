package com.daviddelgado.agenda.feature.tasks

import com.daviddelgado.agenda.common.mvi.UiEffect
import com.daviddelgado.agenda.common.mvi.UiIntent
import com.daviddelgado.agenda.common.mvi.UiState
import com.daviddelgado.agenda.domain.model.IncrementUnit
import com.daviddelgado.agenda.domain.model.ReminderFrequency
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.model.TaskCategory
import com.daviddelgado.agenda.domain.model.TaskPriority
import kotlinx.datetime.LocalDate

data class TasksState(
    val selectedDate: LocalDate,
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isFormVisible: Boolean = false,
    val editingTaskId: String? = null,
    val formTitle: String = "",
    val formDescription: String = "",
    val formCategory: TaskCategory = TaskCategory.OTRO,
    val formPriority: TaskPriority = TaskPriority.MEDIA,
    val formReminderFrequency: ReminderFrequency = ReminderFrequency.NINGUNO,
    /** Texto libre "HH:mm"; vacío = sin hora. Se valida al guardar, ver [TasksViewModel]. */
    val formTime: String = "",
    val formDurationMinutes: String = "",
    val formIsIncremental: Boolean = false,
    val formIncrementAmount: String = "",
    val formIncrementEveryValue: String = "",
    val formIncrementEveryUnit: IncrementUnit = IncrementUnit.REPETICIONES,
    val formError: String? = null,
    /** Modo selección múltiple para el borrado conjunto (punto 2 de lo pendiente). */
    val isSelectionMode: Boolean = false,
    val selectedTaskIds: Set<String> = emptySet(),
    val taskPendingDelete: Task? = null,
    val isBulkDeletePending: Boolean = false,
) : UiState {
    val selectedCount: Int get() = selectedTaskIds.size
}

sealed interface TasksIntent : UiIntent {
    data class SelectDate(val date: LocalDate) : TasksIntent

    data class ToggleCompleted(val taskId: String) : TasksIntent

    /** Fuerza una sincronizacion con el servidor (boton de refrescar). */
    data object Refresh : TasksIntent

    data object OpenNewTaskForm : TasksIntent

    data class OpenEditTaskForm(val task: Task) : TasksIntent

    data object DismissForm : TasksIntent

    data class FormTitleChanged(val value: String) : TasksIntent

    data class FormDescriptionChanged(val value: String) : TasksIntent

    data class FormCategoryChanged(val value: TaskCategory) : TasksIntent

    data class FormPriorityChanged(val value: TaskPriority) : TasksIntent

    data class FormReminderChanged(val value: ReminderFrequency) : TasksIntent

    data class FormTimeChanged(val value: String) : TasksIntent

    data class FormDurationChanged(val value: String) : TasksIntent

    data class FormIncrementToggled(val enabled: Boolean) : TasksIntent

    data class FormIncrementAmountChanged(val value: String) : TasksIntent

    data class FormIncrementEveryValueChanged(val value: String) : TasksIntent

    data class FormIncrementEveryUnitChanged(val value: IncrementUnit) : TasksIntent

    data object SaveTask : TasksIntent

    data class RequestDelete(val task: Task) : TasksIntent

    data object ConfirmDelete : TasksIntent

    data object CancelDelete : TasksIntent

    /** Pulsacion larga sobre una tarea: entra en modo seleccion multiple con esa marcada. */
    data class EnterSelectionMode(val taskId: String) : TasksIntent

    data class ToggleTaskSelection(val taskId: String) : TasksIntent

    data object SelectAll : TasksIntent

    data object ExitSelectionMode : TasksIntent

    data object RequestBulkDelete : TasksIntent

    data object ConfirmBulkDelete : TasksIntent

    data object CancelBulkDelete : TasksIntent
}

sealed interface TasksEffect : UiEffect {
    data class ShowError(val message: String) : TasksEffect
}
