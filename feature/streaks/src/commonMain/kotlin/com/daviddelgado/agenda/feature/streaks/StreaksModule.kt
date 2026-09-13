package com.daviddelgado.agenda.feature.streaks

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val streaksModule =
    module {
        viewModel { StreaksViewModel(get()) }
    }
