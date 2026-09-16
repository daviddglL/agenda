package com.daviddelgado.agenda.server.repository

import com.daviddelgado.agenda.server.db.Users
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

data class UserRecord(
    val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val tokenVersion: Int,
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
        return UserRecord(id, name, email, passwordHash, tokenVersion = 0)
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

    /**
     * Cambia la contrasena y sube `tokenVersion`: cualquier refresh token emitido antes de
     * esta llamada deja de servir (ver [com.daviddelgado.agenda.server.security.JwtConfig]
     * y su uso en `handleRefresh`, `AuthRoutes.kt`).
     */
    fun updatePassword(
        id: String,
        newPasswordHash: String,
    ) {
        transaction {
            val actual = Users.selectAll().where { Users.id eq id }.single()[Users.tokenVersion]
            Users.update({ Users.id eq id }) {
                it[Users.passwordHash] = newPasswordHash
                it[Users.tokenVersion] = actual + 1
            }
        }
    }

    private fun ResultRow.toRecord() =
        UserRecord(
            id = this[Users.id],
            name = this[Users.name],
            email = this[Users.email],
            passwordHash = this[Users.passwordHash],
            tokenVersion = this[Users.tokenVersion],
        )
}
