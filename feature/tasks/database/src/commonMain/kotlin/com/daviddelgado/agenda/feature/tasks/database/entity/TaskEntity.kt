package com.daviddelgado.agenda.feature.tasks.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistencia local (offline-first) de una tarea. Los enums de dominio (categoria,
 * prioridad, recordatorio, unidad de incremento) se guardan como su `.name` en texto
 * para no necesitar TypeConverters ni arrastrar tipos de :feature:tasks:domain en :feature:tasks:database.
 *
 * [pendingSync] marca las tareas creadas o editadas sin red: la sincronizacion las sube
 * al servidor en el siguiente intento y solo entonces las marca como sincronizadas.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val durationMinutes: Int?,
    val category: String,
    val priority: String,
    val reminderFrequency: String,
    val incrementAmount: Int?,
    val incrementEveryValue: Int?,
    val incrementEveryUnit: String?,
    val isCompleted: Boolean,
    val pendingSync: Boolean = false,
)
