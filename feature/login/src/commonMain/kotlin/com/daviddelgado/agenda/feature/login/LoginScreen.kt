package com.daviddelgado.agenda.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daviddelgado.agenda.designsystem.component.AgendaPrimaryButton
import com.daviddelgado.agenda.designsystem.component.AgendaTextField
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                LoginEffect.NavigateToHome -> onNavigateToHome()
                LoginEffect.NavigateToRegister -> onNavigateToRegister()
                is LoginEffect.ShowError -> Unit
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Agenda", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))

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
                Text(text = it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                AgendaPrimaryButton(text = "Entrar", onClick = { viewModel.onIntent(LoginIntent.Submit) })
            }

            TextButton(onClick = { viewModel.onIntent(LoginIntent.NavigateToRegister) }) {
                Text("Crear una cuenta")
            }
        }
    }
}
