plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.domain)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.firebase.messaging)
        }

        // Tests de UI de Compose (punto 5 de markdown.md): corren en un dispositivo o
        // emulador Android real, montando la pantalla con un ViewModel real + un fake propio
        // (no se reutiliza el de LoginViewModelTest.kt: es privado a ese fichero y vive en
        // un source set de test distinto, commonTest, que este no hereda por defecto).
        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.compose.ui.test.manifest)
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.daviddelgado.agenda.feature.login"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// El DSL de dependencias de Kotlin Multiplatform (arriba, "androidMain.dependencies") no
// resuelve bien un BOM (platform(...)); se añade aqui, con el DSL clasico de Gradle, igual
// que hace androidApp/build.gradle.kts.
dependencies {
    add("androidMainImplementation", platform(libs.firebase.bom))
}
