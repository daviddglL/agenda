package com.daviddelgado.agenda.shared.navigation

import com.daviddelgado.agenda.feature.auth.domain.fcm.FcmTokenProvider
import com.daviddelgado.agenda.feature.auth.domain.model.User
import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository
import com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterFcmTokenUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RestoreSessionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** AuthRepository falso: solo hace falta restoreSession/registerFcmToken para este test. */
private class FakeAuthRepository(private val user: User?) : AuthRepository {
    private val current = MutableStateFlow(user)
    var tokenRegistrado: String? = null
        private set

    override fun observeCurrentUser(): Flow<User?> = current

    override suspend fun login(
        email: String,
        password: String,
    ): Result<User> = error("no usado en este test")

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Result<User> = error("no usado en este test")

    override suspend fun logout() = Unit

    override suspend fun restoreSession(): User? = user

    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

    override suspend fun registerFcmToken(token: String): Result<Unit> {
        tokenRegistrado = token
        return Result.success(Unit)
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> = Result.success(Unit)
}

/** FcmTokenProvider falso: evita depender de Firebase real en los tests (Task 10). */
private class FakeFcmTokenProvider(private val token: String? = "token-fcm-fake") : FcmTokenProvider {
    override suspend fun currentToken(): String? = token
}

class SplashSessionHandlerTest {
    @Test
    fun conSesionValidaRegistraElTokenFcmEnElServidor() =
        runTest {
            val repository = FakeAuthRepository(User("u-1", "David", "david@test.com"))
            val handler =
                SplashSessionHandler(
                    RestoreSessionUseCase(repository),
                    RegisterFcmTokenUseCase(repository),
                    FakeFcmTokenProvider("token-fcm-fake"),
                )

            val user = handler.restoreSession()

            assertEquals("u-1", user?.id)
            assertEquals("token-fcm-fake", repository.tokenRegistrado)
        }

    @Test
    fun sinSesionGuardadaNoRegistraNingunToken() =
        runTest {
            val repository = FakeAuthRepository(null)
            val handler =
                SplashSessionHandler(
                    RestoreSessionUseCase(repository),
                    RegisterFcmTokenUseCase(repository),
                    FakeFcmTokenProvider("token-fcm-fake"),
                )

            val user = handler.restoreSession()

            assertNull(user)
            assertNull(repository.tokenRegistrado)
        }

    @Test
    fun conSesionValidaPeroSinTokenDisponibleNoLlamaAlServidor() =
        runTest {
            val repository = FakeAuthRepository(User("u-1", "David", "david@test.com"))
            val handler =
                SplashSessionHandler(
                    RestoreSessionUseCase(repository),
                    RegisterFcmTokenUseCase(repository),
                    FakeFcmTokenProvider(token = null),
                )

            handler.restoreSession()

            assertNull(repository.tokenRegistrado)
        }
}
