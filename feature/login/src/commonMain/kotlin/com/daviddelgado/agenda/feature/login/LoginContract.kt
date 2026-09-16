package com.daviddelgado.agenda.feature.login

import com.daviddelgado.agenda.common.mvi.UiEffect
import com.daviddelgado.agenda.common.mvi.UiIntent
import com.daviddelgado.agenda.common.mvi.UiState

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) : UiState

sealed interface LoginIntent : UiIntent {
    data class EmailChanged(val value: String) : LoginIntent

    data class PasswordChanged(val value: String) : LoginIntent

    data object Submit : LoginIntent

    data object NavigateToRegister : LoginIntent

    data object NavigateToForgotPassword : LoginIntent
}

sealed interface LoginEffect : UiEffect {
    data object NavigateToHome : LoginEffect

    data object NavigateToRegister : LoginEffect

    data object NavigateToForgotPassword : LoginEffect

    data class ShowError(val message: String) : LoginEffect
}
