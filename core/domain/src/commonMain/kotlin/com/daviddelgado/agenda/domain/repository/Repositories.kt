package com.daviddelgado.agenda.domain.repository

import com.daviddelgado.agenda.domain.model.StreakSummary
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Unica Fuente de Verdad (SSOT) para tareas. La implementacion en :core:data combina
 * Room (offline-first) y Ktor (sincronizacion remota) segun el punto 2 de markdown.md.
 */
interface TaskRepository {
    fun observeTasks(date: LocalDate? = null): Flow<List<Task>>

    suspend fun getTask(id: String): Task?

    suspend fun upsertTask(task: Task)

    suspend fun deleteTask(id: String)

    suspend fun deleteTasks(ids: List<String>)

    suspend fun deleteAllTasks()

    suspend fun toggleCompleted(id: String)

    /**
     * Sincroniza con el backend: sube lo pendiente y baja lo del servidor a la base local.
     * Devuelve `Result.failure` si no hay red o el servidor responde con error; la app
     * sigue funcionando con los datos locales (offline-first).
     */
    suspend fun syncTasks(): Result<Unit>

    /**
     * Avisos en tiempo real (punto 3 de markdown.md) de que las tareas de este usuario han
     * cambiado en el servidor, p.ej. desde otro dispositivo. Cada emision es solo la senal
     * de "hay algo nuevo"; quien recoja el flujo debe llamar a [syncTasks] para bajarlo.
     * El propio flujo reintenta la conexion si se corta, asi que no completa nunca por si solo.
     */
    fun observeRemoteChanges(): Flow<Unit>
}

interface StreakRepository {
    fun observeStreak(): Flow<StreakSummary>
}

interface AuthRepository {
    fun observeCurrentUser(): Flow<User?>

    suspend fun login(
        email: String,
        password: String,
    ): Result<User>

    suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Result<User>

    suspend fun logout()

    /**
     * Recupera la sesion guardada al arrancar la app (token en almacenamiento cifrado).
     * Devuelve null si no habia token o si el servidor ya no lo acepta.
     */
    suspend fun restoreSession(): User?

    /** Borra la cuenta y, en cascada (servidor + local), todas sus tareas. */
    suspend fun deleteAccount(): Result<Unit>
}
