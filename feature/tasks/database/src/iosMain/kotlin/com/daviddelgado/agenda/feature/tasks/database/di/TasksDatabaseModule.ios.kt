package com.daviddelgado.agenda.feature.tasks.database.di

import com.daviddelgado.agenda.feature.tasks.database.DatabaseFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformTasksDatabaseModule: Module =
    module {
        single { DatabaseFactory() }
    }
