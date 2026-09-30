package com.daviddelgado.agenda.shared.di

import android.content.Context
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.test.verify.verify
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Por cada feature: (1) su modulo agregado, aislado, resuelve los constructores reales;
 * (2) los tipos que declara son EXACTAMENTE los de [FeatureContract.requiredTypes] (sin
 * sobrar ni faltar); (3) cargar todos los modulos de la app no redefine nada.
 */
@OptIn(KoinExperimentalAPI::class)
class FeatureModulesTest {
    // extraTypes minimos por feature (los mismos justificados en AppModulesTest):
    // - Context: DatabaseFactory(Context) de tasksDatabaseModule, que incluyen las tres features.
    // - HttpClientEngine y HttpClientConfig: constructor propio de HttpClient (coreDataModule).
    // - List: NetworkConfig.certificatePinsSha256 (coreDataModule).
    // STREAKS no incluye coreDataModule, por eso solo necesita Context.
    private val extraTypes: Map<AppFeature, List<KClass<*>>> =
        mapOf(
            AppFeature.AUTH to listOf(Context::class, HttpClientConfig::class, HttpClientEngine::class, List::class),
            AppFeature.TASKS to listOf(Context::class, HttpClientConfig::class, HttpClientEngine::class, List::class),
            AppFeature.STREAKS to listOf(Context::class),
        )

    private fun verifyFeature(feature: AppFeature) {
        FeatureContract.modules.getValue(feature).verify(extraTypes = extraTypes.getValue(feature))
    }

    private fun assertDeclaresExactly(feature: AppFeature) {
        val declared =
            koinApplication {
                allowOverride(false)
                modules(FeatureContract.modules.getValue(feature))
            }.koin.declaredTypes()
        val required = FeatureContract.requiredTypes.getValue(feature)
        val sobran = (declared - required).map { it.qualifiedName }
        val faltan = (required - declared).map { it.qualifiedName }
        assertTrue(
            sobran.isEmpty() && faltan.isEmpty(),
            "Contrato de $feature desincronizado. Sobran en el modulo: $sobran. Faltan en el modulo: $faltan",
        )
        assertEquals(required, declared)
    }

    @Test
    fun `AUTH resuelve sus constructores`() = verifyFeature(AppFeature.AUTH)

    @Test
    fun `TASKS resuelve sus constructores`() = verifyFeature(AppFeature.TASKS)

    @Test
    fun `STREAKS resuelve sus constructores`() = verifyFeature(AppFeature.STREAKS)

    @Test
    fun `AUTH declara exactamente los tipos del contrato`() = assertDeclaresExactly(AppFeature.AUTH)

    @Test
    fun `TASKS declara exactamente los tipos del contrato`() = assertDeclaresExactly(AppFeature.TASKS)

    @Test
    fun `STREAKS declara exactamente los tipos del contrato`() = assertDeclaresExactly(AppFeature.STREAKS)

    @Test
    fun `cargar todos los modulos de la app no redefine ninguna definicion`() {
        koinApplication {
            allowOverride(false)
            modules(appModules)
        }
    }
}
