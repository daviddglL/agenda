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
import com.daviddelgado.agenda.feature.auth.presentation.forgotpassword.ForgotPasswordScreen
import com.daviddelgado.agenda.feature.auth.presentation.login.LoginScreen
import com.daviddelgado.agenda.feature.auth.presentation.register.RegisterScreen
import com.daviddelgado.agenda.feature.auth.presentation.resetpassword.ResetPasswordScreen
import com.daviddelgado.agenda.feature.auth.presentation.settings.SettingsScreen
import com.daviddelgado.agenda.feature.streaks.presentation.streaks.StreaksScreen
import com.daviddelgado.agenda.feature.tasks.presentation.calendar.CalendarScreen
import com.daviddelgado.agenda.feature.tasks.presentation.tasks.TasksScreen
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private sealed interface AppScreen {
    data object Splash : AppScreen

    data class Login(val justReset: Boolean = false) : AppScreen

    data object Register : AppScreen

    data object ForgotPassword : AppScreen

    data class ResetPassword(val email: String) : AppScreen

    data object Home : AppScreen
}

/**
 * @param onHomeShown se llama una vez cada vez que se entra a Home (tras login, registro o
 * sesion recuperada en el splash). Cada plataforma decide que hacer con el aviso; hoy solo
 * Android lo usa para pedir el permiso `POST_NOTIFICATIONS` en tiempo de ejecucion (ver
 * `MainActivity`), necesario desde API 33 para que se vean los recordatorios push (seccion
 * 7quinquies/10 de ESTADO_PROYECTO.md). iOS no pasa nada y no ocurre nada.
 */
@Composable
fun App(onHomeShown: () -> Unit = {}) {
    AgendaTheme {
        var screen by remember { mutableStateOf<AppScreen>(AppScreen.Splash) }

        when (val current = screen) {
            AppScreen.Splash ->
                SplashScreen(
                    onFinished = { hasSession -> screen = if (hasSession) AppScreen.Home else AppScreen.Login() },
                )
            is AppScreen.Login ->
                LoginScreen(
                    onNavigateToHome = { screen = AppScreen.Home },
                    onNavigateToRegister = { screen = AppScreen.Register },
                    onNavigateToForgotPassword = { screen = AppScreen.ForgotPassword },
                    justReset = current.justReset,
                )
            AppScreen.Register -> RegisterScreen(onNavigateToHome = { screen = AppScreen.Home })
            AppScreen.ForgotPassword ->
                ForgotPasswordScreen(onCodeSent = { email -> screen = AppScreen.ResetPassword(email) })
            is AppScreen.ResetPassword ->
                ResetPasswordScreen(
                    email = current.email,
                    onPasswordReset = { screen = AppScreen.Login(justReset = true) },
                )
            AppScreen.Home ->
                HomeWithTabs(onLoggedOut = { screen = AppScreen.Login() }, onShown = onHomeShown)
        }
    }
}

private const val SPLASH_MILLIS = 1200L

/**
 * Arranque de la app: mientras se ve el splash se intenta recuperar la sesion guardada
 * (`GET /users/me` con el token cifrado del dispositivo). Si el token sigue siendo valido
 * se entra directo a Home (y de paso se (re)registra el token push del dispositivo, Task 10:
 * es el arranque mas comun, no solo el que sigue a un login); si no, al login.
 */
@Composable
private fun SplashScreen(onFinished: (hasSession: Boolean) -> Unit) {
    val splashSessionHandler = koinInject<SplashSessionHandler>()

    LaunchedEffect(Unit) {
        val user = splashSessionHandler.restoreSession()
        delay(SPLASH_MILLIS)
        onFinished(user != null)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Agenda", style = MaterialTheme.typography.headlineMedium)
    }
}

/**
 * @param onLoggedOut vuelve a la pantalla de login: se llama al cerrar sesion o borrar la cuenta.
 * @param onShown se llama una vez al entrar a Home (ver [App]).
 */
@Composable
private fun HomeWithTabs(
    onLoggedOut: () -> Unit,
    onShown: () -> Unit,
) {
    val navigator = remember { HomeNavigator() }
    val navState by navigator.state.collectAsState()
    LaunchedEffect(Unit) { onShown() }

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
