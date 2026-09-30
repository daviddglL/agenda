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
import com.daviddelgado.agenda.shared.navigation.SplashSessionHandler
import org.koin.core.module.Module
import org.koin.dsl.module

/** SplashSessionHandler vive aqui (no en domainModule): compone un caso de uso de dominio con
 * FcmTokenProvider (feature:auth:domain), cuya implementacion real depende de la plataforma (feature:auth:data). */
private val sharedModule =
    module {
        single { SplashSessionHandler(get(), get(), get()) }
    }

val appModules: List<Module> =
    listOf(
        coreDataModule,
        tasksDatabaseModule,
        authDomainModule,
        authDataModule,
        authPresentationModule,
        tasksDomainModule,
        tasksDataModule,
        tasksPresentationModule,
        streaksDomainModule,
        streaksDataModule,
        streaksPresentationModule,
        sharedModule,
    )
