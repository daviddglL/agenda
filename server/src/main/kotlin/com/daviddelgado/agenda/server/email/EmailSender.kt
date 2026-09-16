package com.daviddelgado.agenda.server.email

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Properties

fun interface EmailSender {
    /** @return true si el envio se acepto (no garantiza entrega). */
    fun send(
        to: String,
        subject: String,
        body: String,
    ): Boolean
}

/**
 * Envio real por SMTP (Jakarta Mail / Angus Mail). Necesita un servidor SMTP real (Gmail,
 * un proveedor transaccional con SMTP, etc.); las credenciales se leen de las variables de
 * entorno AGENDA_SMTP_HOST/PORT/USERNAME/PASSWORD/FROM en [provideEmailSender]. Sin ellas,
 * se usa [NoOpEmailSender]: la app entera sigue funcionando, solo que sin correos reales,
 * igual que `ProductionConfig` con los pines de certificado y `PushSender` con Firebase.
 *
 * A diferencia de `FirebasePushSender` (que lee un fichero de credenciales en el
 * constructor y puede fallar ahi mismo), construir la sesion SMTP nunca toca la red: el
 * fallo real, si las credenciales son invalidas o el host no responde, ocurre dentro de
 * [send] al intentar conectar - por eso aqui no hace falta un `runCatching` en la
 * construccion, solo en el envio. Los timeouts explicitos evitan que una peticion HTTP se
 * quede colgada esperando a un servidor SMTP que no responde.
 */
class SmtpEmailSender(
    host: String,
    port: Int,
    private val username: String,
    private val password: String,
    private val from: String,
) : EmailSender {
    private val session: Session =
        Session.getInstance(
            Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port.toString())
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.connectiontimeout", "5000")
                put("mail.smtp.timeout", "5000")
                put("mail.smtp.writetimeout", "5000")
            },
            object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
            },
        )

    override fun send(
        to: String,
        subject: String,
        body: String,
    ): Boolean =
        runCatching {
            val message =
                MimeMessage(session).apply {
                    setFrom(InternetAddress(this@SmtpEmailSender.from))
                    setRecipients(Message.RecipientType.TO, to)
                    setSubject(subject)
                    setText(body)
                }
            Transport.send(message)
        }.onFailure { logger.error("Fallo enviando email a $to", it) }.isSuccess

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(SmtpEmailSender::class.java)
    }
}

/**
 * Sin AGENDA_SMTP_HOST configurado, cae aqui: NO manda el correo, pero deja el cuerpo
 * completo (incluido el codigo de recuperacion) visible en el log del servidor (WARN) para
 * poder probar el flujo completo en desarrollo sin credenciales SMTP reales - a diferencia
 * del push, donde "no llega nada" es aceptable, aqui el flujo entero seria imposible de
 * probar de extremo a extremo sin este escape valvula.
 */
object NoOpEmailSender : EmailSender {
    private val logger: Logger = LoggerFactory.getLogger(NoOpEmailSender::class.java)
    private var avisado = false

    override fun send(
        to: String,
        subject: String,
        body: String,
    ): Boolean {
        if (!avisado) {
            logger.warn("AGENDA_SMTP_HOST no configurado: los correos estan deshabilitados (ver SmtpEmailSender).")
            avisado = true
        }
        logger.warn("Correo (no enviado, solo log) para $to:\n$subject\n\n$body")
        return false
    }
}

/**
 * @param host normalmente `System.getenv("AGENDA_SMTP_HOST")`, y el resto de parametros sus
 * variables AGENDA_SMTP_* equivalentes (ver [SmtpEmailSender]).
 */
fun provideEmailSender(
    host: String?,
    port: Int?,
    username: String?,
    password: String?,
    from: String?,
): EmailSender =
    if (host != null && port != null && username != null) {
        provideSmtpEmailSenderOrNoOp(host, port, username, password, from)
    } else {
        NoOpEmailSender
    }

/**
 * Separada de [provideEmailSender] solo para que ningun `if` tenga mas de 3 condiciones
 * encadenadas con `&&` (regla detekt `ComplexCondition`) sin volver a caer en `!!` sobre
 * variables nullable (regla detekt `UnsafeCallOnNullableType`, ver historial de esta funcion):
 * cada `if` deja que el compilador haga smart-cast de verdad de sus propias variables.
 */
private fun provideSmtpEmailSenderOrNoOp(
    host: String,
    port: Int,
    username: String,
    password: String?,
    from: String?,
): EmailSender =
    if (password != null && from != null) {
        SmtpEmailSender(host, port, username, password, from)
    } else {
        NoOpEmailSender
    }
