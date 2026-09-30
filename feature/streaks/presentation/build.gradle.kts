plugins {
    id("agenda.cmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.streaks.domain)
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
        }
    }
}
