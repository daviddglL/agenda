package com.daviddelgado.agenda.feature.auth.domain.usecase

import com.daviddelgado.agenda.feature.auth.domain.model.User
import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(
        name: String,
        email: String,
        password: String,
    ): Result<User> = repository.register(name, email, password)
}
