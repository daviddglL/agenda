package com.daviddelgado.agenda.server.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {
    @Test
    fun laContrasenaCorrectaCoincideConSuHash() {
        val hash = PasswordHasher.hash("secreta123")

        assertTrue(PasswordHasher.matches("secreta123", hash))
    }

    @Test
    fun unaContrasenaDistintaNoCoincide() {
        val hash = PasswordHasher.hash("secreta123")

        assertFalse(PasswordHasher.matches("secreta124", hash))
    }

    @Test
    fun elHashNoEsLaContrasenaEnClaroYUsaSalAleatoria() {
        val primero = PasswordHasher.hash("secreta123")
        val segundo = PasswordHasher.hash("secreta123")

        assertNotEquals("secreta123", primero)
        // Dos hashes de la misma contrasena difieren porque bcrypt genera una sal nueva.
        assertNotEquals(primero, segundo)
        assertTrue(PasswordHasher.matches("secreta123", segundo))
    }
}
