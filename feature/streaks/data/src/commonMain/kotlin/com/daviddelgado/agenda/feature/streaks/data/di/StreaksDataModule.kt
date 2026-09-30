package com.daviddelgado.agenda.feature.streaks.data.di

import com.daviddelgado.agenda.feature.streaks.data.repository.StreakRepositoryImpl
import com.daviddelgado.agenda.feature.streaks.domain.repository.StreakRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val streaksDataModule =
    module {
        singleOf(::StreakRepositoryImpl) { bind<StreakRepository>() }
    }
