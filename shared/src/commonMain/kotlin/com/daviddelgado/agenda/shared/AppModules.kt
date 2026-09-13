package com.daviddelgado.agenda.shared

import com.daviddelgado.agenda.data.di.dataModule
import com.daviddelgado.agenda.domain.di.domainModule
import com.daviddelgado.agenda.feature.calendar.calendarModule
import com.daviddelgado.agenda.feature.login.loginModule
import com.daviddelgado.agenda.feature.register.registerModule
import com.daviddelgado.agenda.feature.settings.settingsModule
import com.daviddelgado.agenda.feature.streaks.streaksModule
import com.daviddelgado.agenda.feature.tasks.tasksModule
import org.koin.core.module.Module

val appModules: List<Module> =
    listOf(
        dataModule,
        domainModule,
        loginModule,
        registerModule,
        calendarModule,
        tasksModule,
        streaksModule,
        settingsModule,
    )
