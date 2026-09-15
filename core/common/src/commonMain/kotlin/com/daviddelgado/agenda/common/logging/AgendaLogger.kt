package com.daviddelgado.agenda.common.logging

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
        try {
            Napier.base(DebugAntilog())
        } catch (e: Exception) {
            // Fallback en unit tests donde DebugAntilog falla
        }
        started = true
    }

    fun d(
        tag: String,
        message: String,
    ) {
        try {
            Napier.d(message, tag = tag)
        } catch (e: Exception) {
            // Ignora excepciones en logging (p.ej. en tests JVM)
        }
    }

    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        try {
            Napier.w(message, throwable, tag)
        } catch (e: Exception) {
            // Ignora excepciones en logging (p.ej. en tests JVM)
        }
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        try {
            Napier.e(message, throwable, tag)
        } catch (e: Exception) {
            // Ignora excepciones en logging (p.ej. en tests JVM)
        }
    }
}
