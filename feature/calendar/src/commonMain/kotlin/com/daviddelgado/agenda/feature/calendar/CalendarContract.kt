package com.daviddelgado.agenda.feature.calendar

import com.daviddelgado.agenda.common.mvi.UiEffect
import com.daviddelgado.agenda.common.mvi.UiIntent
import com.daviddelgado.agenda.common.mvi.UiState
import kotlinx.datetime.LocalDate

data class CalendarState(
    val visibleMonth: LocalDate,
    val selectedDate: LocalDate,
    val datesWithTasks: Set<LocalDate> = emptySet(),
) : UiState

sealed interface CalendarIntent : UiIntent {
    data object PreviousMonth : CalendarIntent

    data object NextMonth : CalendarIntent

    data class SelectDate(val date: LocalDate) : CalendarIntent
}

sealed interface CalendarEffect : UiEffect {
    data class OpenDay(val date: LocalDate) : CalendarEffect
}
