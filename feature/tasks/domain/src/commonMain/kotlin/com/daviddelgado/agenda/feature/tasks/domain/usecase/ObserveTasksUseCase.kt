package com.daviddelgado.agenda.feature.tasks.domain.usecase

import com.daviddelgado.agenda.feature.tasks.domain.model.Task
import com.daviddelgado.agenda.feature.tasks.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

class ObserveTasksUseCase(private val repository: TaskRepository) {
    operator fun invoke(date: LocalDate? = null): Flow<List<Task>> = repository.observeTasks(date)
}
