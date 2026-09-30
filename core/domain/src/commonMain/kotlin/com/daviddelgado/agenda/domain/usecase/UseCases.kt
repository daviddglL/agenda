package com.daviddelgado.agenda.domain.usecase

import com.daviddelgado.agenda.domain.model.IncrementUnit
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class ObserveTasksUseCase(private val repository: TaskRepository) {
    operator fun invoke(date: LocalDate? = null): Flow<List<Task>> = repository.observeTasks(date)
}

class UpsertTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task) = repository.upsertTask(task)
}

/**
 * Al crear una tarea "incremental" genera sus copias adicionales: [Task.increment]!!.amount
 * tareas nuevas, cada una `everyValue` `everyUnit` despues de la anterior, repitiendo las
 * caracteristicas basicas de la original (titulo, descripcion, hora, duracion, categoria,
 * prioridad, recordatorio). La tarea original no se incluye en el resultado; cada copia
 * llega sin completar y sin su propia configuracion de incremento (para no volver a generar
 * copias de una copia). No genera nada si la tarea no tiene [Task.increment].
 */
class GenerateTaskRepetitionsUseCase {
    operator fun invoke(
        task: Task,
        generateId: () -> String,
    ): List<Task> {
        val config = task.increment ?: return emptyList()
        val unit = config.everyUnit.toDateTimeUnit()
        return (1..config.amount).map { index ->
            task.copy(
                id = generateId(),
                date = task.date.plus(config.everyValue * index, unit),
                isCompleted = false,
                increment = null,
            )
        }
    }

    private fun IncrementUnit.toDateTimeUnit(): DateTimeUnit.DateBased =
        when (this) {
            IncrementUnit.DIAS -> DateTimeUnit.DAY
            IncrementUnit.SEMANAS -> DateTimeUnit.WEEK
            IncrementUnit.MESES -> DateTimeUnit.MONTH
        }
}

class DeleteTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: String) = repository.deleteTask(taskId)
}

/** Borrado conjunto: varias tareas elegidas a la vez (p.ej. seleccion multiple en la UI). */
class DeleteTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskIds: List<String>) = repository.deleteTasks(taskIds)
}

class DeleteAllTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke() = repository.deleteAllTasks()
}

class ToggleTaskCompletionUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: String) = repository.toggleCompleted(taskId)
}

/** Fuerza una sincronizacion con el servidor (arranque de pantalla o gesto de refrescar). */
class SyncTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.syncTasks()
}

/** Avisos en tiempo real de cambios remotos (WebSocket); cada emision pide un [SyncTasksUseCase]. */
class ObserveTaskChangesUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<Unit> = repository.observeRemoteChanges()
}
