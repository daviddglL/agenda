package com.daviddelgado.agenda.feature.auth.presentation.di

import com.daviddelgado.agenda.feature.auth.presentation.forgotpassword.ForgotPasswordViewModel
import com.daviddelgado.agenda.feature.auth.presentation.login.LoginViewModel
import com.daviddelgado.agenda.feature.auth.presentation.register.RegisterViewModel
import com.daviddelgado.agenda.feature.auth.presentation.resetpassword.ResetPasswordViewModel
import com.daviddelgado.agenda.feature.auth.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authPresentationModule =
    module {
        viewModel { LoginViewModel(get(), get(), get()) }
        viewModel { RegisterViewModel(get()) }
        viewModel { ForgotPasswordViewModel(get()) }
        viewModel { ResetPasswordViewModel(get()) }
        viewModel { SettingsViewModel(get(), get(), get()) }
    }
