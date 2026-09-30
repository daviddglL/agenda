package com.daviddelgado.agenda.feature.tasks.database.di

import com.daviddelgado.agenda.feature.tasks.database.AgendaDatabase
import com.daviddelgado.agenda.feature.tasks.database.buildAgendaDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/** Cada plataforma provee su DatabaseFactory (en Android necesita el Context). */
expect val platformTasksDatabaseModule: Module

val tasksDatabaseModule =
    module {
        includes(platformTasksDatabaseModule)
        single { buildAgendaDatabase(get()) }
        single { get<AgendaDatabase>().taskDao() }
        single { get<AgendaDatabase>().pendingDeletionDao() }
    }
