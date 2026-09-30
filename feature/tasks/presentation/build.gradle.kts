plugins {
    id("agenda.cmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.tasks.domain)
            implementation(projects.core.presentation)
            implementation(projects.core.domain)
            implementation(projects.core.designsystem)
        }
    }
}
