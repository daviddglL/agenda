plugins {
    id("agenda.cmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.domain)
        }

        androidMain.dependencies {
            implementation(libs.firebase.messaging)
        }
    }
}

// El DSL de dependencias de Kotlin Multiplatform (arriba, "androidMain.dependencies") no
// resuelve bien un BOM (platform(...)); se añade aqui, con el DSL clasico de Gradle, igual
// que hace androidApp/build.gradle.kts.
dependencies {
    add("androidMainImplementation", platform(libs.firebase.bom))
}
