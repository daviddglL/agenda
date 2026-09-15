package com.daviddelgado.agenda.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * H2 en modo fichero: cero configuracion externa (sin Docker ni Postgres) para poder
 * levantar el servidor y probarlo tal cual. Cambiar a Postgres en produccion es solo
 * ajustar la URL/driver aqui, el resto del codigo (Exposed) no cambia.
 */
object DatabaseFactory {
    fun init(jdbcUrl: String = "jdbc:h2:file:./data/agenda;AUTO_SERVER=TRUE") {
        val database =
            Database.connect(
                url = jdbcUrl,
                driver = "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(Users, Tasks, FcmTokens)
        }
    }
}
