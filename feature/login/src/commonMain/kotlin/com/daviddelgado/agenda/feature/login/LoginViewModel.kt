package com.daviddelgado.agenda.feature.login

import androidx.lifecycle.viewModelScope
import com.daviddelgado.agenda.common.mvi.MviViewModel
import com.daviddelgado.agenda.domain.usecase.LoginUseCase
import kotlinx.coroutines.launch

class LoginViewModel(private val loginUseCase: LoginUseCase) :
    MviViewModel<LoginState, LoginIntent, LoginEffect>(LoginState()) {
    override fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged -> setState { copy(email = intent.value, errorMessage = null) }
            is LoginIntent.PasswordChanged -> setState { copy(password = intent.value, errorMessage = null) }
            LoginIntent.NavigateToRegister -> sendEffect(LoginEffect.NavigateToRegister)
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
                    sendEffect(LoginEffect.NavigateToHome)
                }
                .onFailure { error ->
                    setState { copy(isLoading = false, errorMessage = error.message ?: "Error de inicio de sesion") }
                    sendEffect(LoginEffect.ShowError(error.message ?: "Error de inicio de sesion"))
                }
        }
    }
}
