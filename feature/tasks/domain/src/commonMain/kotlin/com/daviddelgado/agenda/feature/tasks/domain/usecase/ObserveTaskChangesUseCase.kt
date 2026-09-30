package com.daviddelgado.agenda.feature.tasks.domain.usecase

import com.daviddelgado.agenda.feature.tasks.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

/** Avisos en tiempo real de cambios remotos (WebSocket); cada emision pide un [SyncTasksUseCase]. */
class ObserveTaskChangesUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<Unit> = repository.observeRemoteChanges()
}
