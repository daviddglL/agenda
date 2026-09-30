package com.daviddelgado.agenda.core.domain.logger

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

/**
 * Punto unico de logging de la app cliente (Android/iOS). Envuelve Napier para no atar el
 * resto del codigo a una libreria concreta: si algun dia se añade un crash reporter real
 * (Sentry/Crashlytics), solo hay que tocar este fichero.
 */
object AgendaLogger {
    private var started = false

    fun start() {
        if (started) return
        Napier.base(DebugAntilog())
        started = true
    }

    fun d(
        tag: String,
        message: String,
    ) = Napier.d(message, tag = tag)

    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) = Napier.w(message, throwable, tag)

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) = Napier.e(message, throwable, tag)
}
