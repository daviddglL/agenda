package com.daviddelgado.agenda.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.daviddelgado.agenda.domain.usecase.RegisterFcmTokenUseCase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private const val REMINDER_CHANNEL_ID = "agenda_reminders"
private const val REMINDER_NOTIFICATION_ID = 1001

/**
 * Recibe los recordatorios push que manda el servidor (ver ReminderJob en :server). Cada
 * mensaje trae ya el titulo/cuerpo listos (notificacion "display", no data-only), asi que
 * basta con mostrarla; el registro del token nuevo se hace en [onNewToken].
 */
class AgendaFirebaseMessagingService : FirebaseMessagingService() {
    private val registerFcmToken: RegisterFcmTokenUseCase by inject()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch { registerFcmToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: return
        val body = message.notification?.body ?: ""
        ensureChannel()
        val notification =
            NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(this).notify(REMINDER_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL_ID, "Recordatorios de tareas", NotificationManager.IMPORTANCE_HIGH),
        )
    }
}
