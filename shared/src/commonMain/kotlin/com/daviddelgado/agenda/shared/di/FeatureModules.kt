package com.daviddelgado.agenda.shared.di

import com.daviddelgado.agenda.core.data.di.coreDataModule
import com.daviddelgado.agenda.feature.auth.data.di.authDataModule
import com.daviddelgado.agenda.feature.auth.domain.di.authDomainModule
import com.daviddelgado.agenda.feature.auth.presentation.di.authPresentationModule
import com.daviddelgado.agenda.feature.streaks.data.di.streaksDataModule
import com.daviddelgado.agenda.feature.streaks.domain.di.streaksDomainModule
import com.daviddelgado.agenda.feature.streaks.presentation.di.streaksPresentationModule
import com.daviddelgado.agenda.feature.tasks.data.di.tasksDataModule
import com.daviddelgado.agenda.feature.tasks.database.di.tasksDatabaseModule
import com.daviddelgado.agenda.feature.tasks.domain.di.tasksDomainModule
import com.daviddelgado.agenda.feature.tasks.presentation.di.tasksPresentationModule
import org.koin.dsl.module

/**
 * Un modulo Koin por feature con TODAS sus capas y lo que necesitan de otras (core, BD de
 * tareas). La app solo lista features: es imposible olvidar una capa. Koin carga una sola vez
 * los modulos incluidos desde varias features (coreDataModule, tasksDatabaseModule).
 */
val authFeatureModule =
    module {
        // tasksDatabaseModule: AuthRepositoryImpl vacia las tareas locales en logout/borrar cuenta.
        includes(coreDataModule, tasksDatabaseModule, authDomainModule, authDataModule, authPresentationModule)
    }

val tasksFeatureModule =
    module {
        includes(coreDataModule, tasksDatabaseModule, tasksDomainModule, tasksDataModule, tasksPresentationModule)
    }

val streaksFeatureModule =
    module {
        // La racha se calcula con las tareas completadas (TaskDao).
        includes(tasksDatabaseModule, streaksDomainModule, streaksDataModule, streaksPresentationModule)
    }
