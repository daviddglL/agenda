plugins {
    id("agenda.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.auth.domain)
            implementation(projects.core.common)
            implementation(projects.core.network)
            // Temporal: pasa a projects.feature.tasks.database en la Tarea 4. AuthRepositoryImpl
            // vacia las tareas locales al hacer logout / borrar la cuenta (excepcion documentada en la spec).
            implementation(projects.core.database)
            implementation(libs.ktor.client.auth)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
        }

        androidMain.dependencies {
            implementation(libs.firebase.messaging)
        }
    }
}

// El DSL de dependencias de Kotlin Multiplatform no resuelve bien un BOM (platform(...)); se
// añade con el DSL clasico de Gradle, igual que hace androidApp/build.gradle.kts.
dependencies {
    add("androidMainImplementation", platform(libs.firebase.bom))
}
