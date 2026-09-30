package com.daviddelgado.agenda.feature.streaks.presentation.di

import com.daviddelgado.agenda.feature.streaks.presentation.streaks.StreaksViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val streaksPresentationModule =
    module {
        viewModelOf(::StreaksViewModel)
    }
