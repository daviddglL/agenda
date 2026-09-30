package com.daviddelgado.agenda.feature.tasks.domain.usecase

import com.daviddelgado.agenda.feature.tasks.domain.repository.TaskRepository

class ToggleTaskCompletionUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: String) = repository.toggleCompleted(taskId)
}
