package com.daviddelgado.agenda.android

import android.app.Application
import com.daviddelgado.agenda.shared.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class AgendaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            appDeclaration = {
                androidLogger()
                androidContext(this@AgendaApplication)
            },
        )
    }
}
