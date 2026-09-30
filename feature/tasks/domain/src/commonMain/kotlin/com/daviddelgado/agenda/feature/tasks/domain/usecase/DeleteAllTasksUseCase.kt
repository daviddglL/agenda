package com.daviddelgado.agenda.feature.tasks.domain.usecase

import com.daviddelgado.agenda.feature.tasks.domain.repository.TaskRepository

class DeleteAllTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke() = repository.deleteAllTasks()
}
