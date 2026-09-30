package com.daviddelgado.agenda.feature.auth.data.fake

import com.daviddelgado.agenda.database.TaskDao
import com.daviddelgado.agenda.database.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * TaskDao en memoria: reproduce el comportamiento de las consultas de Room (filtro por
 * fecha, `pendingSync`, borrados conjuntos) sin necesitar un dispositivo ni SQLite, para
 * poder testear los repositorios de :core:data en codigo comun.
 */
class FakeTaskDao(initial: List<TaskEntity> = emptyList()) : TaskDao {
    private val rows = MutableStateFlow(initial.associateBy { it.id })

    val all: List<TaskEntity> get() = rows.value.values.toList()

    fun byId(id: String): TaskEntity? = rows.value[id]

    override fun observeTasks(dateEpochDay: Long?): Flow<List<TaskEntity>> =
        rows.map { current ->
            current.values
                .filter { dateEpochDay == null || it.dateEpochDay == dateEpochDay }
                .sortedBy { it.timeMinuteOfDay }
        }

    override suspend fun getById(id: String): TaskEntity? = rows.value[id]

    override fun observeCompletedDateEpochDays(): Flow<List<Long>> =
        rows.map { current ->
            current.values.filter { it.isCompleted }.map { it.dateEpochDay }.distinct().sortedDescending()
        }

    override suspend fun getPendingSync(): List<TaskEntity> = rows.value.values.filter { it.pendingSync }

    override suspend fun getAllIds(): List<String> = rows.value.keys.toList()

    override suspend fun upsert(task: TaskEntity) {
        rows.value = rows.value + (task.id to task)
    }

    override suspend fun upsertAll(tasks: List<TaskEntity>) {
        rows.value = rows.value + tasks.associateBy { it.id }
    }

    override suspend fun markSynced(id: String) {
        val row = rows.value[id] ?: return
        rows.value = rows.value + (id to row.copy(pendingSync = false))
    }

    override suspend fun deleteById(id: String) {
        rows.value = rows.value - id
    }

    override suspend fun deleteByIds(ids: List<String>) {
        rows.value = rows.value - ids.toSet()
    }

    override suspend fun deleteSyncedNotIn(remoteIds: List<String>) {
        rows.value = rows.value.filterValues { it.pendingSync || it.id in remoteIds }
    }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }

    override suspend fun toggleCompleted(id: String) {
        val row = rows.value[id] ?: return
        rows.value = rows.value + (id to row.copy(isCompleted = !row.isCompleted, pendingSync = true))
    }

    override suspend fun delete(task: TaskEntity) {
        rows.value = rows.value - task.id
    }
}
