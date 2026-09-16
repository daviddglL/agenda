package com.daviddelgado.agenda.server.security

import com.auth0.jwt.exceptions.JWTVerificationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JwtConfigTest {
    private val jwtConfig = JwtConfig(secret = "secreto-de-test-muy-largo")

    @Test
    fun elAccessTokenLlevaElUsuarioYPasaElVerificador() {
        val token = jwtConfig.generateAccessToken("usuario-1", tokenVersion = 0)

        assertEquals("usuario-1", jwtConfig.verifier.verify(token).subject)
    }

    @Test
    fun elRefreshTokenSeValidaYDevuelveElUsuario() {
        val token = jwtConfig.generateRefreshToken("usuario-1", tokenVersion = 0)

        assertEquals("usuario-1", jwtConfig.verifyRefreshToken(token)?.userId)
    }

    @Test
    fun unRefreshTokenNoSirveComoAccessToken() {
        val refresh = jwtConfig.generateRefreshToken("usuario-1", tokenVersion = 0)

        // El claim "type" distingue los dos tipos: el verificador de acceso lo rechaza.
        assertFailsWith<JWTVerificationException> { jwtConfig.verifier.verify(refresh) }
    }

    @Test
    fun unAccessTokenNoSirveComoRefreshToken() {
        val access = jwtConfig.generateAccessToken("usuario-1", tokenVersion = 0)

        assertNull(jwtConfig.verifyRefreshToken(access))
    }

    @Test
    fun unTokenFirmadoConOtroSecretoNoSeAcepta() {
        val ajeno = JwtConfig(secret = "otro-secreto-distinto").generateRefreshToken("usuario-1", tokenVersion = 0)

        assertNull(jwtConfig.verifyRefreshToken(ajeno))
        assertFailsWith<JWTVerificationException> { jwtConfig.verifier.verify(ajeno) }
    }
}
