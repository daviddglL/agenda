package com.daviddelgado.agenda.feature.auth.presentation.forgotpassword

import com.daviddelgado.agenda.core.presentation.mvi.UiEffect
import com.daviddelgado.agenda.core.presentation.mvi.UiIntent
import com.daviddelgado.agenda.core.presentation.mvi.UiState

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
