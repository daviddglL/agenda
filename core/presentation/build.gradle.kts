plugins {
    id("agenda.cmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.lifecycle.viewmodel)
            implementation(projects.core.domain)
        }
    }
}
