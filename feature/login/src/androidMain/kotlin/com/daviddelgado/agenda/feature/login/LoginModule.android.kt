package com.daviddelgado.agenda.feature.login

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.resume

/** Envuelve en una funcion suspend el callback de Firebase (Task/addOnSuccessListener). */
private class AndroidFcmTokenProvider : FcmTokenProvider {
    override suspend fun currentToken(): String? =
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> continuation.resume(token) }
                .addOnFailureListener { continuation.resume(null) }
        }
}

actual val platformLoginModule: Module =
    module {
        single<FcmTokenProvider> { AndroidFcmTokenProvider() }
    }
