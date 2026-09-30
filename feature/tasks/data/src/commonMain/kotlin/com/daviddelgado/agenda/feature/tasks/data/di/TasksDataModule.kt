package com.daviddelgado.agenda.feature.tasks.data.di

import com.daviddelgado.agenda.feature.tasks.data.remote.TaskApi
import com.daviddelgado.agenda.feature.tasks.data.repository.TaskRepositoryImpl
import com.daviddelgado.agenda.feature.tasks.domain.repository.TaskRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val tasksDataModule =
    module {
        singleOf(::TaskApi)
        singleOf(::TaskRepositoryImpl) { bind<TaskRepository>() }
    }
