package com.daviddelgado.agenda.data.di

import com.daviddelgado.agenda.data.secure.SecureStorage
import com.daviddelgado.agenda.database.DatabaseFactory
import com.daviddelgado.agenda.network.NetworkConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/** El simulador de iOS comparte la red del Mac, asi que el servidor local es localhost. */
private const val IOS_SIMULATOR_BASE_URL = "http://localhost:8080/"

actual val platformDataModule: Module =
    module {
        single { NetworkConfig(baseUrl = IOS_SIMULATOR_BASE_URL) }
        single { SecureStorage() }
        single { DatabaseFactory() }
    }
