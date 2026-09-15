package com.daviddelgado.agenda.feature.calendar

import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarLayoutTest {
    @Test
    fun siElAnchoEsElLadoMasEstrechoElTamanoLoManda_ElAncho() {
        val tamano = squareCellSizeDp(maxWidthDp = 350f, maxHeightDp = 900f, columns = 7, rows = 5)

        assertEquals(50f, tamano)
    }

    @Test
    fun siElAltoEsElLadoMasEstrechoElTamanoLoManda_ElAlto() {
        val tamano = squareCellSizeDp(maxWidthDp = 1000f, maxHeightDp = 300f, columns = 7, rows = 6)

        assertEquals(50f, tamano)
    }

    @Test
    fun conCincoOSeisSemanasLaCeldaSeAdaptaParaSeguirLlenandoElAlto() {
        val conCincoSemanas = squareCellSizeDp(maxWidthDp = 1000f, maxHeightDp = 600f, columns = 7, rows = 5)
        val conSeisSemanas = squareCellSizeDp(maxWidthDp = 1000f, maxHeightDp = 600f, columns = 7, rows = 6)

        assertEquals(120f, conCincoSemanas)
        assertEquals(100f, conSeisSemanas)
    }
}
