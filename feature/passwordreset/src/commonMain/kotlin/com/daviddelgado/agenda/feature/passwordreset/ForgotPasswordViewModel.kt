package com.daviddelgado.agenda.feature.passwordreset

import androidx.lifecycle.viewModelScope
import com.daviddelgado.agenda.common.mvi.MviViewModel
import com.daviddelgado.agenda.domain.usecase.RequestPasswordResetUseCase
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(private val requestPasswordResetUseCase: RequestPasswordResetUseCase) :
    MviViewModel<ForgotPasswordState, ForgotPasswordIntent, ForgotPasswordEffect>(ForgotPasswordState()) {
    override fun onIntent(intent: ForgotPasswordIntent) {
        when (intent) {
            is ForgotPasswordIntent.EmailChanged -> setState { copy(email = intent.value, errorMessage = null) }
            ForgotPasswordIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val email = currentState.email
        if (email.isBlank()) {
            setState { copy(errorMessage = "Escribe tu email") }
            return
        }
        viewModelScope.launch {
            setState { copy(isLoading = true, errorMessage = null) }
            requestPasswordResetUseCase(email)
                .onSuccess {
                    setState { copy(isLoading = false) }
                    sendEffect(ForgotPasswordEffect.CodeSent(email))
                }
                .onFailure { error ->
                    setState { copy(isLoading = false, errorMessage = error.message ?: "Error al pedir el codigo") }
                    sendEffect(ForgotPasswordEffect.ShowError(error.message ?: "Error al pedir el codigo"))
                }
        }
    }
}
