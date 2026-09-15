package com.daviddelgado.agenda.common.logging

import kotlin.test.Test

class AgendaLoggerTest {
    @Test
    fun arrancarDosVecesNoFalla() {
        AgendaLogger.start()
        AgendaLogger.start()
    }

    @Test
    fun logueaSinLanzarExcepcion() {
        AgendaLogger.start()
        AgendaLogger.d("Test", "mensaje de depuracion")
        AgendaLogger.w("Test", "aviso", RuntimeException("motivo"))
        AgendaLogger.e("Test", "error", RuntimeException("causa"))
    }
}
