package com.daviddelgado.agenda.feature.tasks

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tasksModule =
    module {
        viewModel { TasksViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    }
