package com.daviddelgado.agenda.feature.auth.presentation.register

import com.daviddelgado.agenda.core.presentation.mvi.UiEffect
import com.daviddelgado.agenda.core.presentation.mvi.UiIntent
import com.daviddelgado.agenda.core.presentation.mvi.UiState

data class RegisterState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) : UiState

sealed interface RegisterIntent : UiIntent {
    data class NameChanged(val value: String) : RegisterIntent

    data class EmailChanged(val value: String) : RegisterIntent

    data class PasswordChanged(val value: String) : RegisterIntent

    data class ConfirmPasswordChanged(val value: String) : RegisterIntent

    data object Submit : RegisterIntent
}

sealed interface RegisterEffect : UiEffect {
    data object NavigateToHome : RegisterEffect

    data class ShowError(val message: String) : RegisterEffect
}
