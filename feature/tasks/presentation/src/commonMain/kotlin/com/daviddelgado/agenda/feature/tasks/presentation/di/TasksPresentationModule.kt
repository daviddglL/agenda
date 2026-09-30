package com.daviddelgado.agenda.feature.tasks.presentation.di

import com.daviddelgado.agenda.feature.tasks.presentation.calendar.CalendarViewModel
import com.daviddelgado.agenda.feature.tasks.presentation.tasks.TasksViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val tasksPresentationModule =
    module {
        viewModelOf(::TasksViewModel)
        viewModelOf(::CalendarViewModel)
    }
