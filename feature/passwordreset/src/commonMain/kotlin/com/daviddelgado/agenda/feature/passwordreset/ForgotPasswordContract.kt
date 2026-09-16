package com.daviddelgado.agenda.feature.passwordreset

import com.daviddelgado.agenda.common.mvi.UiEffect
import com.daviddelgado.agenda.common.mvi.UiIntent
import com.daviddelgado.agenda.common.mvi.UiState

data class ForgotPasswordState(
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) : UiState

sealed interface ForgotPasswordIntent : UiIntent {
    data class EmailChanged(val value: String) : ForgotPasswordIntent

    data object Submit : ForgotPasswordIntent
}

sealed interface ForgotPasswordEffect : UiEffect {
    data class CodeSent(val email: String) : ForgotPasswordEffect

    data class ShowError(val message: String) : ForgotPasswordEffect
}
