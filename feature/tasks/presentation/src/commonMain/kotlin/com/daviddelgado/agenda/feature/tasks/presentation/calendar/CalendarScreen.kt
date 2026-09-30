package com.daviddelgado.agenda.feature.tasks.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.daviddelgado.agenda.designsystem.theme.AgendaViolet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CalendarScreen(
    onOpenDay: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is CalendarEffect.OpenDay -> onOpenDay(effect.date)
            }
        }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { viewModel.onIntent(CalendarIntent.PreviousMonth) }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Mes anterior")
                }
                Text(
                    text = "${state.visibleMonth.month.name} ${state.visibleMonth.year}",
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = { viewModel.onIntent(CalendarIntent.NextMonth) }) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Mes siguiente")
                }
            }

            MonthGrid(
                state = state,
                onSelectDate = { viewModel.onIntent(CalendarIntent.SelectDate(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Cuadricula del mes: cada celda es cuadrada y su tamano se calcula en cada composicion a
 * partir del espacio realmente disponible (columnas fijas = 7 dias, filas = semanas del mes
 * visible), no con un valor fijo, para que la rejilla aproveche toda la pantalla sea cual sea
 * el numero de semanas del mes (4 a 6).
 */
@Composable
private fun MonthGrid(
    state: CalendarState,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = monthWeeks(state.visibleMonth.year, state.visibleMonth.month)

    BoxWithConstraints(modifier = modifier) {
        val cellSize = squareCellSizeDp(maxWidth.value, maxHeight.value, DAYS_PER_WEEK, weeks.size).dp

        // SpaceEvenly (no Center) reparte el hueco sobrante entre semanas para que la
        // cuadricula aproveche toda la altura disponible en vez de amontonarse arriba: el
        // ancho manda el tamano de cada celda (7 columnas siempre caben menos que las 4-6
        // filas disponibles en un telefono en vertical), asi que sin esto sobraba media
        // pantalla en blanco debajo de la ultima semana.
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            weeks.forEach { week ->
                Row(horizontalArrangement = Arrangement.Center) {
                    week.forEach { date ->
                        DayCell(
                            date = date,
                            size = cellSize,
                            taskCount = date?.let { state.taskCountsByDate[it] } ?: 0,
                            isSelected = date != null && date == state.selectedDate,
                            onSelectDate = onSelectDate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    size: androidx.compose.ui.unit.Dp,
    taskCount: Int,
    isSelected: Boolean,
    onSelectDate: (LocalDate) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (isSelected) AgendaViolet else Color.Transparent)
                .let { m -> if (date != null) m.clickable { onSelectDate(date) } else m },
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.dayOfMonth.toString(),
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                if (taskCount > 0) {
                    Text(
                        text = "($taskCount)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
