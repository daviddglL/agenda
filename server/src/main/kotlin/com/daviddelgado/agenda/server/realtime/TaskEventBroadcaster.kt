package com.daviddelgado.agenda.server.realtime

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Comunicacion en tiempo real (punto 3 de markdown.md): cuando las tareas de un usuario
 * cambian en el servidor (por otro dispositivo, por ejemplo), este objeto avisa por
 * WebSocket a todas las sesiones conectadas de ESE usuario para que refresquen. El mensaje
 * es solo una senal ("tasks_changed"); el cliente responde con su `syncTasks()` habitual,
 * que ya sabe subir lo pendiente y bajar lo nuevo — no hace falta duplicar ese contrato aqui.
 */
object TaskEventBroadcaster {
    private const val TASKS_CHANGED_MESSAGE = "tasks_changed"

    private val sessionsByUser = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val mutex = Mutex()

    suspend fun register(
        userId: String,
        session: WebSocketSession,
    ) {
        mutex.withLock {
            sessionsByUser.getOrPut(userId) { mutableSetOf() }.add(session)
        }
    }

    suspend fun unregister(
        userId: String,
        session: WebSocketSession,
    ) {
        mutex.withLock {
            sessionsByUser[userId]?.remove(session)
        }
    }

    /** Avisa a todas las sesiones abiertas de [userId]; una sesion que ya no responde se ignora. */
    suspend fun notifyTasksChanged(userId: String) {
        val sessions = mutex.withLock { sessionsByUser[userId]?.toList() } ?: return
        sessions.forEach { session ->
            runCatching { session.send(Frame.Text(TASKS_CHANGED_MESSAGE)) }
        }
    }
}
