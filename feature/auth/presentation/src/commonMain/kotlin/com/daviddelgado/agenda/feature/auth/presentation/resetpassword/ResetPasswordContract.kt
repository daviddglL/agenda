package com.daviddelgado.agenda.feature.auth.presentation.resetpassword

import com.daviddelgado.agenda.core.presentation.mvi.UiEffect
import com.daviddelgado.agenda.core.presentation.mvi.UiIntent
import com.daviddelgado.agenda.core.presentation.mvi.UiState

data class ResetPasswordState(
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) : UiState

sealed interface ResetPasswordIntent : UiIntent {
    data class EmailProvided(val value: String) : ResetPasswordIntent

    data class CodeChanged(val value: String) : ResetPasswordIntent

    data class NewPasswordChanged(val value: String) : ResetPasswordIntent

    data class ConfirmPasswordChanged(val value: String) : ResetPasswordIntent

    data object Submit : ResetPasswordIntent
}

sealed interface ResetPasswordEffect : UiEffect {
    data object PasswordReset : ResetPasswordEffect

    data class ShowError(val message: String) : ResetPasswordEffect
}
