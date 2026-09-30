package com.daviddelgado.agenda.feature.tasks.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.daviddelgado.agenda.feature.tasks.database.dao.PendingDeletionDao
import com.daviddelgado.agenda.feature.tasks.database.dao.TaskDao
import com.daviddelgado.agenda.feature.tasks.database.entity.PendingDeletionEntity
import com.daviddelgado.agenda.feature.tasks.database.entity.TaskEntity

@Database(entities = [TaskEntity::class, PendingDeletionEntity::class], version = 4, exportSchema = true)
@ConstructedBy(AgendaDatabaseConstructor::class)
abstract class AgendaDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun pendingDeletionDao(): PendingDeletionDao
}

/**
 * El procesador KSP de Room genera la implementacion `actual` de este objeto para cada
 * plataforma (Android/iOS) a partir del `expect` declarado aqui. No requiere codigo manual.
 */
expect object AgendaDatabaseConstructor : RoomDatabaseConstructor<AgendaDatabase> {
    override fun initialize(): AgendaDatabase
}
