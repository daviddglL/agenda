package com.daviddelgado.agenda.feature.tasks.presentation.calendar

import com.daviddelgado.agenda.core.presentation.mvi.UiEffect
import com.daviddelgado.agenda.core.presentation.mvi.UiIntent
import com.daviddelgado.agenda.core.presentation.mvi.UiState
import kotlinx.datetime.LocalDate

data class CalendarState(
    val visibleMonth: LocalDate,
    val selectedDate: LocalDate,
    val taskCountsByDate: Map<LocalDate, Int> = emptyMap(),
) : UiState

sealed interface CalendarIntent : UiIntent {
    data object PreviousMonth : CalendarIntent

    data object NextMonth : CalendarIntent

    data class SelectDate(val date: LocalDate) : CalendarIntent
}

sealed interface CalendarEffect : UiEffect {
    data class OpenDay(val date: LocalDate) : CalendarEffect
}
