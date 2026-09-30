package com.daviddelgado.agenda.feature.streaks.presentation.streaks

import com.daviddelgado.agenda.core.presentation.mvi.UiEffect
import com.daviddelgado.agenda.core.presentation.mvi.UiIntent
import com.daviddelgado.agenda.core.presentation.mvi.UiState
import kotlinx.datetime.LocalDate

data class StreaksState(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val completedDates: List<LocalDate> = emptyList(),
) : UiState

sealed interface StreaksIntent : UiIntent {
    data object Refresh : StreaksIntent
}

sealed interface StreaksEffect : UiEffect
