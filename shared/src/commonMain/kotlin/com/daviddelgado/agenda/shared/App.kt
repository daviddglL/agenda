package com.daviddelgado.agenda.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.daviddelgado.agenda.designsystem.theme.AgendaTheme
import com.daviddelgado.agenda.domain.usecase.RestoreSessionUseCase
import com.daviddelgado.agenda.feature.calendar.CalendarScreen
import com.daviddelgado.agenda.feature.login.LoginScreen
import com.daviddelgado.agenda.feature.register.RegisterScreen
import com.daviddelgado.agenda.feature.settings.SettingsScreen
import com.daviddelgado.agenda.feature.streaks.StreaksScreen
import com.daviddelgado.agenda.feature.tasks.TasksScreen
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private sealed interface AppScreen {
    data object Splash : AppScreen

    data object Login : AppScreen

    data object Register : AppScreen

    data object Home : AppScreen
}

@Composable
fun App() {
    AgendaTheme {
        var screen by remember { mutableStateOf<AppScreen>(AppScreen.Splash) }

        when (screen) {
            AppScreen.Splash ->
                SplashScreen(
                    onFinished = { hasSession -> screen = if (hasSession) AppScreen.Home else AppScreen.Login },
                )
            AppScreen.Login ->
                LoginScreen(
                    onNavigateToHome = { screen = AppScreen.Home },
                    onNavigateToRegister = { screen = AppScreen.Register },
                )
            AppScreen.Register -> RegisterScreen(onNavigateToHome = { screen = AppScreen.Home })
            AppScreen.Home -> HomeWithTabs(onLoggedOut = { screen = AppScreen.Login })
        }
    }
}

private const val SPLASH_MILLIS = 1200L

/**
 * Arranque de la app: mientras se ve el splash se intenta recuperar la sesion guardada
 * (`GET /users/me` con el token cifrado del dispositivo). Si el token sigue siendo valido
 * se entra directo a Home; si no, al login.
 */
@Composable
private fun SplashScreen(onFinished: (hasSession: Boolean) -> Unit) {
    val restoreSession = koinInject<RestoreSessionUseCase>()

    LaunchedEffect(Unit) {
        val user = restoreSession()
        delay(SPLASH_MILLIS)
        onFinished(user != null)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Agenda", style = MaterialTheme.typography.headlineMedium)
    }
}

/** @param onLoggedOut vuelve a la pantalla de login: se llama al cerrar sesion o borrar la cuenta. */
@Composable
private fun HomeWithTabs(onLoggedOut: () -> Unit) {
    val navigator = remember { HomeNavigator() }
    val navState by navigator.state.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = navState.tab == HomeTab.TASKS,
                    onClick = { navigator.selectTab(HomeTab.TASKS) },
                    icon = { Icon(Icons.Filled.CheckCircle, contentDescription = "Tareas") },
                    label = { Text("Tareas") },
                )
                NavigationBarItem(
                    selected = navState.tab == HomeTab.CALENDAR,
                    onClick = { navigator.selectTab(HomeTab.CALENDAR) },
                    icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "Calendario") },
                    label = { Text("Calendario") },
                )
                NavigationBarItem(
                    selected = navState.tab == HomeTab.STREAKS,
                    onClick = { navigator.selectTab(HomeTab.STREAKS) },
                    icon = { Icon(Icons.Filled.LocalFireDepartment, contentDescription = "Rachas") },
                    label = { Text("Rachas") },
                )
                NavigationBarItem(
                    selected = navState.tab == HomeTab.SETTINGS,
                    onClick = { navigator.selectTab(HomeTab.SETTINGS) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Ajustes") },
                    label = { Text("Ajustes") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (navState.tab) {
                HomeTab.TASKS ->
                    TasksScreen(
                        initialDate = navState.pendingTaskDate,
                        onDateConsumed = { navigator.consumePendingTaskDate() },
                    )
                HomeTab.CALENDAR -> CalendarScreen(onOpenDay = { navigator.openCalendarDay(it) })
                HomeTab.STREAKS -> StreaksScreen()
                HomeTab.SETTINGS -> SettingsScreen(onLoggedOut = onLoggedOut)
            }
        }
    }
}
