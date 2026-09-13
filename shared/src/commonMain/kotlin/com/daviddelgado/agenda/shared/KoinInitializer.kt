package com.daviddelgado.agenda.shared

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/** Punto de entrada comun; cada plataforma añade sus modulos especificos (androidContext, etc). */
fun initKoin(
    extraModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
) {
    startKoin {
        appDeclaration()
        modules(appModules + extraModules)
    }
}
