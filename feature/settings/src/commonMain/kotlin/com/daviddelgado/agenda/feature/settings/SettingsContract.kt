package com.daviddelgado.agenda.feature.settings

import com.daviddelgado.agenda.common.mvi.UiEffect
import com.daviddelgado.agenda.common.mvi.UiIntent
import com.daviddelgado.agenda.common.mvi.UiState
import com.daviddelgado.agenda.domain.model.User

data class SettingsState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isDeleteAccountPending: Boolean = false,
) : UiState

sealed interface SettingsIntent : UiIntent {
    data object Logout : SettingsIntent

    data object RequestDeleteAccount : SettingsIntent

    data object ConfirmDeleteAccount : SettingsIntent

    data object CancelDeleteAccount : SettingsIntent
}

sealed interface SettingsEffect : UiEffect {
    /** Se emite tras cerrar sesion o borrar la cuenta: la navegacion vuelve al login. */
    data object NavigateToLogin : SettingsEffect

    data class ShowError(val message: String) : SettingsEffect
}
