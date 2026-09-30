plugins {
    id("agenda.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.tasks.domain)
            implementation(projects.feature.tasks.database)
            implementation(projects.core.common)
            implementation(projects.core.network)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
        }
    }
}
