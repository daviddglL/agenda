package com.daviddelgado.agenda.domain.di

import com.daviddelgado.agenda.domain.usecase.DeleteAccountUseCase
import com.daviddelgado.agenda.domain.usecase.DeleteAllTasksUseCase
import com.daviddelgado.agenda.domain.usecase.DeleteTaskUseCase
import com.daviddelgado.agenda.domain.usecase.DeleteTasksUseCase
import com.daviddelgado.agenda.domain.usecase.GenerateTaskRepetitionsUseCase
import com.daviddelgado.agenda.domain.usecase.LoginUseCase
import com.daviddelgado.agenda.domain.usecase.LogoutUseCase
import com.daviddelgado.agenda.domain.usecase.ObserveCurrentUserUseCase
import com.daviddelgado.agenda.domain.usecase.ObserveStreakUseCase
import com.daviddelgado.agenda.domain.usecase.ObserveTaskChangesUseCase
import com.daviddelgado.agenda.domain.usecase.ObserveTasksUseCase
import com.daviddelgado.agenda.domain.usecase.RegisterFcmTokenUseCase
import com.daviddelgado.agenda.domain.usecase.RegisterUseCase
import com.daviddelgado.agenda.domain.usecase.RequestPasswordResetUseCase
import com.daviddelgado.agenda.domain.usecase.ResetPasswordUseCase
import com.daviddelgado.agenda.domain.usecase.RestoreSessionUseCase
import com.daviddelgado.agenda.domain.usecase.SyncTasksUseCase
import com.daviddelgado.agenda.domain.usecase.ToggleTaskCompletionUseCase
import com.daviddelgado.agenda.domain.usecase.UpsertTaskUseCase
import org.koin.dsl.module

val domainModule =
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
        factory { ObserveStreakUseCase(get()) }
        factory { LoginUseCase(get()) }
        factory { RegisterUseCase(get()) }
        factory { DeleteAccountUseCase(get()) }
        factory { RegisterFcmTokenUseCase(get()) }
        factory { RequestPasswordResetUseCase(get()) }
        factory { ResetPasswordUseCase(get()) }
        factory { RestoreSessionUseCase(get()) }
        factory { LogoutUseCase(get()) }
        factory { ObserveCurrentUserUseCase(get()) }
    }
