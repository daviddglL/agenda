import com.daviddelgado.agenda.convention.lib
import com.daviddelgado.agenda.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Capa presentation de una feature: Compose + Koin para ViewModels + tests de UI instrumentados
 * (punto 5 de markdown.md). Las dependencias de proyecto (core:*, feature:X:domain) las declara
 * cada modulo en su build.gradle.kts.
 */
class CmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("agenda.cmp.library")

            dependencies {
                "commonMainImplementation"(libs.lib("jetbrains-compose-materialIconsExtended"))
                "commonMainImplementation"(libs.lib("koin-compose"))
                "commonMainImplementation"(libs.lib("koin-compose-viewmodel"))

                "androidInstrumentedTestImplementation"(libs.lib("androidx-compose-ui-test-junit4"))
                "androidInstrumentedTestImplementation"(libs.lib("androidx-test-runner"))
                "androidInstrumentedTestImplementation"(libs.lib("androidx-test-ext-junit"))
                "androidInstrumentedTestImplementation"(libs.lib("androidx-compose-ui-test-manifest"))
                "androidInstrumentedTestImplementation"(libs.lib("androidx-activity-compose"))
                "androidInstrumentedTestImplementation"(libs.lib("kotlinx-coroutines-test"))
            }
        }
    }
}
