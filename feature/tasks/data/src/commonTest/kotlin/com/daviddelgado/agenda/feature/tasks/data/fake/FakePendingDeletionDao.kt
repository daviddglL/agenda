package com.daviddelgado.agenda.feature.tasks.data.fake

import com.daviddelgado.agenda.feature.tasks.database.dao.PendingDeletionDao
import com.daviddelgado.agenda.feature.tasks.database.entity.PendingDeletionEntity

/** PendingDeletionDao en memoria: reproduce los tombstones de borrado sin red. */
class FakePendingDeletionDao(initial: List<String> = emptyList()) : PendingDeletionDao {
    private val ids = initial.toMutableSet()

    val pendingIds: Set<String> get() = ids.toSet()

    override suspend fun getAll(): List<PendingDeletionEntity> = ids.map { PendingDeletionEntity(it) }

    override suspend fun upsertAll(entities: List<PendingDeletionEntity>) {
        ids += entities.map { it.id }
    }

    override suspend fun deleteByIds(ids: List<String>) {
        this.ids -= ids.toSet()
    }

    override suspend fun deleteAll() {
        ids.clear()
    }
}
