package com.daviddelgado.agenda.data.di

import com.daviddelgado.agenda.data.auth.TokenProviderImpl
import com.daviddelgado.agenda.network.TokenProvider
import com.daviddelgado.agenda.network.createHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Cada plataforma provee SecureStorage (depende de Context en Android) y
 * su [com.daviddelgado.agenda.network.NetworkConfig], porque la URL del servidor local no es
 * la misma vista desde el emulador de Android (10.0.2.2) que desde el simulador de iOS.
 */
expect val platformDataModule: Module

val dataModule =
    module {
        includes(platformDataModule)

        single { TokenProviderImpl(get()) }
        single<TokenProvider> { get<TokenProviderImpl>() }
        single { createHttpClient(get(), get()) }
    }
