import com.daviddelgado.agenda.convention.lib
import com.daviddelgado.agenda.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Modulo KMP con Compose Multiplatform (UI compartida). */
class CmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("agenda.kmp.library")
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            dependencies {
                "commonMainImplementation"(libs.lib("jetbrains-compose-runtime"))
                "commonMainImplementation"(libs.lib("jetbrains-compose-foundation"))
                "commonMainImplementation"(libs.lib("jetbrains-compose-material3"))
                "commonMainImplementation"(libs.lib("jetbrains-compose-ui"))
            }
        }
    }
}
