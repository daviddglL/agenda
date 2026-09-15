package com.daviddelgado.agenda.shared

import com.daviddelgado.agenda.common.logging.AgendaLogger
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/** Punto de entrada comun; cada plataforma añade sus modulos especificos (androidContext, etc). */
fun initKoin(
    extraModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
) {
    AgendaLogger.start()
    startKoin {
        appDeclaration()
        modules(appModules + extraModules)
    }
}
