plugins {
    id("agenda.cmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.streaks.domain)
            implementation(projects.core.presentation)
            implementation(projects.core.designsystem)
        }
    }
}
