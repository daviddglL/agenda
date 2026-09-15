package com.daviddelgado.agenda.feature.calendar

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
