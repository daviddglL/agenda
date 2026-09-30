package com.daviddelgado.agenda.feature.streaks.domain.repository

import com.daviddelgado.agenda.feature.streaks.domain.model.StreakSummary
import kotlinx.coroutines.flow.Flow

interface StreakRepository {
    fun observeStreak(): Flow<StreakSummary>
}
