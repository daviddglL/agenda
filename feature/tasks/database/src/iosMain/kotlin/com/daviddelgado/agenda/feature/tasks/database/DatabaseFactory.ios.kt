package com.daviddelgado.agenda.feature.tasks.database

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

actual class DatabaseFactory {
    actual fun create(): RoomDatabase.Builder<AgendaDatabase> {
        val dbFilePath = NSHomeDirectory() + "/$DATABASE_FILE_NAME"
        return Room.databaseBuilder<AgendaDatabase>(name = dbFilePath)
    }
}
