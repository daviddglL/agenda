package com.daviddelgado.agenda.server.security

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientAddressResolverTest {
    @Test
    fun sinProxiesDeConfianzaSeUsaSiempreLaConexionDirecta() {
        val direccion =
            resolveClientAddress(
                directRemoteHost = "203.0.113.9",
                forwardedForHeader = "1.2.3.4",
                trustedProxies = emptySet(),
            )

        assertEquals("203.0.113.9", direccion)
    }

    @Test
    fun siLaConexionDirectaNoEsUnProxyDeConfianzaSeIgnoraLaCabecera() {
        // Un cliente cualquiera podria mandar el header el mismo para intentar suplantar la
        // IP de otro y esquivar su propio limite: solo se lee si viene de verdad de un proxy
        // de confianza (nunca de un origen desconocido).
        val direccion =
            resolveClientAddress(
                directRemoteHost = "1.2.3.4",
                forwardedForHeader = "9.9.9.9",
                trustedProxies = setOf("10.0.0.1"),
            )

        assertEquals("1.2.3.4", direccion)
    }

    @Test
    fun siLaConexionDirectaEsUnProxyDeConfianzaSeUsaElClienteDeLaCabecera() {
        val direccion =
            resolveClientAddress(
                directRemoteHost = "10.0.0.1",
                forwardedForHeader = "1.2.3.4",
                trustedProxies = setOf("10.0.0.1"),
            )

        assertEquals("1.2.3.4", direccion)
    }

    @Test
    fun conVariosSaltosEnLaCabeceraSeUsaElPrimeroSinContarElProxy() {
        val direccion =
            resolveClientAddress(
                directRemoteHost = "10.0.0.1",
                forwardedForHeader = "1.2.3.4, 10.0.0.1",
                trustedProxies = setOf("10.0.0.1"),
            )

        assertEquals("1.2.3.4", direccion)
    }

    @Test
    fun proxyDeConfianzaSinCabeceraCaeALaConexionDirecta() {
        val direccion =
            resolveClientAddress(
                directRemoteHost = "10.0.0.1",
                forwardedForHeader = null,
                trustedProxies = setOf("10.0.0.1"),
            )

        assertEquals("10.0.0.1", direccion)
    }

    @Test
    fun proxyDeConfianzaConCabeceraEnBlancoCaeALaConexionDirecta() {
        val direccion =
            resolveClientAddress(
                directRemoteHost = "10.0.0.1",
                forwardedForHeader = "   ",
                trustedProxies = setOf("10.0.0.1"),
            )

        assertEquals("10.0.0.1", direccion)
    }
}
