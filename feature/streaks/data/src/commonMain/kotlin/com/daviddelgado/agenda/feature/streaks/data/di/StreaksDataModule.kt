package com.daviddelgado.agenda.feature.streaks.data.di

import com.daviddelgado.agenda.feature.streaks.data.repository.StreakRepositoryImpl
import com.daviddelgado.agenda.feature.streaks.domain.repository.StreakRepository
import org.koin.dsl.module

val streaksDataModule =
    module {
        single<StreakRepository> { StreakRepositoryImpl(get()) }
    }
