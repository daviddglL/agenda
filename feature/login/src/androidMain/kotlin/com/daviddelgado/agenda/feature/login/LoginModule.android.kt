package com.daviddelgado.agenda.feature.login

import com.daviddelgado.agenda.common.logging.AgendaLogger
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.resume

private const val LOG_TAG = "AndroidFcmTokenProvider"

/** Envuelve en una funcion suspend el callback de Firebase (Task/addOnSuccessListener). */
private class AndroidFcmTokenProvider : FcmTokenProvider {
    override suspend fun currentToken(): String? =
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> continuation.resume(token) }
                .addOnFailureListener { error ->
                    AgendaLogger.w(LOG_TAG, "No se pudo obtener el token FCM de este dispositivo", error)
                    continuation.resume(null)
                }
        }
}

actual val platformLoginModule: Module =
    module {
        single<FcmTokenProvider> { AndroidFcmTokenProvider() }
    }
