import com.android.build.api.dsl.ApplicationExtension
import com.daviddelgado.agenda.convention.COMPILE_SDK
import com.daviddelgado.agenda.convention.JVM_TARGET
import com.daviddelgado.agenda.convention.MIN_SDK
import com.daviddelgado.agenda.convention.TARGET_SDK
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** App Android con Compose. applicationId, versiones, buildTypes y google-services van en el modulo. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<ApplicationExtension> {
                compileSdk = COMPILE_SDK
                defaultConfig {
                    minSdk = MIN_SDK
                    targetSdk = TARGET_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                buildFeatures.compose = true
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                jvmToolchain(JVM_TARGET)
            }
        }
    }
}
