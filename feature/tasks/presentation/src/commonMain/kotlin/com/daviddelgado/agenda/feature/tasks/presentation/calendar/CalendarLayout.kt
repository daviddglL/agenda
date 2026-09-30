package com.daviddelgado.agenda.feature.tasks.presentation.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

const val DAYS_PER_WEEK = 7

/**
 * Tamano (en dp) de una celda cuadrada de dia que llena el espacio disponible sin fijar un
 * valor constante: se calcula a partir de las columnas (7 dias) y las filas reales del mes
 * visible (4 a 6 semanas), y gana la dimension mas estrecha para que la celda siga siendo
 * cuadrada.
 */
fun squareCellSizeDp(
    maxWidthDp: Float,
    maxHeightDp: Float,
    columns: Int,
    rows: Int,
): Float = minOf(maxWidthDp / columns, maxHeightDp / rows)

/**
 * Semanas del mes indicado, cada una con exactamente [DAYS_PER_WEEK] columnas: `null` antes
 * del dia 1 y, si hace falta, tambien despues del ultimo dia. Sin este relleno final, la
 * ultima semana quedaba con menos de 7 elementos y `Arrangement.Center` la centraba en vez de
 * alinearla bajo sus columnas de dia de la semana.
 */
fun monthWeeks(
    year: Int,
    month: Month,
): List<List<LocalDate?>> {
    val firstOfMonth = LocalDate(year, month, 1)
    val daysInMonth = firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
    val leadingBlanks = firstOfMonth.dayOfWeek.ordinal

    val days =
        buildList {
            repeat(leadingBlanks) { add(null) }
            for (day in 1..daysInMonth) add(LocalDate(year, month, day))
        }
    val trailingBlanks = (DAYS_PER_WEEK - days.size % DAYS_PER_WEEK) % DAYS_PER_WEEK
    return (days + List(trailingBlanks) { null }).chunked(DAYS_PER_WEEK)
}
