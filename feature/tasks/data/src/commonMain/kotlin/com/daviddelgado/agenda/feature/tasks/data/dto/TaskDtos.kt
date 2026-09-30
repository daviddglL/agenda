package com.daviddelgado.agenda.feature.tasks.data.dto

import kotlinx.serialization.Serializable

/**
 * Contrato de red de una tarea. Debe mantenerse identico al `TaskDto` del modulo :server
 * (ver server/src/main/kotlin/.../dto/Dtos.kt). `date`/`time` viajan como ISO-8601
 * ("yyyy-MM-dd" / "HH:mm") y los enums como su `.name`, para no atar el contrato a los
 * tipos de fecha de ninguna plataforma.
 */
@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val description: String = "",
    val date: String,
    val time: String? = null,
    val durationMinutes: Int? = null,
    val category: String,
    val priority: String,
    val reminderFrequency: String,
    val incrementAmount: Int? = null,
    val incrementEveryValue: Int? = null,
    val incrementEveryUnit: String? = null,
    val isCompleted: Boolean = false,
)

@Serializable
data class BulkDeleteRequest(val ids: List<String>)

@Serializable
data class DeletedCountResponse(val deleted: Int)
