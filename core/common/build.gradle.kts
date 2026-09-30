plugins {
    id("agenda.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.napier)
        }
    }
}
