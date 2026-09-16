package com.daviddelgado.agenda.feature.passwordreset

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val passwordResetModule =
    module {
        viewModel { ForgotPasswordViewModel(get()) }
        viewModel { ResetPasswordViewModel(get()) }
    }
