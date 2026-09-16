package com.daviddelgado.agenda.server.push

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame

class PushSenderTest {
    @Test
    fun sinVariableDeEntornoUsaElEnvioSinEfecto() {
        assertSame(NoOpPushSender, providePushSender(serviceAccountJsonPath = null))
    }

    @Test
    fun elEnvioSinEfectoSiempreDevuelveFalse() {
        assertFalse(NoOpPushSender.send(token = "cualquiera", title = "t", body = "b"))
    }

    @Test
    fun conFicheroDeCredencialesInexistenteCaeAEnvioSinEfectoSinLanzar() {
        // La ruta esta configurada pero el fichero no existe: debe registrarse el error real
        // (ver logger de providePushSender) y caer a NoOpPushSender sin propagar la excepcion.
        assertSame(NoOpPushSender, providePushSender(serviceAccountJsonPath = "/ruta/que/no/existe.json"))
    }
}
