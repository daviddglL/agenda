package com.daviddelgado.agenda.feature.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daviddelgado.agenda.designsystem.component.AgendaDropdownField
import com.daviddelgado.agenda.designsystem.component.AgendaPrimaryButton
import com.daviddelgado.agenda.designsystem.component.AgendaTextField
import com.daviddelgado.agenda.domain.model.IncrementUnit
import com.daviddelgado.agenda.domain.model.ReminderFrequency
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.model.TaskCategory
import com.daviddelgado.agenda.domain.model.TaskPriority
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TasksScreen(viewModel: TasksViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Los errores de sincronizacion con el servidor son efectos de un solo uso (MVI).
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is TasksEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isSelectionMode) {
                SelectionTopBar(state = state, onIntent = viewModel::onIntent)
            }
        },
        floatingActionButton = {
            if (!state.isSelectionMode) {
                FloatingActionButton(onClick = { viewModel.onIntent(TasksIntent.OpenNewTaskForm) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Nueva tarea")
                }
            }
        },
    ) { padding ->
        TasksList(state = state, onIntent = viewModel::onIntent, contentPadding = padding)
    }

    TasksDialogs(state = state, onIntent = viewModel::onIntent)
}

/** Cabecera + lista (o estado vacio) de tareas del dia seleccionado. */
@Composable
private fun TasksList(
    state: TasksState,
    onIntent: (TasksIntent) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp)) {
        if (!state.isSelectionMode) {
            TasksHeader(isSyncing = state.isSyncing, onRefresh = { onIntent(TasksIntent.Refresh) })
        }

        if (state.tasks.isEmpty()) {
            Text(text = "No hay tareas para este dia", modifier = Modifier.padding(top = 24.dp))
        } else {
            LazyColumn {
                items(state.tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        isSelectionMode = state.isSelectionMode,
                        isSelected = task.id in state.selectedTaskIds,
                        onToggle = { onIntent(TasksIntent.ToggleCompleted(task.id)) },
                        onDelete = { onIntent(TasksIntent.RequestDelete(task)) },
                        onClick = {
                            if (state.isSelectionMode) {
                                onIntent(TasksIntent.ToggleTaskSelection(task.id))
                            } else {
                                onIntent(TasksIntent.OpenEditTaskForm(task))
                            }
                        },
                        onLongClick = { onIntent(TasksIntent.EnterSelectionMode(task.id)) },
                    )
                }
            }
        }
    }
}

/** Los tres dialogos de la pantalla (formulario, borrado simple, borrado conjunto). */
@Composable
private fun TasksDialogs(
    state: TasksState,
    onIntent: (TasksIntent) -> Unit,
) {
    if (state.isFormVisible) {
        TaskFormDialog(state = state, onIntent = onIntent)
    }

    state.taskPendingDelete?.let { task ->
        DeleteTaskDialog(
            task = task,
            onConfirm = { onIntent(TasksIntent.ConfirmDelete) },
            onCancel = { onIntent(TasksIntent.CancelDelete) },
        )
    }

    if (state.isBulkDeletePending) {
        BulkDeleteDialog(
            count = state.selectedCount,
            onConfirm = { onIntent(TasksIntent.ConfirmBulkDelete) },
            onCancel = { onIntent(TasksIntent.CancelBulkDelete) },
        )
    }
}

/** Titulo de la pantalla con el estado de la sincronizacion con el servidor. */
@Composable
private fun TasksHeader(
    isSyncing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Mis tareas", style = MaterialTheme.typography.headlineMedium)
        if (isSyncing) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Sincronizar con el servidor")
            }
        }
    }
}

/** Barra superior del modo de seleccion multiple, para el borrado conjunto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    state: TasksState,
    onIntent: (TasksIntent) -> Unit,
) {
    TopAppBar(
        title = { Text("${state.selectedCount} seleccionadas") },
        navigationIcon = {
            IconButton(onClick = { onIntent(TasksIntent.ExitSelectionMode) }) {
                Icon(Icons.Filled.Close, contentDescription = "Salir de la seleccion")
            }
        },
        actions = {
            IconButton(onClick = { onIntent(TasksIntent.SelectAll) }) {
                Icon(Icons.Filled.DoneAll, contentDescription = "Seleccionar todas")
            }
            IconButton(
                onClick = { onIntent(TasksIntent.RequestBulkDelete) },
                enabled = state.selectedCount > 0,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar seleccionadas")
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    task: Task,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            } else {
                Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
            }
            Column {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                if (task.description.isNotBlank()) Text(task.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!isSelectionMode) {
            TextButton(onClick = onDelete) { Text("Eliminar") }
        }
    }
}

/** Formulario de nueva tarea / edicion, con todos los campos del dominio (spec punto 2). */
@Composable
private fun TaskFormDialog(
    state: TasksState,
    onIntent: (TasksIntent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onIntent(TasksIntent.DismissForm) },
        title = { Text(if (state.editingTaskId == null) "Nueva tarea" else "Editar tarea") },
        text = {
            Column {
                AgendaTextField(state.formTitle, { onIntent(TasksIntent.FormTitleChanged(it)) }, "Titulo")
                AgendaTextField(
                    state.formDescription,
                    { onIntent(TasksIntent.FormDescriptionChanged(it)) },
                    "Descripcion",
                )
                AgendaDropdownField(
                    label = "Categoria",
                    options = TaskCategory.entries,
                    selected = state.formCategory,
                    optionLabel = { it.name },
                    onSelected = { onIntent(TasksIntent.FormCategoryChanged(it)) },
                )
                AgendaDropdownField(
                    label = "Prioridad",
                    options = TaskPriority.entries,
                    selected = state.formPriority,
                    optionLabel = { it.name },
                    onSelected = { onIntent(TasksIntent.FormPriorityChanged(it)) },
                )
                AgendaDropdownField(
                    label = "Recordatorio",
                    options = ReminderFrequency.entries,
                    selected = state.formReminderFrequency,
                    optionLabel = { it.name },
                    onSelected = { onIntent(TasksIntent.FormReminderChanged(it)) },
                )
                AgendaTextField(
                    value = state.formTime,
                    onValueChange = { onIntent(TasksIntent.FormTimeChanged(it)) },
                    label = "Hora (HH:mm, opcional)",
                )
                AgendaTextField(
                    value = state.formDurationMinutes,
                    onValueChange = { onIntent(TasksIntent.FormDurationChanged(it)) },
                    label = "Duracion en minutos (opcional)",
                    isNumeric = true,
                )
                IncrementSection(state = state, onIntent = onIntent)
                state.formError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { AgendaPrimaryButton(text = "Guardar", onClick = { onIntent(TasksIntent.SaveTask) }) },
        dismissButton = { TextButton(onClick = { onIntent(TasksIntent.DismissForm) }) { Text("Cancelar") } },
    )
}

/** Interruptor "es incremental" + sus campos (cantidad, cada cuanto, unidad) si esta activo. */
@Composable
private fun IncrementSection(
    state: TasksState,
    onIntent: (TasksIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Tarea incremental")
        Switch(
            checked = state.formIsIncremental,
            onCheckedChange = { onIntent(TasksIntent.FormIncrementToggled(it)) },
        )
    }
    if (state.formIsIncremental) {
        AgendaTextField(
            value = state.formIncrementAmount,
            onValueChange = { onIntent(TasksIntent.FormIncrementAmountChanged(it)) },
            label = "Cuanto se incrementa",
            isNumeric = true,
        )
        AgendaTextField(
            value = state.formIncrementEveryValue,
            onValueChange = { onIntent(TasksIntent.FormIncrementEveryValueChanged(it)) },
            label = "Cada cuanto (numero)",
            isNumeric = true,
        )
        AgendaDropdownField(
            label = "Unidad de la cadencia",
            options = IncrementUnit.entries,
            selected = state.formIncrementEveryUnit,
            optionLabel = { it.name },
            onSelected = { onIntent(TasksIntent.FormIncrementEveryUnitChanged(it)) },
        )
    }
}

@Composable
private fun DeleteTaskDialog(
    task: Task,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Eliminar tarea") },
        text = { Text("Seguro que quieres eliminar \"${task.title}\"?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
    )
}

@Composable
private fun BulkDeleteDialog(
    count: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Eliminar tareas") },
        text = { Text("Seguro que quieres eliminar $count tareas?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
    )
}
