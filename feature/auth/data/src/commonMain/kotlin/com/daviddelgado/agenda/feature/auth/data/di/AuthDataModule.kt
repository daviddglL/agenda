package com.daviddelgado.agenda.feature.auth.data.di

import com.daviddelgado.agenda.feature.auth.data.remote.AuthApi
import com.daviddelgado.agenda.feature.auth.data.repository.AuthRepositoryImpl
import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** Cada plataforma provee su FcmTokenProvider real (Firebase en Android; iOS pendiente). */
expect val platformAuthDataModule: Module

val authDataModule =
    module {
        includes(platformAuthDataModule)
        singleOf(::AuthApi)
        singleOf(::AuthRepositoryImpl) { bind<AuthRepository>() }
    }
