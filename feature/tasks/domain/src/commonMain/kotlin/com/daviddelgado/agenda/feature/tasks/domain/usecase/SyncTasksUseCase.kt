package com.daviddelgado.agenda.feature.tasks.domain.usecase

import com.daviddelgado.agenda.feature.tasks.domain.repository.TaskRepository

/** Fuerza una sincronizacion con el servidor (arranque de pantalla o gesto de refrescar). */
class SyncTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.syncTasks()
}
