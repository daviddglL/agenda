plugins {
    id("agenda.cmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.components.resources)
        }
    }
}
