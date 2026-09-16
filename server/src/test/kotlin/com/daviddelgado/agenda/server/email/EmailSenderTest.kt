package com.daviddelgado.agenda.server.email

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class EmailSenderTest {
    @Test
    fun sinHostConfiguradoUsaElEnvioSinEfecto() {
        assertSame(NoOpEmailSender, provideEmailSender(null, null, null, null, null))
    }

    @Test
    fun conTodosLosDatosConfiguradosUsaSmtpReal() {
        val sender = provideEmailSender("smtp.test.com", 587, "user", "pass", "agenda@test.com")
        assertIs<SmtpEmailSender>(sender)
    }

    @Test
    fun siFaltaAlgunDatoUsaElEnvioSinEfecto() {
        val sender = provideEmailSender("smtp.test.com", 587, "user", password = null, from = "agenda@test.com")
        assertSame(NoOpEmailSender, sender)
    }

    @Test
    fun elEnvioSinEfectoSiempreDevuelveFalse() {
        assertFalse(NoOpEmailSender.send(to = "a@test.com", subject = "s", body = "b"))
    }

    @Test
    fun unSmtpConHostInalcanzableNoLanzaAlEnviarYDevuelveFalse() {
        val sender = SmtpEmailSender("smtp.host-que-no-existe.invalid", 587, "user", "pass", "agenda@test.com")

        assertFalse(sender.send(to = "a@test.com", subject = "s", body = "b"))
    }
}
