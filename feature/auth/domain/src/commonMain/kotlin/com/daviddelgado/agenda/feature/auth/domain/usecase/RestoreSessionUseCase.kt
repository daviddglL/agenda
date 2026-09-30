package com.daviddelgado.agenda.feature.auth.domain.usecase

import com.daviddelgado.agenda.feature.auth.domain.model.User
import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository

/** Sesion guardada al abrir la app: evita pedir login en cada arranque. */
class RestoreSessionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): User? = repository.restoreSession()
}
