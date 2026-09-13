package com.daviddelgado.agenda.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

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
