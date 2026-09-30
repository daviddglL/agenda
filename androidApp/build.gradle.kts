plugins {
    id("agenda.android.application")
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.daviddelgado.agenda.android"

    defaultConfig {
        applicationId = "com.daviddelgado.agenda"
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(projects.shared)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.network)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
