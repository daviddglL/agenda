package com.daviddelgado.agenda.feature.tasks.domain.di

import com.daviddelgado.agenda.feature.tasks.domain.usecase.DeleteAllTasksUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.DeleteTaskUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.DeleteTasksUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.GenerateTaskRepetitionsUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.ObserveTaskChangesUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.ObserveTasksUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.SyncTasksUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.ToggleTaskCompletionUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.UpsertTaskUseCase
import org.koin.dsl.module

val tasksDomainModule =
    module {
        factory { ObserveTasksUseCase(get()) }
        factory { UpsertTaskUseCase(get()) }
        factory { GenerateTaskRepetitionsUseCase() }
        factory { DeleteTaskUseCase(get()) }
        factory { DeleteTasksUseCase(get()) }
        factory { DeleteAllTasksUseCase(get()) }
        factory { ToggleTaskCompletionUseCase(get()) }
        factory { SyncTasksUseCase(get()) }
        factory { ObserveTaskChangesUseCase(get()) }
    }
