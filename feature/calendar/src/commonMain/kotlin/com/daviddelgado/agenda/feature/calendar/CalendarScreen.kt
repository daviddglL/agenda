package com.daviddelgado.agenda.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.daviddelgado.agenda.designsystem.theme.AgendaViolet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { viewModel.onIntent(CalendarIntent.PreviousMonth) }) { Text("<") }
                Text(
                    text = "${state.visibleMonth.month.name} ${state.visibleMonth.year}",
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = { viewModel.onIntent(CalendarIntent.NextMonth) }) { Text(">") }
            }

            MonthGrid(state = state, onSelectDate = { viewModel.onIntent(CalendarIntent.SelectDate(it)) })
        }
    }
}

@Composable
private fun MonthGrid(
    state: CalendarState,
    onSelectDate: (LocalDate) -> Unit,
) {
    val firstOfMonth = LocalDate(state.visibleMonth.year, state.visibleMonth.month, 1)
    val daysInMonth = firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
    val leadingBlanks = firstOfMonth.dayOfWeek.ordinal

    val cells =
        buildList {
            repeat(leadingBlanks) { add(null) }
            for (day in 1..daysInMonth) add(LocalDate(state.visibleMonth.year, state.visibleMonth.month, day))
        }

    cells.chunked(7).forEach { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { date ->
                Box(
                    modifier =
                        Modifier
                            .aspectRatio(1f)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    date == null -> androidx.compose.ui.graphics.Color.Transparent
                                    date == state.selectedDate -> AgendaViolet
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                            )
                            .let { m -> if (date != null) m.clickable { onSelectDate(date) } else m },
                    contentAlignment = Alignment.Center,
                ) {
                    if (date != null) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            color =
                                if (date == state.selectedDate) {
                                    androidx.compose.ui.graphics.Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    }
                }
            }
        }
    }
}
