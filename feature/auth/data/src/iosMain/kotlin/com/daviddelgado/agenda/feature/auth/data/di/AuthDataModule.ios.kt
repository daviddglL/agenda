package com.daviddelgado.agenda.feature.auth.data.di

import com.daviddelgado.agenda.feature.auth.domain.fcm.FcmTokenProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * TODO(produccion-iOS): implementar con el Firebase Messaging del SDK de iOS cuando haya un Mac
 * disponible para probarlo en Xcode, igual que el resto de TODOs de plataforma iOS del proyecto
 * (ver core/data/.../CoreDataModule.ios.kt). De momento no manda el token push del dispositivo.
 */
private class NoopFcmTokenProvider : FcmTokenProvider {
    override suspend fun currentToken(): String? = null
}

actual val platformAuthDataModule: Module =
    module {
        single<FcmTokenProvider> { NoopFcmTokenProvider() }
    }
