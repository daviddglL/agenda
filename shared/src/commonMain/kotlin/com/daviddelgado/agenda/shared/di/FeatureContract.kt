package com.daviddelgado.agenda.shared.di

import com.daviddelgado.agenda.core.data.networking.NetworkConfig
import com.daviddelgado.agenda.core.data.session.SecureStorage
import com.daviddelgado.agenda.core.data.session.TokenProvider
import com.daviddelgado.agenda.core.data.session.TokenProviderImpl
import com.daviddelgado.agenda.feature.auth.data.remote.AuthApi
import com.daviddelgado.agenda.feature.auth.data.repository.AuthRepositoryImpl
import com.daviddelgado.agenda.feature.auth.domain.fcm.FcmTokenProvider
import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository
import com.daviddelgado.agenda.feature.auth.domain.usecase.DeleteAccountUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.LoginUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.LogoutUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterFcmTokenUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RequestPasswordResetUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.ResetPasswordUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RestoreSessionUseCase
import com.daviddelgado.agenda.feature.auth.presentation.forgotpassword.ForgotPasswordViewModel
import com.daviddelgado.agenda.feature.auth.presentation.login.LoginViewModel
import com.daviddelgado.agenda.feature.auth.presentation.register.RegisterViewModel
import com.daviddelgado.agenda.feature.auth.presentation.resetpassword.ResetPasswordViewModel
import com.daviddelgado.agenda.feature.auth.presentation.settings.SettingsViewModel
import com.daviddelgado.agenda.feature.streaks.data.repository.StreakRepositoryImpl
import com.daviddelgado.agenda.feature.streaks.domain.repository.StreakRepository
import com.daviddelgado.agenda.feature.streaks.domain.usecase.ObserveStreakUseCase
import com.daviddelgado.agenda.feature.streaks.presentation.streaks.StreaksViewModel
import com.daviddelgado.agenda.feature.tasks.data.remote.TaskApi
import com.daviddelgado.agenda.feature.tasks.data.repository.TaskRepositoryImpl
import com.daviddelgado.agenda.feature.tasks.database.AgendaDatabase
import com.daviddelgado.agenda.feature.tasks.database.DatabaseFactory
import com.daviddelgado.agenda.feature.tasks.database.dao.PendingDeletionDao
import com.daviddelgado.agenda.feature.tasks.database.dao.TaskDao
import com.daviddelgado.agenda.feature.tasks.domain.repository.TaskRepository
import com.daviddelgado.agenda.feature.tasks.domain.usecase.DeleteAllTasksUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.DeleteTaskUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.DeleteTasksUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.GenerateTaskRepetitionsUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.ObserveTaskChangesUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.ObserveTasksUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.SyncTasksUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.ToggleTaskCompletionUseCase
import com.daviddelgado.agenda.feature.tasks.domain.usecase.UpsertTaskUseCase
import com.daviddelgado.agenda.feature.tasks.presentation.calendar.CalendarViewModel
import com.daviddelgado.agenda.feature.tasks.presentation.tasks.TasksViewModel
import com.daviddelgado.agenda.shared.navigation.SplashSessionHandler
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import kotlin.reflect.KClass

enum class AppFeature { AUTH, TASKS, STREAKS }

/**
 * Contrato explicito: que tipos debe declarar el modulo agregado de cada feature (incluidos los
 * de core/BD que incluye). Los tests exigen igualdad exacta en ambos sentidos: anadir o quitar
 * una definicion sin actualizar esta lista rompe un test. Tambien es la base de la comprobacion
 * de arranque (`missingTypes`).
 */
object FeatureContract {
    val modules: Map<AppFeature, Module> =
        mapOf(
            AppFeature.AUTH to authFeatureModule,
            AppFeature.TASKS to tasksFeatureModule,
            AppFeature.STREAKS to streaksFeatureModule,
        )

    // core:data (coreDataModule, con la parte de plataforma): config, almacen, token y cliente HTTP.
    private val coreDataTypes: Set<KClass<*>> =
        linkedSetOf(
            NetworkConfig::class,
            SecureStorage::class,
            TokenProviderImpl::class,
            TokenProvider::class,
            HttpClient::class,
        )

    // feature:tasks:database (tasksDatabaseModule, con DatabaseFactory de plataforma).
    private val tasksDatabaseTypes: Set<KClass<*>> =
        linkedSetOf(
            DatabaseFactory::class,
            AgendaDatabase::class,
            TaskDao::class,
            PendingDeletionDao::class,
        )

    private val authTypes: Set<KClass<*>> =
        linkedSetOf(
            // domain
            LoginUseCase::class,
            RegisterUseCase::class,
            DeleteAccountUseCase::class,
            RegisterFcmTokenUseCase::class,
            RequestPasswordResetUseCase::class,
            ResetPasswordUseCase::class,
            RestoreSessionUseCase::class,
            LogoutUseCase::class,
            ObserveCurrentUserUseCase::class,
            // data (FcmTokenProvider: su implementacion de plataforma es privada)
            FcmTokenProvider::class,
            AuthApi::class,
            AuthRepositoryImpl::class,
            AuthRepository::class,
            // presentation
            LoginViewModel::class,
            RegisterViewModel::class,
            ForgotPasswordViewModel::class,
            ResetPasswordViewModel::class,
            SettingsViewModel::class,
        )

    private val tasksTypes: Set<KClass<*>> =
        linkedSetOf(
            // domain
            ObserveTasksUseCase::class,
            UpsertTaskUseCase::class,
            GenerateTaskRepetitionsUseCase::class,
            DeleteTaskUseCase::class,
            DeleteTasksUseCase::class,
            DeleteAllTasksUseCase::class,
            ToggleTaskCompletionUseCase::class,
            SyncTasksUseCase::class,
            ObserveTaskChangesUseCase::class,
            // data
            TaskApi::class,
            TaskRepositoryImpl::class,
            TaskRepository::class,
            // presentation
            TasksViewModel::class,
            CalendarViewModel::class,
        )

    private val streaksTypes: Set<KClass<*>> =
        linkedSetOf(
            ObserveStreakUseCase::class,
            StreakRepositoryImpl::class,
            StreakRepository::class,
            StreaksViewModel::class,
        )

    val requiredTypes: Map<AppFeature, Set<KClass<*>>> =
        mapOf(
            AppFeature.AUTH to (coreDataTypes + tasksDatabaseTypes + authTypes),
            AppFeature.TASKS to (coreDataTypes + tasksDatabaseTypes + tasksTypes),
            AppFeature.STREAKS to (tasksDatabaseTypes + streaksTypes),
        )

    /**
     * Tipos de `sharedModule` que la app resuelve nada mas arrancar (el splash) y de los que
     * depende una feature. Solo entran en [missingTypes] (comprobacion de arranque), NO en
     * [requiredTypes]: la igualdad exacta con el modulo de la feature no debe cambiar.
     */
    private val appEntryTypes: Map<AppFeature, Set<KClass<*>>> =
        mapOf(AppFeature.AUTH to setOf(SplashSessionHandler::class))

    /** Por feature, los tipos del contrato que NO estan en [declared]; solo las features con alguno ausente. */
    fun missingTypes(declared: Set<KClass<*>>): Map<AppFeature, Set<KClass<*>>> =
        requiredTypes
            .mapValues { (feature, required) -> (required + appEntryTypes[feature].orEmpty()) - declared }
            .filterValues { it.isNotEmpty() }
}
