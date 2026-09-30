package com.daviddelgado.agenda.feature.streaks.presentation.streaks

import androidx.lifecycle.viewModelScope
import com.daviddelgado.agenda.common.mvi.MviViewModel
import com.daviddelgado.agenda.feature.streaks.domain.usecase.ObserveStreakUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class StreaksViewModel(observeStreakUseCase: ObserveStreakUseCase) :
    MviViewModel<StreaksState, StreaksIntent, StreaksEffect>(StreaksState()) {
    init {
        observeStreakUseCase()
            .onEach { summary ->
                setState {
                    copy(
                        currentStreak = summary.currentStreak,
                        bestStreak = summary.bestStreak,
                        completedDates = summary.completedDates,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: StreaksIntent) {
        // La observacion es reactiva (StateFlow); Refresh queda reservado para forzar
        // una resincronizacion remota cuando :core:data implemente el pull manual.
    }
}
