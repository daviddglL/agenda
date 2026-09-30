plugins {
    id("agenda.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.streaks.domain)
            // Excepcion documentada en la spec (§3): la racha se calcula con las fechas de las
            // tareas completadas.
            implementation(projects.feature.tasks.database)
        }
    }
}
