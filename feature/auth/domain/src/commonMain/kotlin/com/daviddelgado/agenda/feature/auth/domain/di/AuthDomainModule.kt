package com.daviddelgado.agenda.feature.auth.domain.di

import com.daviddelgado.agenda.feature.auth.domain.usecase.DeleteAccountUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.LoginUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.LogoutUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterFcmTokenUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RequestPasswordResetUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.ResetPasswordUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RestoreSessionUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val authDomainModule =
    module {
        factoryOf(::LoginUseCase)
        factoryOf(::RegisterUseCase)
        factoryOf(::DeleteAccountUseCase)
        factoryOf(::RegisterFcmTokenUseCase)
        factoryOf(::RequestPasswordResetUseCase)
        factoryOf(::ResetPasswordUseCase)
        factoryOf(::RestoreSessionUseCase)
        factoryOf(::LogoutUseCase)
        factoryOf(::ObserveCurrentUserUseCase)
    }
