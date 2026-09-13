package com.daviddelgado.agenda.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

const val DATABASE_FILE_NAME = "agenda.db"

/**
 * El constructor difiere por plataforma (Android necesita `Context`, iOS no), pero el metodo
 * `create()` es comun. Koin inyecta el `Context` en Android via `androidContext()`.
 */
expect class DatabaseFactory {
    fun create(): RoomDatabase.Builder<AgendaDatabase>
}

fun buildAgendaDatabase(factory: DatabaseFactory): AgendaDatabase =
    factory.create()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigration(dropAllTables = false)
        .build()
