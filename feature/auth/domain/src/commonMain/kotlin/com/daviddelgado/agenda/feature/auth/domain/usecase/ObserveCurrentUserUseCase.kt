package com.daviddelgado.agenda.feature.auth.domain.usecase

import com.daviddelgado.agenda.feature.auth.domain.model.User
import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/** Usuario con sesion iniciada ahora mismo (null si no hay sesion), para pantallas como Ajustes. */
class ObserveCurrentUserUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Flow<User?> = repository.observeCurrentUser()
}
