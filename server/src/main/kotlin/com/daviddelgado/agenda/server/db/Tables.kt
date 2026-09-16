package com.daviddelgado.agenda.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.javatime.timestamp

/** Un usuario y sus credenciales. El borrado en cascada de sus tareas se define en [Tasks]. */
object Users : Table("users") {
    val id = varchar("id", 36)
    val name = varchar("name", 120)
    val email = varchar("email", 190).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val tokenVersion = integer("token_version").default(0)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Una tarea, con todos los campos pedidos: titulo, descripcion, fecha, hora, duracion,
 * categoria y prioridad (a elegir de un catalogo cerrado, ver enums de dominio),
 * frecuencia de recordatorio, y configuracion de incremento (si es incremental, cuanto
 * se incrementa y cada cuanto). `userId` referencia [Users.id] con ON DELETE CASCADE:
 * borrar el usuario borra automaticamente todas sus tareas de forma conjunta.
 */
object Tasks : Table("tasks") {
    val id = varchar("id", 36)
    val userId =
        varchar("user_id", 36)
            .references(Users.id, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val title = varchar("title", 200)
    val description = text("description").default("")
    val date = date("date")
    val time = time("time").nullable()
    val durationMinutes = integer("duration_minutes").nullable()
    val category = varchar("category", 30)
    val priority = varchar("priority", 10)
    val reminderFrequency = varchar("reminder_frequency", 20)
    val incrementAmount = integer("increment_amount").nullable()
    val incrementEveryValue = integer("increment_every_value").nullable()
    val incrementEveryUnit = varchar("increment_every_unit", 20).nullable()
    val isCompleted = bool("is_completed").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val lastReminderSentAt = timestamp("last_reminder_sent_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/** Un token de dispositivo FCM por el que se le puede mandar un push a este usuario. */
object FcmTokens : Table("fcm_tokens") {
    val userId =
        varchar("user_id", 36)
            .references(Users.id, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val token = varchar("token", 255)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId, token)
}
