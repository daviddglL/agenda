package com.daviddelgado.agenda.feature.streaks.domain.model

import kotlinx.datetime.LocalDate

data class StreakSummary(
    val currentStreak: Int,
    val bestStreak: Int,
    val completedDates: List<LocalDate>,
)
