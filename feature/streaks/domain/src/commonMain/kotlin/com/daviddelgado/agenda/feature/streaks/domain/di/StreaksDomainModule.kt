package com.daviddelgado.agenda.feature.streaks.domain.di

import com.daviddelgado.agenda.feature.streaks.domain.usecase.ObserveStreakUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val streaksDomainModule =
    module {
        factoryOf(::ObserveStreakUseCase)
    }
