package com.daviddelgado.agenda.data.task

import com.daviddelgado.agenda.database.PendingDeletionDao
import com.daviddelgado.agenda.database.PendingDeletionEntity
import com.daviddelgado.agenda.database.TaskDao
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.domain.repository.TaskRepository
import com.daviddelgado.agenda.network.api.TaskApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** Espera antes de reintentar el WebSocket de tiempo real si la conexion se corta o falla. */
private const val REALTIME_RECONNECT_DELAY_MILLIS = 5_000L

/**
 * Room es la Unica Fuente de Verdad (SSOT, offline-first): toda lectura viene de local y la
 * UI nunca espera a la red. Cada escritura se guarda primero en Room marcada como pendiente
 * (`pendingSync`) y despues se intenta empujar al servidor; si no hay red, la tarea se queda
 * pendiente y [syncTasks] la sube en el siguiente intento.
 *
 * Los borrados sin red dejan un "tombstone" en [pendingDeletionDao]: sin el, una tarea
 * borrada offline podia reaparecer en la siguiente sincronizacion porque el `GET /tasks`
 * del servidor seguia devolviendola (era una limitacion documentada; ya no aplica).
 */
class TaskRepositoryImpl(
    private val dao: TaskDao,
    private val pendingDeletionDao: PendingDeletionDao,
    private val api: TaskApi,
) : TaskRepository {
    override fun observeTasks(date: LocalDate?): Flow<List<Task>> =
        dao.observeTasks(date?.toEpochDays()?.toLong()).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTask(id: String): Task? = dao.getById(id)?.toDomain()

    override suspend fun upsertTask(task: Task) {
        val isNew = dao.getById(task.id) == null
        dao.upsert(task.toEntity(pendingSync = true))
        runCatching {
            if (isNew) api.create(task.toDto()) else api.update(task.toDto())
        }.onSuccess { dao.markSynced(task.id) }
    }

    override suspend fun deleteTask(id: String) = deleteWithTombstone(listOf(id)) { api.delete(id) }

    override suspend fun deleteTasks(ids: List<String>) = deleteWithTombstone(ids) { api.bulkDelete(ids) }

    override suspend fun deleteAllTasks() {
        val ids = dao.getAllIds()
        deleteWithTombstone(ids) { api.deleteAll() }
    }

    /**
     * Borra localmente ya (la UI no debe esperar a la red) y dejar un tombstone por si el
     * borrado remoto falla; si tiene exito se retira el tombstone en el momento, si no lo
     * retira [syncTasks] en el siguiente intento.
     */
    private suspend fun deleteWithTombstone(
        ids: List<String>,
        remoteDelete: suspend () -> Unit,
    ) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
        pendingDeletionDao.upsertAll(ids.map { PendingDeletionEntity(it) })
        runCatching { remoteDelete() }.onSuccess { pendingDeletionDao.deleteByIds(ids) }
    }

    override suspend fun toggleCompleted(id: String) {
        dao.toggleCompleted(id)
        runCatching { api.toggleCompleted(id) }.onSuccess { dao.markSynced(id) }
    }

    override suspend fun syncTasks(): Result<Unit> =
        runCatching {
            pushPendingDeletions()
            pushPending()
            pullRemote()
        }

    /** Reintenta los borrados que no se pudieron confirmar en el servidor la ultima vez. */
    private suspend fun pushPendingDeletions() {
        val pending = pendingDeletionDao.getAll().map { it.id }
        if (pending.isEmpty()) return
        api.bulkDelete(pending)
        pendingDeletionDao.deleteByIds(pending)
    }

    /** Sube lo creado o editado sin red. `create` es idempotente por id en el servidor. */
    private suspend fun pushPending() {
        dao.getPendingSync().forEach { entity ->
            api.create(entity.toDomain().toDto())
            dao.markSynced(entity.id)
        }
    }

    /** Baja el estado del servidor a Room sin tocar lo que aun esta pendiente de subir. */
    private suspend fun pullRemote() {
        val remote = api.getAll()
        dao.upsertAll(remote.map { it.toDomain().toEntity(pendingSync = false) })
        dao.deleteSyncedNotIn(remote.map { it.id })
    }

    /**
     * El WebSocket de [TaskApi.observeChanges] termina en cuanto se corta la conexion (sin
     * red, servidor reiniciado...); aqui se envuelve en un bucle que reconecta pasado
     * [REALTIME_RECONNECT_DELAY_MILLIS] en lugar de dejar el flujo muerto para siempre.
     */
    override fun observeRemoteChanges(): Flow<Unit> =
        flow {
            while (true) {
                runCatching { api.observeChanges().collect { emit(Unit) } }
                delay(REALTIME_RECONNECT_DELAY_MILLIS)
            }
        }
}
