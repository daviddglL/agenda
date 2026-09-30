package com.daviddelgado.agenda.feature.tasks.domain.usecase

import com.daviddelgado.agenda.feature.tasks.domain.model.IncrementUnit
import com.daviddelgado.agenda.feature.tasks.domain.model.Task
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

/**
 * Al crear una tarea "incremental" genera sus copias adicionales: [Task.increment]!!.amount
 * tareas nuevas, cada una `everyValue` `everyUnit` despues de la anterior, repitiendo las
 * caracteristicas basicas de la original (titulo, descripcion, hora, duracion, categoria,
 * prioridad, recordatorio). La tarea original no se incluye en el resultado; cada copia
 * llega sin completar y sin su propia configuracion de incremento (para no volver a generar
 * copias de una copia). No genera nada si la tarea no tiene [Task.increment].
 */
class GenerateTaskRepetitionsUseCase {
    operator fun invoke(
        task: Task,
        generateId: () -> String,
    ): List<Task> {
        val config = task.increment ?: return emptyList()
        val unit = config.everyUnit.toDateTimeUnit()
        return (1..config.amount).map { index ->
            task.copy(
                id = generateId(),
                date = task.date.plus(config.everyValue * index, unit),
                isCompleted = false,
                increment = null,
            )
        }
    }

    private fun IncrementUnit.toDateTimeUnit(): DateTimeUnit.DateBased =
        when (this) {
            IncrementUnit.DIAS -> DateTimeUnit.DAY
            IncrementUnit.SEMANAS -> DateTimeUnit.WEEK
            IncrementUnit.MESES -> DateTimeUnit.MONTH
        }
}
