package com.daviddelgado.agenda.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PendingDeletionDao {
    @Query("SELECT * FROM pending_deletions")
    suspend fun getAll(): List<PendingDeletionEntity>

    @Upsert
    suspend fun upsertAll(entities: List<PendingDeletionEntity>)

    @Query("DELETE FROM pending_deletions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM pending_deletions")
    suspend fun deleteAll()
}
