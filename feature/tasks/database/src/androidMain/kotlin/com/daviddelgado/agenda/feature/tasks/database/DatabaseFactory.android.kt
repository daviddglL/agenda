package com.daviddelgado.agenda.feature.tasks.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class DatabaseFactory(private val context: Context) {
    actual fun create(): RoomDatabase.Builder<AgendaDatabase> {
        val dbFile = context.getDatabasePath(DATABASE_FILE_NAME)
        return Room.databaseBuilder<AgendaDatabase>(context = context, name = dbFile.absolutePath)
    }
}
