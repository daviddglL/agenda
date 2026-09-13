package com.daviddelgado.agenda.feature.calendar

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val calendarModule =
    module {
        viewModel { CalendarViewModel(get()) }
    }
