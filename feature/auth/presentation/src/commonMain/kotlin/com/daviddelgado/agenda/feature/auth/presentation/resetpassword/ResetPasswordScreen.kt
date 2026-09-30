package com.daviddelgado.agenda.feature.auth.presentation.resetpassword

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
import com.daviddelgado.agenda.core.designsystem.component.AgendaPrimaryButton
import com.daviddelgado.agenda.core.designsystem.component.AgendaTextField
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * @param email llega desde [ForgotPasswordScreen] (ver `App.kt`); se fija una sola vez al
 * entrar mediante [ResetPasswordIntent.EmailProvided] (mismo patron que `TasksScreen`'s
 * `initialDate`, ver `shared/.../HomeNavigator.kt`).
 */
@Composable
fun ResetPasswordScreen(
    email: String,
    onPasswordReset: () -> Unit,
    viewModel: ResetPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(email) {
        viewModel.onIntent(ResetPasswordIntent.EmailProvided(email))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                ResetPasswordEffect.PasswordReset -> onPasswordReset()
                is ResetPasswordEffect.ShowError -> Unit
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Restablecer contrasena", style = MaterialTheme.typography.headlineMedium)
            Text(text = "Hemos mandado un codigo a $email", style = MaterialTheme.typography.bodyMedium)

            AgendaTextField(state.code, { viewModel.onIntent(ResetPasswordIntent.CodeChanged(it)) }, "Codigo")
            AgendaTextField(
                state.newPassword,
                { viewModel.onIntent(ResetPasswordIntent.NewPasswordChanged(it)) },
                "Contrasena nueva",
                isPassword = true,
            )
            AgendaTextField(
                state.confirmPassword,
                { viewModel.onIntent(ResetPasswordIntent.ConfirmPasswordChanged(it)) },
                "Confirmar contrasena",
                isPassword = true,
            )

            state.errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                AgendaPrimaryButton(
                    text = "Cambiar contrasena",
                    onClick = { viewModel.onIntent(ResetPasswordIntent.Submit) },
                )
            }
        }
    }
}
