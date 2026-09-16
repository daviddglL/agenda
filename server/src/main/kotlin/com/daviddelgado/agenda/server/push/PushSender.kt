package com.daviddelgado.agenda.server.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileInputStream

fun interface PushSender {
    /** @return true si Firebase acepto el envio. */
    fun send(
        token: String,
        title: String,
        body: String,
    ): Boolean
}

/**
 * Envio real via Firebase Cloud Messaging. Necesita un proyecto Firebase real y su fichero de
 * credenciales de cuenta de servicio (Firebase Console > Configuracion del proyecto > Cuentas
 * de servicio > Generar nueva clave privada), cuya ruta se pasa en la variable de entorno
 * AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON. Sin ella, [providePushSender] usa [NoOpPushSender]:
 * la app entera sigue funcionando, solo que sin recordatorios push de verdad, igual que
 * `ProductionConfig` con los pines de certificado.
 */
class FirebasePushSender(serviceAccountJsonPath: String) : PushSender {
    private val messaging: FirebaseMessaging

    init {
        val credentials = FileInputStream(serviceAccountJsonPath).use { GoogleCredentials.fromStream(it) }
        val app =
            FirebaseApp.getApps().firstOrNull()
                ?: FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build())
        messaging = FirebaseMessaging.getInstance(app)
    }

    override fun send(
        token: String,
        title: String,
        body: String,
    ): Boolean =
        runCatching {
            messaging.send(
                Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .build(),
            )
        }.onFailure { logger.error("Fallo enviando push a $token", it) }.isSuccess

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(FirebasePushSender::class.java)
    }
}

object NoOpPushSender : PushSender {
    private val logger: Logger = LoggerFactory.getLogger(NoOpPushSender::class.java)
    private var avisado = false

    override fun send(
        token: String,
        title: String,
        body: String,
    ): Boolean {
        if (!avisado) {
            logger.warn(
                "AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON no configurado: los recordatorios push " +
                    "estan deshabilitados (ver FirebasePushSender).",
            )
            avisado = true
        }
        return false
    }
}

private val providePushSenderLogger: Logger = LoggerFactory.getLogger("providePushSender")

/** @param serviceAccountJsonPath normalmente `System.getenv("AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON")`. */
fun providePushSender(serviceAccountJsonPath: String?): PushSender =
    serviceAccountJsonPath
        ?.let { path ->
            runCatching { FirebasePushSender(path) }
                .onFailure { error ->
                    providePushSenderLogger.error(
                        "No se pudo inicializar FirebasePushSender con $path; se usara NoOpPushSender",
                        error,
                    )
                }.getOrNull()
        }
        ?: NoOpPushSender
