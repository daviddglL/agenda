package com.daviddelgado.agenda.feature.auth.presentation.register

import androidx.lifecycle.viewModelScope
import com.daviddelgado.agenda.common.mvi.MviViewModel
import com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterUseCase
import kotlinx.coroutines.launch

class RegisterViewModel(private val registerUseCase: RegisterUseCase) :
    MviViewModel<RegisterState, RegisterIntent, RegisterEffect>(RegisterState()) {
    override fun onIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.NameChanged -> setState { copy(name = intent.value, errorMessage = null) }
            is RegisterIntent.EmailChanged -> setState { copy(email = intent.value, errorMessage = null) }
            is RegisterIntent.PasswordChanged -> setState { copy(password = intent.value, errorMessage = null) }
            is RegisterIntent.ConfirmPasswordChanged ->
                setState { copy(confirmPassword = intent.value, errorMessage = null) }
            RegisterIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val state = currentState
        if (state.name.isBlank() || state.email.isBlank() || state.password.isBlank()) {
            setState { copy(errorMessage = "Rellena todos los campos") }
            return
        }
        if (state.password != state.confirmPassword) {
            setState { copy(errorMessage = "Las contrasenas no coinciden") }
            return
        }
        viewModelScope.launch {
            setState { copy(isLoading = true, errorMessage = null) }
            registerUseCase(state.name, state.email, state.password)
                .onSuccess {
                    setState { copy(isLoading = false) }
                    sendEffect(RegisterEffect.NavigateToHome)
                }
                .onFailure { error ->
                    setState { copy(isLoading = false, errorMessage = error.message ?: "Error de registro") }
                    sendEffect(RegisterEffect.ShowError(error.message ?: "Error de registro"))
                }
        }
    }
}
