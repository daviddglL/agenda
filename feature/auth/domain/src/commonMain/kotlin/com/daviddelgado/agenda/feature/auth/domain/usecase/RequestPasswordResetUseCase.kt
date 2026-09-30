package com.daviddelgado.agenda.feature.auth.domain.usecase

import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository

/** Pide al servidor un codigo de recuperacion de contrasena para este email (si existe la cuenta). */
class RequestPasswordResetUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): Result<Unit> = repository.requestPasswordReset(email)
}
