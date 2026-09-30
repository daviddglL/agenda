package com.daviddelgado.agenda.shared.di

import com.daviddelgado.agenda.core.domain.logger.AgendaLogger
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/** Punto de entrada comun; cada plataforma añade sus modulos especificos (androidContext, etc). */
fun initKoin(
    extraModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
) {
    AgendaLogger.start()
    val koinApp =
        startKoin {
            appDeclaration()
            modules(appModules + extraModules)
        }
    FeatureAvailability.update(
        checkFeatures({ koinApp.koin.declaredTypes() }) { AgendaLogger.e("Koin", it) },
    )
}
