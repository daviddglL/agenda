package com.daviddelgado.agenda.server.repository

import com.daviddelgado.agenda.server.db.FcmTokens
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class FcmTokenRepository {
    fun upsert(
        userId: String,
        token: String,
    ) {
        transaction {
            val yaExiste =
                FcmTokens.selectAll().where { (FcmTokens.userId eq userId) and (FcmTokens.token eq token) }
                    .empty().not()
            if (yaExiste) {
                FcmTokens.update({ (FcmTokens.userId eq userId) and (FcmTokens.token eq token) }) {
                    it[updatedAt] = Instant.now()
                }
            } else {
                FcmTokens.insert {
                    it[FcmTokens.userId] = userId
                    it[FcmTokens.token] = token
                    it[updatedAt] = Instant.now()
                }
            }
        }
    }

    fun tokensForUser(userId: String): List<String> =
        transaction {
            FcmTokens.selectAll().where { FcmTokens.userId eq userId }.map { it[FcmTokens.token] }
        }
}
