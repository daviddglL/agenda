plugins {
    `kotlin-dsl`
}

group = "com.daviddelgado.agenda.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.androidx.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "agenda.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("cmpLibrary") {
            id = "agenda.cmp.library"
            implementationClass = "CmpLibraryConventionPlugin"
        }
        register("cmpFeature") {
            id = "agenda.cmp.feature"
            implementationClass = "CmpFeatureConventionPlugin"
        }
        register("room") {
            id = "agenda.room"
            implementationClass = "RoomConventionPlugin"
        }
        register("androidApplication") {
            id = "agenda.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
