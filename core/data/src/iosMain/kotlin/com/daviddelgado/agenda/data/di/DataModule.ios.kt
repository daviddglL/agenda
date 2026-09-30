package com.daviddelgado.agenda.data.di

import com.daviddelgado.agenda.data.secure.SecureStorage
import com.daviddelgado.agenda.network.NetworkConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/** El simulador de iOS comparte la red del Mac, asi que el servidor local es localhost. */
private const val IOS_SIMULATOR_BASE_URL = "http://localhost:8080/"

/**
 * TODO(produccion-iOS): esto compila pero no se ha podido probar en Xcode (sin Mac en este
 * entorno, ver iosApp/README.md). Cuando haya Mac disponible, replicar aqui el mismo cambio
 * que [com.daviddelgado.agenda.data.di.platformDataModule] de Android: elegir entre esta URL
 * de desarrollo y `com.daviddelgado.agenda.network.ProductionConfig` segun la build
 * (Kotlin/Native no tiene un `BuildConfig.DEBUG` equivalente automatico; hay que leerlo del
 * `Debug`/`Release` scheme de Xcode, p.ej. via un flag de compilacion `-DPRODUCTION`).
 */
actual val platformDataModule: Module =
    module {
        single { NetworkConfig(baseUrl = IOS_SIMULATOR_BASE_URL) }
        single { SecureStorage() }
    }
