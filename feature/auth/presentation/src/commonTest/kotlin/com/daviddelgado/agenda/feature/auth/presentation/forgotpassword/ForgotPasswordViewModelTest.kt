package com.daviddelgado.agenda.feature.auth.presentation.forgotpassword

import com.daviddelgado.agenda.feature.auth.domain.usecase.RequestPasswordResetUseCase
import com.daviddelgado.agenda.feature.auth.presentation.FakeAuthRepository
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun prepararDispatcherPrincipal() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun restaurarDispatcherPrincipal() {
        Dispatchers.resetMain()
    }

    private fun viewModelCon(repository: FakeAuthRepository) =
        ForgotPasswordViewModel(RequestPasswordResetUseCase(repository))

    @Test
    fun elFormularioGuardaElEmailEscrito() =
        runTest(dispatcher) {
            val viewModel = viewModelCon(FakeAuthRepository())

            viewModel.onIntent(ForgotPasswordIntent.EmailChanged("david@test.com"))

            assertEquals("david@test.com", viewModel.currentState.email)
        }

    @Test
    fun enviarConEmailVacioNoLlamaAlServidor() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(ForgotPasswordIntent.Submit)

            assertEquals("Escribe tu email", viewModel.currentState.errorMessage)
            assertNull(repository.emailPedido)
        }

    @Test
    fun pedirElCodigoConExitoDisparaCodeSent() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<ForgotPasswordEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.onIntent(ForgotPasswordIntent.EmailChanged("david@test.com"))
            viewModel.onIntent(ForgotPasswordIntent.Submit)

            assertEquals("david@test.com", repository.emailPedido)
            assertEquals(listOf<ForgotPasswordEffect>(ForgotPasswordEffect.CodeSent("david@test.com")), efectos)
        }

    @Test
    fun unFalloDelServidorMuestraElError() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(IllegalStateException("Error de red"))
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<ForgotPasswordEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.onIntent(ForgotPasswordIntent.EmailChanged("david@test.com"))
            viewModel.onIntent(ForgotPasswordIntent.Submit)

            assertEquals("Error de red", viewModel.currentState.errorMessage)
            assertTrue(efectos.any { it is ForgotPasswordEffect.ShowError })
        }
}
