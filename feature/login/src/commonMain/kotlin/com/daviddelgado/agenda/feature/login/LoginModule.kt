package com.daviddelgado.agenda.feature.login

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val loginModule =
    module {
        viewModel { LoginViewModel(get()) }
    }
