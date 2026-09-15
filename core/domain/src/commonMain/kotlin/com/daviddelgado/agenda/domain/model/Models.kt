package com.daviddelgado.agenda.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class User(
    val id: String,
    val name: String,
    val email: String,
)

/** Categoria de la tarea, elegida por el usuario de un conjunto cerrado. */
enum class TaskCategory {
    TRABAJO,
    ESTUDIO,
    SALUD,
    PERSONAL,
    HOGAR,
    FINANZAS,
    OTRO,
}

/** Prioridad de la tarea, elegida por el usuario de un conjunto cerrado. */
enum class TaskPriority {
    ALTA,
    MEDIA,
    BAJA,
}

/** Cada cuanto se debe recordar la tarea al usuario. */
enum class ReminderFrequency {
    NINGUNO,
    UNA_VEZ,
    DIARIO,
    SEMANAL,
    MENSUAL,
    PERSONALIZADO,
}

/** Unidad del intervalo entre repeticiones de una tarea incremental (ver [IncrementConfig]). */
enum class IncrementUnit {
    DIAS,
    SEMANAS,
    MESES,
}

/**
 * Configuracion de una tarea "incremental": al crearla se generan copias adicionales de la
 * tarea, repitiendo sus caracteristicas basicas. `amount` es cuantas copias repetir;
 * `everyValue`+`everyUnit` es el intervalo entre cada copia (ver
 * `GenerateTaskRepetitionsUseCase`).
 */
data class IncrementConfig(
    val amount: Int,
    val everyValue: Int,
    val everyUnit: IncrementUnit,
)

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val date: LocalDate,
    val time: LocalTime? = null,
    val durationMinutes: Int? = null,
    val category: TaskCategory = TaskCategory.OTRO,
    val priority: TaskPriority = TaskPriority.MEDIA,
    val reminderFrequency: ReminderFrequency = ReminderFrequency.NINGUNO,
    val increment: IncrementConfig? = null,
    val isCompleted: Boolean = false,
) {
    val isIncremental: Boolean get() = increment != null
}

data class StreakSummary(
    val currentStreak: Int,
    val bestStreak: Int,
    val completedDates: List<LocalDate>,
)
