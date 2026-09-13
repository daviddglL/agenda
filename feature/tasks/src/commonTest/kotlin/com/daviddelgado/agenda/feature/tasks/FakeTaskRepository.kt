package com.daviddelgado.agenda.feature.tasks

import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** TaskRepository en memoria para probar el reductor MVI sin Room ni servidor. */
class FakeTaskRepository(initial: List<Task> = emptyList()) : TaskRepository {
    val tasks = MutableStateFlow(initial)
    var syncCount = 0
        private set
    var syncResult: Result<Unit> = Result.success(Unit)

    /** Los tests emiten aqui para simular un aviso "tasks_changed" del WebSocket. */
    val remoteChanges = MutableSharedFlow<Unit>()

    override fun observeTasks(date: LocalDate?): Flow<List<Task>> =
        tasks.map { list -> if (date == null) list else list.filter { it.date == date } }

    override suspend fun getTask(id: String): Task? = tasks.value.firstOrNull { it.id == id }

    override suspend fun upsertTask(task: Task) {
        tasks.value = tasks.value.filterNot { it.id == task.id } + task
    }

    override suspend fun deleteTask(id: String) {
        tasks.value = tasks.value.filterNot { it.id == id }
    }

    override suspend fun deleteTasks(ids: List<String>) {
        tasks.value = tasks.value.filterNot { it.id in ids }
    }

    override suspend fun deleteAllTasks() {
        tasks.value = emptyList()
    }

    override suspend fun toggleCompleted(id: String) {
        tasks.value = tasks.value.map { if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it }
    }

    override suspend fun syncTasks(): Result<Unit> {
        syncCount++
        return syncResult
    }

    override fun observeRemoteChanges(): Flow<Unit> = remoteChanges
}
