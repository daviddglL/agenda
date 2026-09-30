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
            implementation(projects.core.domain)
            implementation(projects.core.designsystem)
            implementation(projects.core.data)
            implementation(projects.feature.auth.domain)
            implementation(projects.feature.auth.data)
            implementation(projects.feature.auth.presentation)
            implementation(projects.feature.tasks.domain)
            implementation(projects.feature.tasks.data)
            implementation(projects.feature.tasks.database)
            implementation(projects.feature.tasks.presentation)
            implementation(projects.feature.streaks.domain)
            implementation(projects.feature.streaks.data)
            implementation(projects.feature.streaks.presentation)
            implementation(libs.jetbrains.compose.materialIconsExtended)
            implementation(libs.koin.compose)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        androidUnitTest.dependencies {
            implementation(libs.koin.test)
        }
    }
}
