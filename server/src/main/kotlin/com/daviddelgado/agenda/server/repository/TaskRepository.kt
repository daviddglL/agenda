package com.daviddelgado.agenda.server.repository

import com.daviddelgado.agenda.server.db.Tasks
import com.daviddelgado.agenda.server.dto.TaskDto
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class TaskRepository {
    /**
     * Crea la tarea respetando el `id` que envia el cliente (lo genera el movil para poder
     * trabajar sin red) y, si ese id ya existe para el mismo usuario, la actualiza en lugar
     * de duplicarla: asi reintentar la subida de una tarea creada offline es idempotente.
     * Si el cliente no manda id, lo genera el servidor.
     */
    fun create(
        userId: String,
        dto: TaskDto,
    ): TaskDto {
        val id = dto.id.ifBlank { UUID.randomUUID().toString() }
        if (exists(userId, id)) {
            update(userId, id, dto)
            return dto.copy(id = id)
        }
        val now = Instant.now()
        transaction {
            Tasks.insert {
                it[Tasks.id] = id
                it[Tasks.userId] = userId
                it[title] = dto.title
                it[description] = dto.description
                it[date] = LocalDate.parse(dto.date)
                it[time] = dto.time?.let(LocalTime::parse)
                it[durationMinutes] = dto.durationMinutes
                it[category] = dto.category
                it[priority] = dto.priority
                it[reminderFrequency] = dto.reminderFrequency
                it[incrementAmount] = dto.incrementAmount
                it[incrementEveryValue] = dto.incrementEveryValue
                it[incrementEveryUnit] = dto.incrementEveryUnit
                it[isCompleted] = dto.isCompleted
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        return dto.copy(id = id)
    }

    fun exists(
        userId: String,
        taskId: String,
    ): Boolean =
        transaction {
            Tasks.selectAll().where { (Tasks.id eq taskId) and (Tasks.userId eq userId) }.empty().not()
        }

    fun listForUser(userId: String): List<TaskDto> =
        transaction {
            Tasks.selectAll().where { Tasks.userId eq userId }.map { it.toDto() }
        }

    fun update(
        userId: String,
        taskId: String,
        dto: TaskDto,
    ): Boolean =
        transaction {
            Tasks.update({ (Tasks.id eq taskId) and (Tasks.userId eq userId) }) {
                it[title] = dto.title
                it[description] = dto.description
                it[date] = LocalDate.parse(dto.date)
                it[time] = dto.time?.let(LocalTime::parse)
                it[durationMinutes] = dto.durationMinutes
                it[category] = dto.category
                it[priority] = dto.priority
                it[reminderFrequency] = dto.reminderFrequency
                it[incrementAmount] = dto.incrementAmount
                it[incrementEveryValue] = dto.incrementEveryValue
                it[incrementEveryUnit] = dto.incrementEveryUnit
                it[isCompleted] = dto.isCompleted
                it[updatedAt] = Instant.now()
            } > 0
        }

    fun toggleCompleted(
        userId: String,
        taskId: String,
    ): Boolean =
        transaction {
            val current =
                Tasks.selectAll().where { (Tasks.id eq taskId) and (Tasks.userId eq userId) }.singleOrNull()
                    ?: return@transaction false
            Tasks.update({ (Tasks.id eq taskId) and (Tasks.userId eq userId) }) {
                it[isCompleted] = !current[Tasks.isCompleted]
                it[updatedAt] = Instant.now()
            } > 0
        }

    fun deleteById(
        userId: String,
        taskId: String,
    ): Boolean =
        transaction {
            Tasks.deleteWhere { (Tasks.id eq taskId) and (Tasks.userId eq userId) } > 0
        }

    /** Borrado conjunto: varias tareas del mismo usuario en una sola operacion atomica. */
    fun deleteByIds(
        userId: String,
        ids: List<String>,
    ): Int =
        transaction {
            Tasks.deleteWhere { (Tasks.userId eq userId) and (Tasks.id inList ids) }
        }

    fun deleteAllForUser(userId: String): Int =
        transaction {
            Tasks.deleteWhere { Tasks.userId eq userId }
        }

    private fun ResultRow.toDto() =
        TaskDto(
            id = this[Tasks.id],
            title = this[Tasks.title],
            description = this[Tasks.description],
            date = this[Tasks.date].toString(),
            time = this[Tasks.time]?.toString(),
            durationMinutes = this[Tasks.durationMinutes],
            category = this[Tasks.category],
            priority = this[Tasks.priority],
            reminderFrequency = this[Tasks.reminderFrequency],
            incrementAmount = this[Tasks.incrementAmount],
            incrementEveryValue = this[Tasks.incrementEveryValue],
            incrementEveryUnit = this[Tasks.incrementEveryUnit],
            isCompleted = this[Tasks.isCompleted],
        )
}
