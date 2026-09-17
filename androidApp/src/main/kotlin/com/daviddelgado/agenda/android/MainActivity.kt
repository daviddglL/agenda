package com.daviddelgado.agenda.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.daviddelgado.agenda.shared.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val requestNotificationPermission =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

            App(onHomeShown = { requestNotificationPermissionIfNeeded(requestNotificationPermission) })
        }
    }

    /**
     * Sin esto el permiso "dangerous" `POST_NOTIFICATIONS` (API 33+) se queda denegado para
     * siempre salvo que el usuario lo conceda a mano desde Ajustes: los recordatorios push
     * nunca llegarian a verse (ver ESTADO_PROYECTO.md, seccion 10). Se pide una vez al entrar
     * a Home; si el sistema ya lo concedio o denego antes, `launch` no vuelve a mostrar dialogo.
     */
    private fun requestNotificationPermissionIfNeeded(launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val yaConcedido =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!yaConcedido) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
