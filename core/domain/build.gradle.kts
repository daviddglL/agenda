plugins {
    id("agenda.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.napier)
        }
    }
}
