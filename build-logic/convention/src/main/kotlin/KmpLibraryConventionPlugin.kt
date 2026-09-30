import com.android.build.api.dsl.LibraryExtension
import com.daviddelgado.agenda.convention.COMPILE_SDK
import com.daviddelgado.agenda.convention.JVM_TARGET
import com.daviddelgado.agenda.convention.MIN_SDK
import com.daviddelgado.agenda.convention.lib
import com.daviddelgado.agenda.convention.libs
import com.daviddelgado.agenda.convention.pathToNamespace
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Modulo KMP sin UI: Android + los 3 targets iOS, Java 17, SDKs comunes y namespace desde el path. */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.library")

            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain(JVM_TARGET)
                androidTarget()
                iosX64()
                iosArm64()
                iosSimulatorArm64()
            }

            extensions.configure<LibraryExtension> {
                namespace = pathToNamespace()
                compileSdk = COMPILE_SDK
                defaultConfig {
                    minSdk = MIN_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                // Los tests unitarios de Android que pasan por android.util.Log (Napier, via
                // AgendaLogger) reciben valores por defecto en vez de "Method not mocked".
                testOptions.unitTests.isReturnDefaultValues = true
            }

            dependencies {
                "commonMainImplementation"(libs.lib("kotlinx-coroutines-core"))
                "commonMainImplementation"(libs.lib("kotlinx-datetime"))
                "commonMainImplementation"(libs.lib("koin-core"))
                "commonTestImplementation"(kotlin("test"))
                "commonTestImplementation"(libs.lib("kotlinx-coroutines-test"))
            }
        }
    }
}
