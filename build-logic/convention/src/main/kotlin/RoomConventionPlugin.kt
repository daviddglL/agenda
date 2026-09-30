import androidx.room.gradle.RoomExtension
import com.daviddelgado.agenda.convention.lib
import com.daviddelgado.agenda.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room KMP: KSP por target y esquemas exportados en <modulo>/schemas (se versionan en git). */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("androidx.room")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                "commonMainApi"(libs.lib("androidx-room-runtime"))
                "kspAndroid"(libs.lib("androidx-room-compiler"))
                "kspIosX64"(libs.lib("androidx-room-compiler"))
                "kspIosArm64"(libs.lib("androidx-room-compiler"))
                "kspIosSimulatorArm64"(libs.lib("androidx-room-compiler"))
            }
        }
    }
}
