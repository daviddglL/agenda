package com.daviddelgado.agenda.feature.streaks.domain.usecase

import com.daviddelgado.agenda.feature.streaks.domain.model.StreakSummary
import com.daviddelgado.agenda.feature.streaks.domain.repository.StreakRepository
import kotlinx.coroutines.flow.Flow

class ObserveStreakUseCase(private val repository: StreakRepository) {
    operator fun invoke(): Flow<StreakSummary> = repository.observeStreak()
}
