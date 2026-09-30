package com.daviddelgado.agenda.shared.di

import android.content.Context
import com.daviddelgado.agenda.core.data.session.TokenProvider
import com.daviddelgado.agenda.core.data.session.TokenProviderImpl
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Grafo de Koin de la app completa. Los modulos usan el DSL por constructor (`singleOf(::Impl)
 * { bind<I>() }`), asi que `verify()` de Koin 4.0.0 revisa los constructores reales, tambien
 * los de las implementaciones tras una interfaz. Siguen siendo lambdas (y por tanto no se
 * revisan por constructor) NetworkConfig, SecureStorage, DatabaseFactory, HttpClient,
 * AgendaDatabase, los DAO y FcmTokenProvider.
 * - `Context` lo da `androidContext()`.
 * - `HttpClientEngine` y `HttpClientConfig` estan en `extraTypes` solo por el constructor
 *   propio de `HttpClient`.
 * - `List` es por `NetworkConfig.certificatePinsSha256`; punto ciego para un futuro parametro
 *   `List` de constructor.
 * Ademas: contrato de tipos por feature sin huecos ([FeatureContract.missingTypes]) y que
 * `TokenProviderImpl`/`TokenProvider` sean una unica definicion. El contrato exacto por
 * feature y la ausencia de overrides estan en [FeatureModulesTest].
 */
@OptIn(KoinExperimentalAPI::class)
class AppModulesTest {
    @Test
    fun `el grafo de Koin de la app esta completo`() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(Context::class, HttpClientConfig::class, HttpClientEngine::class, List::class),
        )
    }

    @Test
    fun `los modulos de la app declaran todos los tipos del contrato de cada feature`() {
        val missing = FeatureContract.missingTypes(koinApplication { modules(appModules) }.koin.declaredTypes())
        assertTrue(missing.isEmpty(), "Faltan tipos del contrato en appModules: $missing")
    }

    @Test
    fun `TokenProviderImpl y TokenProvider son una unica definicion single con dos tipos`() {
        val koin = koinApplication { modules(appModules) }.koin
        assertEquals(1, koin.definitionsDeclaring(TokenProviderImpl::class))
        assertEquals(1, koin.definitionsDeclaring(TokenProvider::class))
    }
}
