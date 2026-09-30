package com.daviddelgado.agenda.shared.di

import com.daviddelgado.agenda.shared.navigation.SplashSessionHandler
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** SplashSessionHandler vive aqui (no en domainModule): compone un caso de uso de dominio con
 * FcmTokenProvider (feature:auth:domain), cuya implementacion real depende de la plataforma (feature:auth:data). */
internal val sharedModule =
    module {
        singleOf(::SplashSessionHandler)
    }

val appModules: List<Module> =
    listOf(
        authFeatureModule,
        tasksFeatureModule,
        streaksFeatureModule,
        sharedModule,
    )
