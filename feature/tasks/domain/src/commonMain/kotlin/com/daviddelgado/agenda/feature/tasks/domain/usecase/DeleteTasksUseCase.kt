package com.daviddelgado.agenda.feature.tasks.domain.usecase

import com.daviddelgado.agenda.feature.tasks.domain.repository.TaskRepository

/** Borrado conjunto: varias tareas elegidas a la vez (p.ej. seleccion multiple en la UI). */
class DeleteTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskIds: List<String>) = repository.deleteTasks(taskIds)
}
