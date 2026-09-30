plugins {
    id("agenda.cmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.domain)
        }
    }
}
