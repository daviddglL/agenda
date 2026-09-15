package com.daviddelgado.agenda.data.task

import com.daviddelgado.agenda.domain.model.IncrementConfig
import com.daviddelgado.agenda.domain.model.IncrementUnit
import com.daviddelgado.agenda.domain.model.ReminderFrequency
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.model.TaskCategory
import com.daviddelgado.agenda.domain.model.TaskPriority
import com.daviddelgado.agenda.network.dto.TaskDto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Puente entre el modelo de dominio y el contrato de red del modulo :server.
 * Las fechas viajan como ISO-8601 ("yyyy-MM-dd" / "HH:mm") y los enums como su `.name`;
 * un valor desconocido (servidor mas nuevo que el cliente) cae al valor por defecto en
 * lugar de romper la deserializacion de toda la lista.
 */
fun Task.toDto(): TaskDto =
    TaskDto(
        id = id,
        title = title,
        description = description,
        date = date.toString(),
        time = time?.toString(),
        durationMinutes = durationMinutes,
        category = category.name,
        priority = priority.name,
        reminderFrequency = reminderFrequency.name,
        incrementAmount = increment?.amount,
        incrementEveryValue = increment?.everyValue,
        incrementEveryUnit = increment?.everyUnit?.name,
        isCompleted = isCompleted,
    )

fun TaskDto.toDomain(): Task =
    Task(
        id = id,
        title = title,
        description = description,
        date = LocalDate.parse(date),
        time = time?.let { LocalTime.parse(it) },
        durationMinutes = durationMinutes,
        category = enumOrDefault(category, TaskCategory.entries, TaskCategory.OTRO),
        priority = enumOrDefault(priority, TaskPriority.entries, TaskPriority.MEDIA),
        reminderFrequency =
            enumOrDefault(reminderFrequency, ReminderFrequency.entries, ReminderFrequency.NINGUNO),
        increment = toIncrementConfig(),
        isCompleted = isCompleted,
    )

private fun TaskDto.toIncrementConfig(): IncrementConfig? {
    val amount = incrementAmount ?: return null
    val everyValue = incrementEveryValue ?: return null
    val everyUnit = incrementEveryUnit ?: return null
    return IncrementConfig(
        amount = amount,
        everyValue = everyValue,
        everyUnit = enumOrDefault(everyUnit, IncrementUnit.entries, IncrementUnit.DIAS),
    )
}

private fun <E : Enum<E>> enumOrDefault(
    name: String,
    values: List<E>,
    default: E,
): E = values.firstOrNull { it.name == name } ?: default
