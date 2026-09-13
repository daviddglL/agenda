package com.daviddelgado.agenda.feature.register

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val registerModule =
    module {
        viewModel { RegisterViewModel(get()) }
    }
