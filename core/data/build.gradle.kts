plugins {
    id("agenda.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.network)
            implementation(projects.core.common)
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
            implementation(libs.koin.android)
        }
    }
}

android {
    // Expone BuildConfig.DEBUG para elegir servidor de desarrollo vs ProductionConfig
    // (punto 4 de markdown.md) sin tocar codigo entre un build debug y uno release.
    buildFeatures {
        buildConfig = true
    }
}
