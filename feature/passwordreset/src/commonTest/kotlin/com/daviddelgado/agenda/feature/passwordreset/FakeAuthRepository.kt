package com.daviddelgado.agenda.feature.passwordreset

import com.daviddelgado.agenda.domain.model.User
import com.daviddelgado.agenda.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository(private val error: Throwable? = null) : AuthRepository {
    private val current = MutableStateFlow<User?>(null)
    var emailPedido: String? = null
        private set
    var resetRealizadoCon: Triple<String, String, String>? = null
        private set

    override fun observeCurrentUser(): Flow<User?> = current

    override suspend fun login(
        email: String,
        password: String,
    ): Result<User> = Result.success(User("u-1", "David", email))

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Result<User> = Result.success(User("u-1", name, email))

    override suspend fun logout() = Unit

    override suspend fun restoreSession(): User? = null

    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

    override suspend fun registerFcmToken(token: String): Result<Unit> = Result.success(Unit)

    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        emailPedido = email
        return error?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> {
        error?.let { return Result.failure(it) }
        resetRealizadoCon = Triple(email, code, newPassword)
        return Result.success(Unit)
    }
}
