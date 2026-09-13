plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // Sin este filtro, ktlint tambien analiza codigo generado (KSP de Room,
        // recursos de Compose Multiplatform), que no controlamos y no debe lintarse.
        filter {
            exclude("**/build/**")
            exclude { entry -> entry.file.path.replace('\\', '/').contains("/build/") }
        }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
    }

    // Igual que con ktlint: el codigo generado (KSP de Room, recursos de Compose) no es
    // nuestro y no debe hacer fallar el analisis estatico.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        exclude("**/build/**")
        exclude { it.file.path.replace('\\', '/').contains("/build/") }
    }

    // En modulos Kotlin Multiplatform la tarea agregada "detekt" queda vacia (NO-SOURCE):
    // detekt genera una tarea por cada source set (detektMetadataCommonMain, detektAndroidDebug...).
    // Enganchamos todas esas tareas reales a "check" para que el analisis estatico se ejecute de verdad.
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(tasks.withType<io.gitlab.arturbosch.detekt.Detekt>())
    }
}
