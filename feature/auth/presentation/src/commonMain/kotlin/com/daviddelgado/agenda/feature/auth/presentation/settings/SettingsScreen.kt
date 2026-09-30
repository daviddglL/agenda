package com.daviddelgado.agenda.feature.auth.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daviddelgado.agenda.designsystem.component.AgendaPrimaryButton
import org.koin.compose.viewmodel.koinViewModel

/**
 * Ajustes de la cuenta: quien ha iniciado sesion, cerrar sesion y borrar la cuenta (que
 * borra tambien todas las tareas, en cascada, ver
 * [com.daviddelgado.agenda.feature.auth.domain.usecase.DeleteAccountUseCase]).
 */
@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SettingsEffect.NavigateToLogin -> onLoggedOut()
                is SettingsEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(text = "Ajustes", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))

            state.user?.let { user ->
                Text(text = user.name, style = MaterialTheme.typography.titleMedium)
                Text(text = user.email, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(32.dp))

            AgendaPrimaryButton(text = "Cerrar sesion", onClick = { viewModel.onIntent(SettingsIntent.Logout) })

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = { viewModel.onIntent(SettingsIntent.RequestDeleteAccount) }) {
                Text("Borrar mi cuenta", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (state.isDeleteAccountPending) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(SettingsIntent.CancelDeleteAccount) },
            title = { Text("Borrar cuenta") },
            text = {
                Text(
                    "Se borrara tu cuenta y todas tus tareas de forma permanente. " +
                        "Esta accion no se puede deshacer. Seguro que quieres continuar?",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(SettingsIntent.ConfirmDeleteAccount) }) {
                    Text("Borrar cuenta", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(SettingsIntent.CancelDeleteAccount) }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
