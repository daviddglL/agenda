package com.daviddelgado.agenda.feature.tasks.data.mapper

import com.daviddelgado.agenda.feature.tasks.database.entity.TaskEntity
import com.daviddelgado.agenda.feature.tasks.domain.model.IncrementConfig
import com.daviddelgado.agenda.feature.tasks.domain.model.IncrementUnit
import com.daviddelgado.agenda.feature.tasks.domain.model.ReminderFrequency
import com.daviddelgado.agenda.feature.tasks.domain.model.Task
import com.daviddelgado.agenda.feature.tasks.domain.model.TaskCategory
import com.daviddelgado.agenda.feature.tasks.domain.model.TaskPriority
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

fun TaskEntity.toDomain(): Task =
    Task(
        id = id,
        title = title,
        description = description,
        date = LocalDate.fromEpochDays(dateEpochDay.toInt()),
        time = timeMinuteOfDay?.let { LocalTime(it / 60, it % 60) },
        durationMinutes = durationMinutes,
        category = TaskCategory.valueOf(category),
        priority = TaskPriority.valueOf(priority),
        reminderFrequency = ReminderFrequency.valueOf(reminderFrequency),
        increment =
            run {
                val amount = incrementAmount
                val everyValue = incrementEveryValue
                val everyUnit = incrementEveryUnit
                when {
                    amount != null && everyValue != null && everyUnit != null ->
                        IncrementConfig(
                            amount = amount,
                            everyValue = everyValue,
                            everyUnit = IncrementUnit.valueOf(everyUnit),
                        )
                    else -> null
                }
            },
        isCompleted = isCompleted,
    )

/** @param pendingSync true si la tarea todavia no se ha podido subir al servidor. */
fun Task.toEntity(pendingSync: Boolean = false): TaskEntity =
    TaskEntity(
        id = id,
        title = title,
        description = description,
        dateEpochDay = date.toEpochDays().toLong(),
        timeMinuteOfDay = time?.let { it.hour * 60 + it.minute },
        durationMinutes = durationMinutes,
        category = category.name,
        priority = priority.name,
        reminderFrequency = reminderFrequency.name,
        incrementAmount = increment?.amount,
        incrementEveryValue = increment?.everyValue,
        incrementEveryUnit = increment?.everyUnit?.name,
        isCompleted = isCompleted,
        pendingSync = pendingSync,
    )
