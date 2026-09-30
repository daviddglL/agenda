package com.daviddelgado.agenda.feature.auth.presentation.di

import com.daviddelgado.agenda.feature.auth.presentation.forgotpassword.ForgotPasswordViewModel
import com.daviddelgado.agenda.feature.auth.presentation.login.LoginViewModel
import com.daviddelgado.agenda.feature.auth.presentation.register.RegisterViewModel
import com.daviddelgado.agenda.feature.auth.presentation.resetpassword.ResetPasswordViewModel
import com.daviddelgado.agenda.feature.auth.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authPresentationModule =
    module {
        viewModelOf(::LoginViewModel)
        viewModelOf(::RegisterViewModel)
        viewModelOf(::ForgotPasswordViewModel)
        viewModelOf(::ResetPasswordViewModel)
        viewModelOf(::SettingsViewModel)
    }
