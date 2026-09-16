package com.daviddelgado.agenda.server.repository

import com.daviddelgado.agenda.server.db.PasswordResetCodes
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

data class PasswordResetCodeRecord(
    val codeHash: String,
    val expiresAt: Instant,
    val attempts: Int,
)

class PasswordResetRepository {
    /** Sustituye cualquier codigo anterior del usuario: solo el ultimo pedido sirve. */
    fun createOrReplace(
        userId: String,
        codeHash: String,
        expiresAt: Instant,
    ) {
        transaction {
            val yaExiste = PasswordResetCodes.selectAll().where { PasswordResetCodes.userId eq userId }.empty().not()
            if (yaExiste) {
                PasswordResetCodes.update({ PasswordResetCodes.userId eq userId }) {
                    it[PasswordResetCodes.codeHash] = codeHash
                    it[PasswordResetCodes.expiresAt] = expiresAt
                    it[attempts] = 0
                }
            } else {
                PasswordResetCodes.insert {
                    it[PasswordResetCodes.userId] = userId
                    it[PasswordResetCodes.codeHash] = codeHash
                    it[PasswordResetCodes.expiresAt] = expiresAt
                }
            }
        }
    }

    fun find(userId: String): PasswordResetCodeRecord? =
        transaction {
            PasswordResetCodes.selectAll().where { PasswordResetCodes.userId eq userId }.singleOrNull()?.let {
                PasswordResetCodeRecord(
                    codeHash = it[PasswordResetCodes.codeHash],
                    expiresAt = it[PasswordResetCodes.expiresAt],
                    attempts = it[PasswordResetCodes.attempts],
                )
            }
        }

    fun incrementAttempts(userId: String) {
        transaction {
            val actual =
                PasswordResetCodes.selectAll().where { PasswordResetCodes.userId eq userId }.singleOrNull()
                    ?.get(PasswordResetCodes.attempts) ?: return@transaction
            PasswordResetCodes.update({ PasswordResetCodes.userId eq userId }) {
                it[attempts] = actual + 1
            }
        }
    }

    fun delete(userId: String) {
        transaction {
            PasswordResetCodes.deleteWhere { PasswordResetCodes.userId eq userId }
        }
    }
}
