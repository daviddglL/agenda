package com.daviddelgado.agenda.server.security

import java.security.MessageDigest

/** Hash SHA-256 en hexadecimal minuscula. Usado para no guardar codigos de un solo uso en claro. */
fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
