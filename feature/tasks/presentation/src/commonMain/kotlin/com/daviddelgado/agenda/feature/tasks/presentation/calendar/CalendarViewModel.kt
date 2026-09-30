package com.daviddelgado.agenda.feature.tasks.presentation.calendar

import androidx.lifecycle.viewModelScope
import com.daviddelgado.agenda.common.mvi.MviViewModel
import com.daviddelgado.agenda.feature.tasks.domain.usecase.ObserveTasksUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

class CalendarViewModel(observeTasksUseCase: ObserveTasksUseCase) :
    MviViewModel<CalendarState, CalendarIntent, CalendarEffect>(
        run {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            CalendarState(visibleMonth = today, selectedDate = today)
        },
    ) {
    init {
        observeTasksUseCase(null)
            .onEach { tasks ->
                setState { copy(taskCountsByDate = tasks.groupingBy { it.date }.eachCount()) }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: CalendarIntent) {
        when (intent) {
            CalendarIntent.PreviousMonth -> setState { copy(visibleMonth = visibleMonth.plus(-1, DateTimeUnit.MONTH)) }
            CalendarIntent.NextMonth -> setState { copy(visibleMonth = visibleMonth.plus(1, DateTimeUnit.MONTH)) }
            is CalendarIntent.SelectDate -> {
                setState { copy(selectedDate = intent.date) }
                sendEffect(CalendarEffect.OpenDay(intent.date))
            }
        }
    }
}
