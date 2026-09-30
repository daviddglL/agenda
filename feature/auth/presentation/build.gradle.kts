plugins {
    id("agenda.cmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.auth.domain)
            implementation(projects.core.presentation)
            implementation(projects.core.domain)
            implementation(projects.core.designsystem)
        }
    }
}
