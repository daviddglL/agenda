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
}
