plugins {
    id("agenda.cmp.library")
}

kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.feature.login)
            implementation(projects.feature.register)
            implementation(projects.feature.passwordreset)
            implementation(projects.feature.calendar)
            implementation(projects.feature.tasks)
            implementation(projects.feature.streaks)
            implementation(projects.feature.settings)
            implementation(libs.jetbrains.compose.materialIconsExtended)
            implementation(libs.koin.compose)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
