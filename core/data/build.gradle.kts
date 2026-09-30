plugins {
    id("agenda.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.database)
            implementation(projects.core.network)
            implementation(projects.core.common)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
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
