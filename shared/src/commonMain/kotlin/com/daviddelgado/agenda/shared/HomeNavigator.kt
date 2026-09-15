package com.daviddelgado.agenda.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

enum class HomeTab { TASKS, CALENDAR, STREAKS, SETTINGS }

data class HomeNavigationState(
    val tab: HomeTab = HomeTab.TASKS,
    val pendingTaskDate: LocalDate? = null,
)

/**
 * Decide que pestana de Home se ve y, si se llega desde el calendario, que dia debe
 * abrir la pantalla de tareas (punto 3 de "lo que NO esta hecho" en ESTADO_PROYECTO.md:
 * antes no habia atajo directo entre un dia del calendario y editar una tarea concreta).
 */
class HomeNavigator {
    private val _state = MutableStateFlow(HomeNavigationState())
    val state: StateFlow<HomeNavigationState> = _state.asStateFlow()

    fun selectTab(tab: HomeTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun openCalendarDay(date: LocalDate) {
        _state.update { it.copy(tab = HomeTab.TASKS, pendingTaskDate = date) }
    }

    fun consumePendingTaskDate() {
        _state.update { it.copy(pendingTaskDate = null) }
    }
}
