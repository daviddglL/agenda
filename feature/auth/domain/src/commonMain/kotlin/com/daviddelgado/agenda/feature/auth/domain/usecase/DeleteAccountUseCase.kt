package com.daviddelgado.agenda.feature.auth.domain.usecase

import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository

/** Borra la cuenta y, en cascada, todas sus tareas (servidor + Room local). */
class DeleteAccountUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.deleteAccount()
}
