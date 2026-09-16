package com.daviddelgado.agenda.domain.usecase

import com.daviddelgado.agenda.domain.model.IncrementUnit
import com.daviddelgado.agenda.domain.model.StreakSummary
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.model.User
import com.daviddelgado.agenda.domain.repository.AuthRepository
import com.daviddelgado.agenda.domain.repository.StreakRepository
import com.daviddelgado.agenda.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class ObserveTasksUseCase(private val repository: TaskRepository) {
    operator fun invoke(date: LocalDate? = null): Flow<List<Task>> = repository.observeTasks(date)
}

class UpsertTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task) = repository.upsertTask(task)
}

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

class DeleteTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: String) = repository.deleteTask(taskId)
}

/** Borrado conjunto: varias tareas elegidas a la vez (p.ej. seleccion multiple en la UI). */
class DeleteTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskIds: List<String>) = repository.deleteTasks(taskIds)
}

class DeleteAllTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke() = repository.deleteAllTasks()
}

class ToggleTaskCompletionUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: String) = repository.toggleCompleted(taskId)
}

/** Fuerza una sincronizacion con el servidor (arranque de pantalla o gesto de refrescar). */
class SyncTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.syncTasks()
}

/** Avisos en tiempo real de cambios remotos (WebSocket); cada emision pide un [SyncTasksUseCase]. */
class ObserveTaskChangesUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<Unit> = repository.observeRemoteChanges()
}

class ObserveStreakUseCase(private val repository: StreakRepository) {
    operator fun invoke(): Flow<StreakSummary> = repository.observeStreak()
}

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(
        email: String,
        password: String,
    ): Result<User> = repository.login(email, password)
}

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(
        name: String,
        email: String,
        password: String,
    ): Result<User> = repository.register(name, email, password)
}

/** Sesion guardada al abrir la app: evita pedir login en cada arranque. */
class RestoreSessionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): User? = repository.restoreSession()
}

/** Usuario con sesion iniciada ahora mismo (null si no hay sesion), para pantallas como Ajustes. */
class ObserveCurrentUserUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Flow<User?> = repository.observeCurrentUser()
}

/** Cierra la sesion: borra tokens y datos locales del usuario. */
class LogoutUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.logout()
}

/** Borra la cuenta y, en cascada, todas sus tareas (servidor + Room local). */
class DeleteAccountUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.deleteAccount()
}

/** Registra en el servidor el token FCM de este dispositivo para recibir recordatorios push. */
class RegisterFcmTokenUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(token: String): Result<Unit> = repository.registerFcmToken(token)
}
