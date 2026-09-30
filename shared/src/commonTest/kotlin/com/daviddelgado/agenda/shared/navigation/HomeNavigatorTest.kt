package com.daviddelgado.agenda.shared.navigation

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeNavigatorTest {
    private val unDia = LocalDate(2026, 9, 20)

    @Test
    fun arrancaEnLaPestanaDeTareasSinFechaPendiente() {
        val navigator = HomeNavigator()

        assertEquals(HomeTab.TASKS, navigator.state.value.tab)
        assertNull(navigator.state.value.pendingTaskDate)
    }

    @Test
    fun selectTabCambiaLaPestanaActivaSinTocarLaFechaPendiente() {
        val navigator = HomeNavigator()

        navigator.selectTab(HomeTab.CALENDAR)

        assertEquals(HomeTab.CALENDAR, navigator.state.value.tab)
        assertNull(navigator.state.value.pendingTaskDate)
    }

    @Test
    fun abrirUnDiaDelCalendarioLlevaATareasConEsaFechaPendiente() {
        val navigator = HomeNavigator()
        navigator.selectTab(HomeTab.CALENDAR)

        navigator.openCalendarDay(unDia)

        assertEquals(HomeTab.TASKS, navigator.state.value.tab)
        assertEquals(unDia, navigator.state.value.pendingTaskDate)
    }

    @Test
    fun consumirLaFechaPendienteLaLimpiaSinCambiarDePestana() {
        val navigator = HomeNavigator()
        navigator.openCalendarDay(unDia)

        navigator.consumePendingTaskDate()

        assertEquals(HomeTab.TASKS, navigator.state.value.tab)
        assertNull(navigator.state.value.pendingTaskDate)
    }
}
