package com.daviddelgado.agenda.common.util

import kotlin.random.Random

private const val ID_LENGTH = 32
private const val HEX_DIGITS = "0123456789abcdef"

/**
 * Id para entidades creadas en el cliente. `java.util.UUID` no existe en codigo comun de KMP,
 * asi que se genera una cadena hexadecimal de 32 caracteres (cabe en el `varchar(36)` del
 * servidor). El id lo fija el cliente para que crear una tarea sin red y subirla despues sea
 * idempotente: reintentar no duplica la tarea.
 */
fun randomEntityId(): String = (1..ID_LENGTH).map { HEX_DIGITS[Random.nextInt(HEX_DIGITS.length)] }.joinToString("")
