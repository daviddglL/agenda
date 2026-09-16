package com.daviddelgado.agenda.domain.usecase

import com.daviddelgado.agenda.domain.model.User
import com.daviddelgado.agenda.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository(
    private val user: User = User("u-1", "David", "david@test.com"),
    private val failWith: Throwable? = null,
) : AuthRepository {
    private val current = MutableStateFlow<User?>(null)
    var sessionRestored: User? = null
    var loggedOut = false
        private set
    var accountDeleted = false
        private set
    var tokenRegistrado: String? = null
    var passwordResetRequestedFor: String? = null
    var passwordWasReset = false
        private set

    override fun observeCurrentUser(): Flow<User?> = current

    override suspend fun login(
        email: String,
        password: String,
    ): Result<User> = authenticate()

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Result<User> = authenticate()

    override suspend fun logout() {
        loggedOut = true
        current.value = null
    }

    override suspend fun restoreSession(): User? = sessionRestored?.also { current.value = it }

    override suspend fun deleteAccount(): Result<Unit> {
        failWith?.let { return Result.failure(it) }
        accountDeleted = true
        current.value = null
        return Result.success(Unit)
    }

    override suspend fun registerFcmToken(token: String): Result<Unit> {
        tokenRegistrado = token
        return Result.success(Unit)
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        passwordResetRequestedFor = email
        return Result.success(Unit)
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> {
        failWith?.let { return Result.failure(it) }
        passwordWasReset = true
        return Result.success(Unit)
    }

    private fun authenticate(): Result<User> {
        failWith?.let { return Result.failure(it) }
        current.value = user
        return Result.success(user)
    }
}
