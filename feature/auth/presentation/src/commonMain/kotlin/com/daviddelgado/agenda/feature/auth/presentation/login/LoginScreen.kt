package com.daviddelgado.agenda.feature.auth.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daviddelgado.agenda.core.designsystem.component.AgendaPrimaryButton
import com.daviddelgado.agenda.core.designsystem.component.AgendaTextField
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    justReset: Boolean = false,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var mostrarAvisoDeReset by remember { mutableStateOf(justReset) }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                LoginEffect.NavigateToHome -> onNavigateToHome()
                LoginEffect.NavigateToRegister -> onNavigateToRegister()
                LoginEffect.NavigateToForgotPassword -> onNavigateToForgotPassword()
                is LoginEffect.ShowError -> Unit
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Agenda", style = MaterialTheme.typography.headlineMedium)

            if (mostrarAvisoDeReset) {
                Text(text = "Contrasena actualizada, inicia sesion", color = MaterialTheme.colorScheme.primary)
            }

            AgendaTextField(
                value = state.email,
                onValueChange = { viewModel.onIntent(LoginIntent.EmailChanged(it)) },
                label = "Email",
            )
            AgendaTextField(
                value = state.password,
                onValueChange = { viewModel.onIntent(LoginIntent.PasswordChanged(it)) },
                label = "Contrasena",
                isPassword = true,
            )

            state.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                AgendaPrimaryButton(
                    text = "Entrar",
                    onClick = {
                        mostrarAvisoDeReset = false
                        viewModel.onIntent(LoginIntent.Submit)
                    },
                )
            }

            TextButton(onClick = { viewModel.onIntent(LoginIntent.NavigateToRegister) }) {
                Text("Crear una cuenta")
            }
            TextButton(onClick = { viewModel.onIntent(LoginIntent.NavigateToForgotPassword) }) {
                Text("Olvidaste tu contrasena?")
            }
        }
    }
}
