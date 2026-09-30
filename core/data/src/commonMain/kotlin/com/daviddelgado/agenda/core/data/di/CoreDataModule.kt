package com.daviddelgado.agenda.core.data.di

import com.daviddelgado.agenda.core.data.networking.createHttpClient
import com.daviddelgado.agenda.core.data.session.TokenProvider
import com.daviddelgado.agenda.core.data.session.TokenProviderImpl
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Cada plataforma provee SecureStorage (depende de Context en Android) y
 * su [com.daviddelgado.agenda.core.data.networking.NetworkConfig], porque la URL del servidor local no es
 * la misma vista desde el emulador de Android (10.0.2.2) que desde el simulador de iOS.
 */
expect val platformCoreDataModule: Module

val coreDataModule =
    module {
        includes(platformCoreDataModule)

        single { TokenProviderImpl(get()) }
        single<TokenProvider> { get<TokenProviderImpl>() }
        single { createHttpClient(get(), get()) }
    }
