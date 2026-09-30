package com.daviddelgado.agenda.feature.auth.presentation.settings

import com.daviddelgado.agenda.feature.auth.domain.model.User
import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository
import com.daviddelgado.agenda.feature.auth.domain.usecase.DeleteAccountUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.LogoutUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeAuthRepository(
    initialUser: User?,
    private val deleteError: Throwable? = null,
) : AuthRepository {
    private val current = MutableStateFlow(initialUser)
    var loggedOut = false
        private set
    var accountDeleted = false
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

    override suspend fun logout() {
        loggedOut = true
        current.value = null
    }

    override suspend fun restoreSession(): User? = current.value

    override suspend fun deleteAccount(): Result<Unit> {
        deleteError?.let { return Result.failure(it) }
        accountDeleted = true
        current.value = null
        return Result.success(Unit)
    }

    override suspend fun registerFcmToken(token: String): Result<Unit> = Result.success(Unit)

    override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> = Result.success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val usuario = User("u-1", "David", "david@test.com")

    @BeforeTest
    fun prepararDispatcherPrincipal() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun restaurarDispatcherPrincipal() {
        Dispatchers.resetMain()
    }

    private fun viewModelCon(repository: FakeAuthRepository) =
        SettingsViewModel(
            ObserveCurrentUserUseCase(repository),
            LogoutUseCase(repository),
            DeleteAccountUseCase(repository),
        )

    @Test
    fun elEstadoMuestraElUsuarioConSesionIniciada() =
        runTest(dispatcher) {
            val viewModel = viewModelCon(FakeAuthRepository(usuario))

            assertEquals(usuario, viewModel.currentState.user)
        }

    @Test
    fun cerrarSesionAvisaAlRepositorioYNavegaAlLogin() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(usuario)
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<SettingsEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.onIntent(SettingsIntent.Logout)

            assertTrue(repository.loggedOut)
            assertEquals(listOf<SettingsEffect>(SettingsEffect.NavigateToLogin), efectos)
        }

    @Test
    fun borrarLaCuentaPideConfirmacionAntes() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(usuario)
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(SettingsIntent.RequestDeleteAccount)

            assertTrue(viewModel.currentState.isDeleteAccountPending)
            assertFalse(repository.accountDeleted)
        }

    @Test
    fun cancelarElBorradoNoTocaLaCuenta() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(usuario)
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(SettingsIntent.RequestDeleteAccount)
            viewModel.onIntent(SettingsIntent.CancelDeleteAccount)

            assertFalse(viewModel.currentState.isDeleteAccountPending)
            assertFalse(repository.accountDeleted)
        }

    @Test
    fun confirmarElBorradoBorraLaCuentaYNavegaAlLogin() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(usuario)
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<SettingsEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.onIntent(SettingsIntent.RequestDeleteAccount)
            viewModel.onIntent(SettingsIntent.ConfirmDeleteAccount)

            assertTrue(repository.accountDeleted)
            assertEquals(listOf<SettingsEffect>(SettingsEffect.NavigateToLogin), efectos)
        }

    @Test
    fun siElServidorFallaAlBorrarSeMuestraElError() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(usuario, deleteError = IllegalStateException("Error del servidor"))
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<SettingsEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.onIntent(SettingsIntent.RequestDeleteAccount)
            viewModel.onIntent(SettingsIntent.ConfirmDeleteAccount)

            assertFalse(repository.accountDeleted)
            assertEquals("Error del servidor", viewModel.currentState.errorMessage)
            assertTrue(efectos.any { it is SettingsEffect.ShowError })
        }
}
