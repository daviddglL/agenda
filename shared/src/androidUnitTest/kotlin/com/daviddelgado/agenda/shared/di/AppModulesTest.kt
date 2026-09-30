package com.daviddelgado.agenda.shared.di

import android.content.Context
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Comprueba dependencias de constructor de los modulos de Koin de la app. Alcance limitado:
 * `verify()` de Koin 4.0.0 solo revisa los constructores del tipo declarado de cada definicion.
 * - Se salta los enlaces con tipo de interfaz (`single<TaskRepository> { ... }`,
 *   AuthRepository, StreakRepository, TokenProvider, FcmTokenProvider).
 * - No ve lo que llaman las lambdas.
 * - `HttpClientEngine` y `HttpClientConfig` estan en `extraTypes` solo porque se comprueba el
 *   constructor propio de `HttpClient`; `Context` lo da `androidContext()`.
 * - `List` esta por `NetworkConfig.certificatePinsSha256`, que provee como instancia el modulo
 *   de plataforma; es un punto ciego para un futuro parametro `List` de constructor.
 * Seria mas fuerte con `singleOf(::Impl) { bind<I>() }` o con un test de resolucion real.
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
