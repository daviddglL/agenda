package com.daviddelgado.agenda.feature.auth.domain.usecase

import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository

/** Registra en el servidor el token FCM de este dispositivo para recibir recordatorios push. */
class RegisterFcmTokenUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(token: String): Result<Unit> = repository.registerFcmToken(token)
}
