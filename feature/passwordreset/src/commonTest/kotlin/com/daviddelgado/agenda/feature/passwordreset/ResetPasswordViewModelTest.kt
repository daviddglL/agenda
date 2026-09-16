package com.daviddelgado.agenda.feature.passwordreset

import com.daviddelgado.agenda.domain.usecase.ResetPasswordUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ResetPasswordViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun prepararDispatcherPrincipal() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun restaurarDispatcherPrincipal() {
        Dispatchers.resetMain()
    }

    private fun viewModelCon(repository: FakeAuthRepository) = ResetPasswordViewModel(ResetPasswordUseCase(repository))

    private fun ResetPasswordViewModel.rellenarFormulario(
        email: String = "david@test.com",
        code: String = "123456",
        newPassword: String = "nueva123",
        confirmPassword: String = "nueva123",
    ) {
        onIntent(ResetPasswordIntent.EmailProvided(email))
        onIntent(ResetPasswordIntent.CodeChanged(code))
        onIntent(ResetPasswordIntent.NewPasswordChanged(newPassword))
        onIntent(ResetPasswordIntent.ConfirmPasswordChanged(confirmPassword))
    }

    @Test
    fun unCodigoQueNoTieneSeisDigitosNoLlamaAlServidor() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)

            viewModel.rellenarFormulario(code = "123")
            viewModel.onIntent(ResetPasswordIntent.Submit)

            assertEquals("El codigo tiene que tener 6 numeros", viewModel.currentState.errorMessage)
            assertNull(repository.resetRealizadoCon)
        }

    @Test
    fun siLasContrasenasNoCoincidenNoSeResetea() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)

            viewModel.rellenarFormulario(confirmPassword = "otra-distinta")
            viewModel.onIntent(ResetPasswordIntent.Submit)

            assertEquals("Las contrasenas no coinciden", viewModel.currentState.errorMessage)
            assertNull(repository.resetRealizadoCon)
        }

    @Test
    fun elResetCorrectoDisparaPasswordReset() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<ResetPasswordEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.rellenarFormulario()
            viewModel.onIntent(ResetPasswordIntent.Submit)

            assertEquals(Triple("david@test.com", "123456", "nueva123"), repository.resetRealizadoCon)
            assertEquals(listOf<ResetPasswordEffect>(ResetPasswordEffect.PasswordReset), efectos)
        }

    @Test
    fun unCodigoInvalidoDelServidorMuestraElError() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(IllegalStateException("Codigo invalido o caducado"))
            val viewModel = viewModelCon(repository)

            viewModel.rellenarFormulario()
            viewModel.onIntent(ResetPasswordIntent.Submit)

            assertEquals("Codigo invalido o caducado", viewModel.currentState.errorMessage)
        }

    @Test
    fun elEmailLlegaFijoPorParametroSinFormulario() =
        runTest(dispatcher) {
            val viewModel = viewModelCon(FakeAuthRepository())

            viewModel.onIntent(ResetPasswordIntent.EmailProvided("desde-forgot@test.com"))

            assertEquals("desde-forgot@test.com", viewModel.currentState.email)
        }
}
