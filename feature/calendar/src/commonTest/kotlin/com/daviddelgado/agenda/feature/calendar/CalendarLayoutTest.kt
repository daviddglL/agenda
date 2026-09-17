package com.daviddelgado.agenda.feature.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun todasLasSemanasTienenSieteColumnasIncluidaLaUltima() {
        // Regresion: la ultima semana solo tenia los dias que quedaban (sin rellenar con
        // null hasta 7), asi que Arrangement.Center la centraba en vez de alinearla bajo
        // sus columnas de dia de la semana.
        val semanas = monthWeeks(2026, Month.SEPTEMBER)

        assertTrue(semanas.isNotEmpty())
        assertTrue(semanas.all { it.size == 7 })
    }

    @Test
    fun losDiasDelMesAparecenEnOrdenSinDuplicarNiSaltarNinguno() {
        val anio = 2026
        val mes = Month.SEPTEMBER
        val primerDia = LocalDate(anio, mes, 1)
        val diasEnElMes = primerDia.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth

        val dias = monthWeeks(anio, mes).flatten().filterNotNull()

        assertEquals((1..diasEnElMes).map { LocalDate(anio, mes, it) }, dias)
    }
}
