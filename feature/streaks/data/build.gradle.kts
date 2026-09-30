plugins {
    id("agenda.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.streaks.domain)
            // Temporal: pasa a projects.feature.tasks.database en la Tarea 4. La racha se calcula
            // con las fechas de las tareas completadas (excepcion documentada en la spec).
            implementation(projects.core.database)
        }
    }
}
