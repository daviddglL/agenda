plugins {
    id("agenda.kmp.library")
    id("agenda.room")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
