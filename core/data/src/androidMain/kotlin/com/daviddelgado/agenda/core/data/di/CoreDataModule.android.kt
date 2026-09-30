package com.daviddelgado.agenda.core.data.di

import com.daviddelgado.agenda.core.data.BuildConfig
import com.daviddelgado.agenda.core.data.networking.NetworkConfig
import com.daviddelgado.agenda.core.data.networking.ProductionConfig
import com.daviddelgado.agenda.core.data.session.SecureStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 10.0.2.2 es la IP con la que el emulador de Android ve el `localhost` del PC, asi que
 * apunta al `./gradlew :server:run` que corre en la maquina de desarrollo. Para un movil
 * fisico hay que poner aqui la IP de la red local del PC (p.ej. "http://192.168.1.40:8080/").
 */
private const val ANDROID_EMULATOR_BASE_URL = "http://10.0.2.2:8080/"

/**
 * Debug (`./gradlew :androidApp:assembleDebug`) apunta al servidor local; release
 * (`assembleRelease`, la build que se sube a la tienda) apunta a [ProductionConfig], que hoy
 * es un placeholder documentado hasta que el backend tenga un dominio real desplegado.
 */
private fun networkConfig(): NetworkConfig =
    if (BuildConfig.DEBUG) {
        NetworkConfig(baseUrl = ANDROID_EMULATOR_BASE_URL)
    } else {
        NetworkConfig(
            baseUrl = ProductionConfig.BASE_URL,
            certificatePinsSha256 = ProductionConfig.CERTIFICATE_PINS_SHA256,
        )
    }

actual val platformCoreDataModule: Module =
    module {
        single { networkConfig() }
        single { SecureStorage(androidContext()) }
    }
