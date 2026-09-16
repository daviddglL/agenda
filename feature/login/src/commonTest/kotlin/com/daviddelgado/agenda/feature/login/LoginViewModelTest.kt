package com.daviddelgado.agenda.feature.login

import com.daviddelgado.agenda.domain.model.User
import com.daviddelgado.agenda.domain.repository.AuthRepository
import com.daviddelgado.agenda.domain.usecase.LoginUseCase
import com.daviddelgado.agenda.domain.usecase.RegisterFcmTokenUseCase
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AuthRepository falso: solo hace falta el login para probar el reductor MVI. */
private class FakeAuthRepository(private val error: Throwable? = null) : AuthRepository {
    private val current = MutableStateFlow<User?>(null)
    var intentos = 0
        private set

    override fun observeCurrentUser(): Flow<User?> = current

    override suspend fun login(
        email: String,
        password: String,
    ): Result<User> {
        intentos++
        return error?.let { Result.failure(it) } ?: Result.success(User("u-1", "David", email))
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Result<User> = Result.success(User("u-1", name, email))

    override suspend fun logout() = Unit

    override suspend fun restoreSession(): User? = null

    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

    var tokenRegistrado: String? = null
        private set

    override suspend fun registerFcmToken(token: String): Result<Unit> {
        tokenRegistrado = token
        return Result.success(Unit)
    }
}

/** FcmTokenProvider falso: evita depender de Firebase real en los tests (Task 10). */
private class FakeFcmTokenProvider(private val token: String? = "token-fcm-fake") : FcmTokenProvider {
    override suspend fun currentToken(): String? = token
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun prepararDispatcherPrincipal() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun restaurarDispatcherPrincipal() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(
        repository: AuthRepository = FakeAuthRepository(),
        fcmTokenProvider: FcmTokenProvider = FakeFcmTokenProvider(),
    ) = LoginViewModel(LoginUseCase(repository), RegisterFcmTokenUseCase(repository), fcmTokenProvider)

    @Test
    fun escribirEmailYContrasenaActualizaElEstado() =
        runTest(dispatcher) {
            val viewModel = crearViewModel()

            viewModel.onIntent(LoginIntent.EmailChanged("david@test.com"))
            viewModel.onIntent(LoginIntent.PasswordChanged("secreta123"))

            assertEquals("david@test.com", viewModel.currentState.email)
            assertEquals("secreta123", viewModel.currentState.password)
        }

    @Test
    fun enviarConCamposVaciosNoLlamaAlServidorYAvisaAlUsuario() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = crearViewModel(repository)

            viewModel.onIntent(LoginIntent.Submit)

            assertEquals("Introduce email y contrasena", viewModel.currentState.errorMessage)
            assertEquals(0, repository.intentos)
        }

    @Test
    fun escribirDeNuevoLimpiaElErrorAnterior() =
        runTest(dispatcher) {
            val viewModel = crearViewModel()
            viewModel.onIntent(LoginIntent.Submit)

            viewModel.onIntent(LoginIntent.EmailChanged("david@test.com"))

            assertNull(viewModel.currentState.errorMessage)
        }

    @Test
    fun elLoginCorrectoNavegaAHomeYQuitaElCargando() =
        runTest(dispatcher) {
            val viewModel = crearViewModel()
            val efectos = mutableListOf<LoginEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.onIntent(LoginIntent.EmailChanged("david@test.com"))
            viewModel.onIntent(LoginIntent.PasswordChanged("secreta123"))
            viewModel.onIntent(LoginIntent.Submit)

            assertEquals(listOf<LoginEffect>(LoginEffect.NavigateToHome), efectos)
            assertFalse(viewModel.currentState.isLoading)
            assertNull(viewModel.currentState.errorMessage)
        }

    @Test
    fun elLoginFallidoMuestraElMensajeDelServidor() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(IllegalStateException("Email o contrasena incorrectos"))
            val viewModel = crearViewModel(repository)
            val efectos = mutableListOf<LoginEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.onIntent(LoginIntent.EmailChanged("david@test.com"))
            viewModel.onIntent(LoginIntent.PasswordChanged("mala"))
            viewModel.onIntent(LoginIntent.Submit)

            assertEquals("Email o contrasena incorrectos", viewModel.currentState.errorMessage)
            assertFalse(viewModel.currentState.isLoading)
            assertTrue(efectos.any { it is LoginEffect.ShowError })
        }

    @Test
    fun pedirRegistroEmiteElEfectoDeNavegacion() =
        runTest(dispatcher) {
            val viewModel = crearViewModel()
            val efectos = mutableListOf<LoginEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.onIntent(LoginIntent.NavigateToRegister)

            assertEquals(listOf<LoginEffect>(LoginEffect.NavigateToRegister), efectos)
        }

    @Test
    fun elLoginCorrectoRegistraElTokenFcmEnElServidor() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = crearViewModel(repository, FakeFcmTokenProvider("token-fcm-fake"))

            viewModel.onIntent(LoginIntent.EmailChanged("david@test.com"))
            viewModel.onIntent(LoginIntent.PasswordChanged("secreta123"))
            viewModel.onIntent(LoginIntent.Submit)

            assertEquals("token-fcm-fake", repository.tokenRegistrado)
        }

    @Test
    fun elLoginCorrectoSinTokenFcmDisponibleNoLlamaAlServidor() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = crearViewModel(repository, FakeFcmTokenProvider(token = null))

            viewModel.onIntent(LoginIntent.EmailChanged("david@test.com"))
            viewModel.onIntent(LoginIntent.PasswordChanged("secreta123"))
            viewModel.onIntent(LoginIntent.Submit)

            assertNull(repository.tokenRegistrado)
        }
}
