package com.daviddelgado.agenda.feature.register

import com.daviddelgado.agenda.domain.model.User
import com.daviddelgado.agenda.domain.repository.AuthRepository
import com.daviddelgado.agenda.domain.usecase.RegisterUseCase
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

private class FakeAuthRepository(private val error: Throwable? = null) : AuthRepository {
    private val current = MutableStateFlow<User?>(null)
    var intentos = 0
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
    ): Result<User> {
        intentos++
        return error?.let { Result.failure(it) } ?: Result.success(User("u-1", name, email))
    }

    override suspend fun logout() = Unit

    override suspend fun restoreSession(): User? = null

    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

    override suspend fun registerFcmToken(token: String): Result<Unit> = Result.success(Unit)

    override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> = Result.success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun prepararDispatcherPrincipal() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun restaurarDispatcherPrincipal() {
        Dispatchers.resetMain()
    }

    private fun viewModelCon(repository: FakeAuthRepository) = RegisterViewModel(RegisterUseCase(repository))

    private fun RegisterViewModel.rellenarFormulario(
        password: String = "secreta123",
        confirmacion: String = "secreta123",
    ) {
        onIntent(RegisterIntent.NameChanged("David"))
        onIntent(RegisterIntent.EmailChanged("david@test.com"))
        onIntent(RegisterIntent.PasswordChanged(password))
        onIntent(RegisterIntent.ConfirmPasswordChanged(confirmacion))
    }

    @Test
    fun elFormularioGuardaLoQueEscribeElUsuario() =
        runTest(dispatcher) {
            val viewModel = viewModelCon(FakeAuthRepository())

            viewModel.rellenarFormulario()

            assertEquals("David", viewModel.currentState.name)
            assertEquals("david@test.com", viewModel.currentState.email)
            assertEquals("secreta123", viewModel.currentState.password)
            assertEquals("secreta123", viewModel.currentState.confirmPassword)
        }

    @Test
    fun enviarConCamposVaciosNoLlamaAlServidor() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(RegisterIntent.Submit)

            assertEquals("Rellena todos los campos", viewModel.currentState.errorMessage)
            assertEquals(0, repository.intentos)
        }

    @Test
    fun siLasContrasenasNoCoincidenNoSeRegistra() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)

            viewModel.rellenarFormulario(confirmacion = "otra-distinta")
            viewModel.onIntent(RegisterIntent.Submit)

            assertEquals("Las contrasenas no coinciden", viewModel.currentState.errorMessage)
            assertEquals(0, repository.intentos)
        }

    @Test
    fun elRegistroCorrectoNavegaAHome() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<RegisterEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.rellenarFormulario()
            viewModel.onIntent(RegisterIntent.Submit)

            assertEquals(listOf<RegisterEffect>(RegisterEffect.NavigateToHome), efectos)
            assertEquals(1, repository.intentos)
            assertFalse(viewModel.currentState.isLoading)
        }

    @Test
    fun siElEmailYaExisteSeMuestraElErrorDelServidor() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(IllegalStateException("Ya existe una cuenta con ese email"))
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<RegisterEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.rellenarFormulario()
            viewModel.onIntent(RegisterIntent.Submit)

            assertEquals("Ya existe una cuenta con ese email", viewModel.currentState.errorMessage)
            assertTrue(efectos.any { it is RegisterEffect.ShowError })
        }
}
