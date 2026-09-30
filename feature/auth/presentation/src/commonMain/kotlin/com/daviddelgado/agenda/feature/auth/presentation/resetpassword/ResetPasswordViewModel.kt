package com.daviddelgado.agenda.feature.auth.presentation.resetpassword

import androidx.lifecycle.viewModelScope
import com.daviddelgado.agenda.core.presentation.mvi.MviViewModel
import com.daviddelgado.agenda.feature.auth.domain.usecase.ResetPasswordUseCase
import kotlinx.coroutines.launch

private const val CODE_LENGTH = 6
private const val MIN_PASSWORD_LENGTH = 6

class ResetPasswordViewModel(private val resetPasswordUseCase: ResetPasswordUseCase) :
    MviViewModel<ResetPasswordState, ResetPasswordIntent, ResetPasswordEffect>(ResetPasswordState()) {
    override fun onIntent(intent: ResetPasswordIntent) {
        when (intent) {
            is ResetPasswordIntent.EmailProvided -> setState { copy(email = intent.value) }
            is ResetPasswordIntent.CodeChanged -> setState { copy(code = intent.value, errorMessage = null) }
            is ResetPasswordIntent.NewPasswordChanged ->
                setState { copy(newPassword = intent.value, errorMessage = null) }
            is ResetPasswordIntent.ConfirmPasswordChanged ->
                setState { copy(confirmPassword = intent.value, errorMessage = null) }
            ResetPasswordIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val state = currentState
        if (state.code.length != CODE_LENGTH || state.code.any { !it.isDigit() }) {
            setState { copy(errorMessage = "El codigo tiene que tener 6 numeros") }
            return
        }
        if (state.newPassword.length < MIN_PASSWORD_LENGTH) {
            setState { copy(errorMessage = "La contrasena tiene que tener al menos 6 caracteres") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            setState { copy(errorMessage = "Las contrasenas no coinciden") }
            return
        }
        viewModelScope.launch {
            setState { copy(isLoading = true, errorMessage = null) }
            resetPasswordUseCase(state.email, state.code, state.newPassword)
                .onSuccess {
                    setState { copy(isLoading = false) }
                    sendEffect(ResetPasswordEffect.PasswordReset)
                }
                .onFailure { error ->
                    setState {
                        copy(isLoading = false, errorMessage = error.message ?: "Error al restablecer la contrasena")
                    }
                    sendEffect(ResetPasswordEffect.ShowError(error.message ?: "Error al restablecer la contrasena"))
                }
        }
    }
}
