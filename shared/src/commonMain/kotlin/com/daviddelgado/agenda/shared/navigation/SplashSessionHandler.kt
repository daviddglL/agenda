package com.daviddelgado.agenda.shared.navigation

import com.daviddelgado.agenda.core.domain.logger.AgendaLogger
import com.daviddelgado.agenda.feature.auth.domain.fcm.FcmTokenProvider
import com.daviddelgado.agenda.feature.auth.domain.model.User
import com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterFcmTokenUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RestoreSessionUseCase

private const val LOG_TAG = "SplashSessionHandler"

/**
 * Logica de arranque de la app (Task 10): intenta recuperar la sesion guardada y, si sigue
 * siendo valida, aprovecha para (re)registrar el token push de este dispositivo en el
 * servidor. Cubre el caso mas comun (no solo el login): la sesion persiste hasta un logout
 * explicito, asi que la mayoria de arranques pasan por aqui y no por LoginViewModel.
 */
class SplashSessionHandler(
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val registerFcmTokenUseCase: RegisterFcmTokenUseCase,
    private val fcmTokenProvider: FcmTokenProvider,
) {
    suspend fun restoreSession(): User? {
        val user = restoreSessionUseCase()
        if (user != null) registrarTokenFcm()
        return user
    }

    private suspend fun registrarTokenFcm() {
        val token = fcmTokenProvider.currentToken() ?: return
        registerFcmTokenUseCase(token)
            .onFailure { AgendaLogger.w(LOG_TAG, "No se pudo registrar el token FCM al recuperar la sesion", it) }
    }
}
