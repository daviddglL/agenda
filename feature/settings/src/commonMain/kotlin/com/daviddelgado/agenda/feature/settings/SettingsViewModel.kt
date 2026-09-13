package com.daviddelgado.agenda.feature.settings

import androidx.lifecycle.viewModelScope
import com.daviddelgado.agenda.common.mvi.MviViewModel
import com.daviddelgado.agenda.domain.usecase.DeleteAccountUseCase
import com.daviddelgado.agenda.domain.usecase.LogoutUseCase
import com.daviddelgado.agenda.domain.usecase.ObserveCurrentUserUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SettingsViewModel(
    observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {
    init {
        observeCurrentUserUseCase()
            .onEach { user -> setState { copy(user = user) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.Logout -> logout()
            SettingsIntent.RequestDeleteAccount -> setState { copy(isDeleteAccountPending = true) }
            SettingsIntent.CancelDeleteAccount -> setState { copy(isDeleteAccountPending = false) }
            SettingsIntent.ConfirmDeleteAccount -> deleteAccount()
        }
    }

    private fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            sendEffect(SettingsEffect.NavigateToLogin)
        }
    }

    private fun deleteAccount() {
        viewModelScope.launch {
            setState { copy(isLoading = true, isDeleteAccountPending = false) }
            deleteAccountUseCase()
                .onSuccess {
                    setState { copy(isLoading = false) }
                    sendEffect(SettingsEffect.NavigateToLogin)
                }
                .onFailure { error ->
                    val message = error.message ?: "No se pudo borrar la cuenta"
                    setState { copy(isLoading = false, errorMessage = message) }
                    sendEffect(SettingsEffect.ShowError(message))
                }
        }
    }
}
