package com.daviddelgado.agenda.core.presentation.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel base para el patron MVI compartido entre Android e iOS.
 * Estado centralizado e inmutable via [StateFlow], efectos de un solo uso via [SharedFlow].
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(initialState: S) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<E>()
    val effect: SharedFlow<E> = _effect

    val currentState: S
        get() = _state.value

    abstract fun onIntent(intent: I)

    protected fun setState(reducer: S.() -> S) {
        _state.value = currentState.reducer()
    }

    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effect.emit(effect) }
    }
}
