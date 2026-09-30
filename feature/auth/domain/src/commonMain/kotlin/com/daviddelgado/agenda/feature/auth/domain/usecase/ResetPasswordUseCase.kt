package com.daviddelgado.agenda.feature.auth.domain.usecase

import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository

/** Cambia la contrasena usando el codigo recibido por email. */
class ResetPasswordUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> = repository.resetPassword(email, code, newPassword)
}
