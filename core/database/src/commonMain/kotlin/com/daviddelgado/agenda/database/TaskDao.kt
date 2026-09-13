package com.daviddelgado.agenda.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        "SELECT * FROM tasks WHERE (:dateEpochDay IS NULL OR dateEpochDay = :dateEpochDay) " +
            "ORDER BY timeMinuteOfDay ASC",
    )
    fun observeTasks(dateEpochDay: Long?): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT DISTINCT dateEpochDay FROM tasks WHERE isCompleted = 1 ORDER BY dateEpochDay DESC")
    fun observeCompletedDateEpochDays(): Flow<List<Long>>

    /** Tareas creadas/editadas sin red, pendientes de subir al servidor. */
    @Query("SELECT * FROM tasks WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<TaskEntity>

    /** Ids de todas las tareas locales; se usa antes de un borrado total para dejar tombstones. */
    @Query("SELECT id FROM tasks")
    suspend fun getAllIds(): List<String>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Query("UPDATE tasks SET pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /**
     * Limpia las tareas que el servidor ya no tiene, respetando las que aun no se han
     * podido subir (`pendingSync = 1`), que se perderian si se borrasen aqui.
     */
    @Query("DELETE FROM tasks WHERE pendingSync = 0 AND id NOT IN (:remoteIds)")
    suspend fun deleteSyncedNotIn(remoteIds: List<String>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("UPDATE tasks SET isCompleted = NOT isCompleted, pendingSync = 1 WHERE id = :id")
    suspend fun toggleCompleted(id: String)

    @Delete
    suspend fun delete(task: TaskEntity)
}
