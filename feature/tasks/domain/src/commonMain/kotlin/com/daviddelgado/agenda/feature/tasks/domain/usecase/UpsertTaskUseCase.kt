package com.daviddelgado.agenda.feature.tasks.domain.usecase

import com.daviddelgado.agenda.feature.tasks.domain.model.Task
import com.daviddelgado.agenda.feature.tasks.domain.repository.TaskRepository

class UpsertTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task) = repository.upsertTask(task)
}
