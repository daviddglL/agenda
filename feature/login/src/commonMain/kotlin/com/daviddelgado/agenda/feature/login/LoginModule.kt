package com.daviddelgado.agenda.feature.login

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Cada plataforma provee su FcmTokenProvider real (Firebase en Android; iOS pendiente). */
expect val platformLoginModule: Module

val loginModule =
    module {
        includes(platformLoginModule)
        viewModel { LoginViewModel(get(), get(), get()) }
    }
