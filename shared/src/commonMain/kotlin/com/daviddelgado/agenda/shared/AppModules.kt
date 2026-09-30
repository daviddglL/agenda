package com.daviddelgado.agenda.shared

import com.daviddelgado.agenda.data.di.dataModule
import com.daviddelgado.agenda.domain.di.domainModule
import com.daviddelgado.agenda.feature.auth.data.di.authDataModule
import com.daviddelgado.agenda.feature.auth.domain.di.authDomainModule
import com.daviddelgado.agenda.feature.auth.presentation.di.authPresentationModule
import com.daviddelgado.agenda.feature.calendar.calendarModule
import com.daviddelgado.agenda.feature.streaks.streaksModule
import com.daviddelgado.agenda.feature.tasks.tasksModule
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
        dataModule,
        domainModule,
        authDomainModule,
        authDataModule,
        authPresentationModule,
        calendarModule,
        tasksModule,
        streaksModule,
        sharedModule,
    )
