package com.daviddelgado.agenda.server.security

import org.mindrot.jbcrypt.BCrypt

object PasswordHasher {
    fun hash(rawPassword: String): String = BCrypt.hashpw(rawPassword, BCrypt.gensalt(12))

    fun matches(
        rawPassword: String,
        hashed: String,
    ): Boolean = BCrypt.checkpw(rawPassword, hashed)
}
