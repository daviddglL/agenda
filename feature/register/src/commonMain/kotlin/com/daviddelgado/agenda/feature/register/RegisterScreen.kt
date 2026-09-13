package com.daviddelgado.agenda.feature.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
fun RegisterScreen(
    onNavigateToHome: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                RegisterEffect.NavigateToHome -> onNavigateToHome()
                is RegisterEffect.ShowError -> Unit
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Crear cuenta", style = MaterialTheme.typography.headlineMedium)

            AgendaTextField(state.name, { viewModel.onIntent(RegisterIntent.NameChanged(it)) }, "Nombre")
            AgendaTextField(state.email, { viewModel.onIntent(RegisterIntent.EmailChanged(it)) }, "Email")
            AgendaTextField(
                state.password,
                { viewModel.onIntent(RegisterIntent.PasswordChanged(it)) },
                "Contrasena",
                isPassword = true,
            )
            AgendaTextField(
                state.confirmPassword,
                { viewModel.onIntent(RegisterIntent.ConfirmPasswordChanged(it)) },
                "Confirmar contrasena",
                isPassword = true,
            )

            state.errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                AgendaPrimaryButton(text = "Registrarme", onClick = { viewModel.onIntent(RegisterIntent.Submit) })
            }
        }
    }
}
