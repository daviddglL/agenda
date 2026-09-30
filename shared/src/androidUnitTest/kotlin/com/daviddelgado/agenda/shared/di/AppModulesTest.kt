package com.daviddelgado.agenda.shared.di

import android.content.Context
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Comprueba que cada dependencia que piden los modulos de Koin de la app la declara algun
 * modulo (tras dividir DomainModule/DataModule por feature es facil perder una). Solo analiza
 * los constructores, no crea nada, asi que no necesita un Context real.
 */
@OptIn(KoinExperimentalAPI::class)
class AppModulesTest {
    @Test
    fun `el grafo de Koin de la app esta completo`() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(Context::class, HttpClientConfig::class, HttpClientEngine::class, List::class),
        )
    }
}
