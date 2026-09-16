package com.daviddelgado.agenda.feature.login

import androidx.lifecycle.viewModelScope
import com.daviddelgado.agenda.common.logging.AgendaLogger
import com.daviddelgado.agenda.common.mvi.MviViewModel
import com.daviddelgado.agenda.domain.usecase.LoginUseCase
import com.daviddelgado.agenda.domain.usecase.RegisterFcmTokenUseCase
import kotlinx.coroutines.launch

private const val LOG_TAG = "LoginViewModel"

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val registerFcmTokenUseCase: RegisterFcmTokenUseCase,
    private val fcmTokenProvider: FcmTokenProvider,
) : MviViewModel<LoginState, LoginIntent, LoginEffect>(LoginState()) {
    override fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged -> setState { copy(email = intent.value, errorMessage = null) }
            is LoginIntent.PasswordChanged -> setState { copy(password = intent.value, errorMessage = null) }
            LoginIntent.NavigateToRegister -> sendEffect(LoginEffect.NavigateToRegister)
            LoginIntent.NavigateToForgotPassword -> sendEffect(LoginEffect.NavigateToForgotPassword)
            LoginIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val email = currentState.email
        val password = currentState.password
        if (email.isBlank() || password.isBlank()) {
            setState { copy(errorMessage = "Introduce email y contrasena") }
            return
        }
        viewModelScope.launch {
            setState { copy(isLoading = true, errorMessage = null) }
            loginUseCase(email, password)
                .onSuccess {
                    setState { copy(isLoading = false) }
                    registrarTokenFcm()
                    sendEffect(LoginEffect.NavigateToHome)
                }
                .onFailure { error ->
                    setState { copy(isLoading = false, errorMessage = error.message ?: "Error de inicio de sesion") }
                    sendEffect(LoginEffect.ShowError(error.message ?: "Error de inicio de sesion"))
                }
        }
    }

    /** Tras iniciar sesion, manda al servidor el token push de este dispositivo (Task 10). */
    private fun registrarTokenFcm() {
        viewModelScope.launch {
            fcmTokenProvider.currentToken()?.let { token ->
                registerFcmTokenUseCase(token)
                    .onFailure { AgendaLogger.w(LOG_TAG, "No se pudo registrar el token FCM tras iniciar sesion", it) }
            }
        }
    }
}
