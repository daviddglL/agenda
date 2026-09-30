plugins {
    id("agenda.kmp.library")
    id("agenda.room")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}
