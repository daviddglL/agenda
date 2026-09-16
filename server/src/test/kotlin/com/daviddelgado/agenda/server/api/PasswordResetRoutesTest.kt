package com.daviddelgado.agenda.server.api

import com.daviddelgado.agenda.server.dto.ForgotPasswordRequest
import com.daviddelgado.agenda.server.dto.LoginRequest
import com.daviddelgado.agenda.server.dto.RefreshRequest
import com.daviddelgado.agenda.server.dto.ResetPasswordRequest
import com.daviddelgado.agenda.server.email.EmailSender
import com.daviddelgado.agenda.server.repository.PasswordResetRepository
import com.daviddelgado.agenda.server.security.sha256Hex
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val ESPERA_ENVIO_CORREO_SEGUNDOS = 2L

/**
 * `handleForgotPassword` (AuthRoutes.kt) lanza el envio real en segundo plano
 * (`GlobalScope.launch(Dispatchers.IO)`) para que la respuesta 204 no tarde mas cuando el
 * email si existe que cuando no (cerrar ese canal lateral de temporizacion es justo el
 * motivo del fondo asincrono). Por eso `send` aqui no es sincrono con la respuesta HTTP:
 * este sender usa un `CountDownLatch` para que el test pueda esperar de forma determinista a
 * que la corutina de fondo haya terminado antes de leer `lastBody`, en vez de asumir que ya
 * ha corrido (seria una carrera) o meter un `Thread.sleep` a ciegas.
 */
private class CapturingEmailSender : EmailSender {
    var lastBody: String? = null
    private val enviado = CountDownLatch(1)

    override fun send(
        to: String,
        subject: String,
        body: String,
    ): Boolean {
        lastBody = body
        enviado.countDown()
        return true
    }

    fun esperarEnvio() {
        check(enviado.await(ESPERA_ENVIO_CORREO_SEGUNDOS, TimeUnit.SECONDS)) {
            "El correo no se mando en el tiempo de espera"
        }
    }
}

private fun CapturingEmailSender.codigoEnviado(): String {
    esperarEnvio()
    val cuerpo = assertNotNull(lastBody, "No se envio ningun correo")
    return Regex("[0-9]{6}").find(cuerpo)!!.value
}

class PasswordResetRoutesTest {
    @Test
    fun pedirCodigoParaEmailExistenteResponde204YMandaUnCorreo() =
        withApi { client ->
            client.registrarUsuario(email = "pide-codigo@test.com")
            val sender = CapturingEmailSender()

            val response =
                client.post("/auth/forgot-password") {
                    contentType(ContentType.Application.Json)
                    setBody(ForgotPasswordRequest("pide-codigo@test.com"))
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
        }

    @Test
    fun pedirCodigoParaEmailInexistenteTambienResponde204() =
        withApi { client ->
            val response =
                client.post("/auth/forgot-password") {
                    contentType(ContentType.Application.Json)
                    setBody(ForgotPasswordRequest("no-existe@test.com"))
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
        }

    @Test
    fun resetearConCodigoCorrectoPermiteLoguearseConLaContrasenaNueva() {
        val sender = CapturingEmailSender()
        withApi(emailSender = sender) { client ->
            client.registrarUsuario(email = "reset-ok@test.com", password = "vieja123")
            client.post("/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest("reset-ok@test.com"))
            }
            val codigo = sender.codigoEnviado()

            val resetResponse =
                client.post("/auth/reset-password") {
                    contentType(ContentType.Application.Json)
                    setBody(ResetPasswordRequest("reset-ok@test.com", codigo, "nueva123"))
                }
            assertEquals(HttpStatusCode.NoContent, resetResponse.status)

            val loginConVieja =
                client.post("/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest("reset-ok@test.com", "vieja123"))
                }
            assertEquals(HttpStatusCode.Unauthorized, loginConVieja.status)

            val loginConNueva =
                client.post("/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest("reset-ok@test.com", "nueva123"))
                }
            assertEquals(HttpStatusCode.OK, loginConNueva.status)
        }
    }

    @Test
    fun resetearConCodigoIncorrectoResponde400YNoCambiaLaContrasena() =
        withApi { client ->
            client.registrarUsuario(email = "codigo-malo@test.com", password = "vieja123")
            client.post("/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest("codigo-malo@test.com"))
            }

            val response =
                client.post("/auth/reset-password") {
                    contentType(ContentType.Application.Json)
                    setBody(ResetPasswordRequest("codigo-malo@test.com", "000000", "nueva123"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun trasCincoIntentosFallidosElCodigoDejaDeAceptarseAunqueSeaElCorrecto() {
        val sender = CapturingEmailSender()
        withApi(emailSender = sender) { client ->
            client.registrarUsuario(email = "agotado@test.com")
            client.post("/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest("agotado@test.com"))
            }
            val codigoCorrecto = sender.codigoEnviado()

            repeat(5) {
                client.post("/auth/reset-password") {
                    contentType(ContentType.Application.Json)
                    setBody(ResetPasswordRequest("agotado@test.com", "000000", "nueva123"))
                }
            }

            val response =
                client.post("/auth/reset-password") {
                    contentType(ContentType.Application.Json)
                    setBody(ResetPasswordRequest("agotado@test.com", codigoCorrecto, "nueva123"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun resetearInvalidaElRefreshTokenAnterior() {
        val sender = CapturingEmailSender()
        withApi(emailSender = sender) { client ->
            val session = client.registrarUsuario(email = "invalida-refresh@test.com")
            client.post("/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest("invalida-refresh@test.com"))
            }
            val codigo = sender.codigoEnviado()

            client.post("/auth/reset-password") {
                contentType(ContentType.Application.Json)
                setBody(ResetPasswordRequest("invalida-refresh@test.com", codigo, "nueva123"))
            }

            val refreshResponse =
                client.post("/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(session.refreshToken))
                }

            assertEquals(HttpStatusCode.Unauthorized, refreshResponse.status)
        }
    }

    @Test
    fun pedirCodigoDosVecesInvalidaElPrimerCodigo() {
        val sender = CapturingEmailSender()
        withApi(emailSender = sender) { client ->
            client.registrarUsuario(email = "dos-codigos@test.com")

            client.post("/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest("dos-codigos@test.com"))
            }
            val primerCodigo = sender.codigoEnviado()

            client.post("/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest("dos-codigos@test.com"))
            }

            val conPrimero =
                client.post("/auth/reset-password") {
                    contentType(ContentType.Application.Json)
                    setBody(ResetPasswordRequest("dos-codigos@test.com", primerCodigo, "nueva123"))
                }
            assertEquals(HttpStatusCode.BadRequest, conPrimero.status)
        }
    }

    @Test
    fun elCodigoCaducadoNoSirve() =
        withApi { client ->
            val session = client.registrarUsuario(email = "caducado@test.com")
            val codigo = "123456"
            PasswordResetRepository().createOrReplace(
                userId = session.userId,
                codeHash = sha256Hex(codigo),
                expiresAt = Instant.now().minusSeconds(1),
            )

            val response =
                client.post("/auth/reset-password") {
                    contentType(ContentType.Application.Json)
                    setBody(ResetPasswordRequest("caducado@test.com", codigo, "nueva123"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun unaContrasenaNuevaDeMenosDeSeisCaracteresSeRechaza() {
        val sender = CapturingEmailSender()
        withApi(emailSender = sender) { client ->
            client.registrarUsuario(email = "corta@test.com")
            client.post("/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest("corta@test.com"))
            }
            val codigo = sender.codigoEnviado()

            val response =
                client.post("/auth/reset-password") {
                    contentType(ContentType.Application.Json)
                    setBody(ResetPasswordRequest("corta@test.com", codigo, "1234"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }
}
