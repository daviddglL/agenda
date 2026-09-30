package com.daviddelgado.agenda.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daviddelgado.agenda.core.domain.logger.AgendaLogger
import com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterFcmTokenUseCase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private const val REMINDER_CHANNEL_ID = "agenda_reminders"
private const val REMINDER_NOTIFICATION_ID = 1001
private const val LOG_TAG = "AgendaFirebaseMessagingService"

/**
 * Recibe los recordatorios push que manda el servidor (ver ReminderJob en :server). Cada
 * mensaje trae ya el titulo/cuerpo listos (notificacion "display", no data-only), asi que
 * basta con mostrarla; el registro del token nuevo se hace en [onNewToken].
 *
 * `ioDispatcher` es un parametro con valor por defecto (no una inyeccion por Koin) porque
 * Android instancia este servicio por reflexion desde el manifest usando el constructor sin
 * argumentos: el valor por defecto mantiene esa instanciacion intacta y a la vez permite un
 * dispatcher de test en un test unitario futuro (regla detekt InjectDispatcher).
 */
class AgendaFirebaseMessagingService(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FirebaseMessagingService() {
    private val registerFcmToken: RegisterFcmTokenUseCase by inject()
    private val scope = CoroutineScope(ioDispatcher)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            registerFcmToken(token)
                .onFailure { AgendaLogger.w(LOG_TAG, "No se pudo registrar el nuevo token FCM", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: return
        val body = message.notification?.body.orEmpty()
        ensureChannel()
        val notification =
            NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setAutoCancel(true)
                .build()
        // POST_NOTIFICATIONS es un permiso "dangerous" desde API 33: aunque este declarado en
        // el manifest, el usuario puede denegarlo en tiempo de ejecucion, y notify() lanza
        // SecurityException si no esta concedido. El check inline (en vez de en una funcion
        // aparte) es a proposito: el analisis de flujo de datos de Android Lint solo reconoce
        // checkSelfPermission() dentro del mismo metodo que la llamada que protege.
        val canPostNotifications =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (canPostNotifications) {
            NotificationManagerCompat.from(this).notify(REMINDER_NOTIFICATION_ID, notification)
        } else {
            AgendaLogger.w(LOG_TAG, "Recordatorio recibido pero sin permiso POST_NOTIFICATIONS: no se muestra")
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL_ID, "Recordatorios de tareas", NotificationManager.IMPORTANCE_HIGH),
        )
    }
}
