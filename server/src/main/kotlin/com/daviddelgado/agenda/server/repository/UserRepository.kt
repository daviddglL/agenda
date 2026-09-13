package com.daviddelgado.agenda.server.repository

import com.daviddelgado.agenda.server.db.Users
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class UserRecord(
    val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
)

class UserRepository {
    fun create(
        name: String,
        email: String,
        passwordHash: String,
    ): UserRecord {
        val id = UUID.randomUUID().toString()
        transaction {
            Users.insert {
                it[Users.id] = id
                it[Users.name] = name
                it[Users.email] = email
                it[Users.passwordHash] = passwordHash
                it[Users.createdAt] = Instant.now()
            }
        }
        return UserRecord(id, name, email, passwordHash)
    }

    fun findByEmail(email: String): UserRecord? =
        transaction {
            Users.selectAll().where { Users.email eq email }.singleOrNull()?.toRecord()
        }

    fun findById(id: String): UserRecord? =
        transaction {
            Users.selectAll().where { Users.id eq id }.singleOrNull()?.toRecord()
        }

    /** Borra el usuario; ON DELETE CASCADE en Tasks borra sus tareas de forma conjunta. */
    fun delete(id: String): Boolean =
        transaction {
            Users.deleteWhere { Users.id eq id } > 0
        }

    private fun ResultRow.toRecord() =
        UserRecord(
            id = this[Users.id],
            name = this[Users.name],
            email = this[Users.email],
            passwordHash = this[Users.passwordHash],
        )
}
