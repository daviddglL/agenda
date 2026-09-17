package com.daviddelgado.agenda.server.security

/**
 * IP del cliente real para limitar peticiones por origen (`rateLimit("auth")` en
 * Application.kt), sin fiarse a ciegas de `X-Forwarded-For`: cualquiera puede mandar esa
 * cabecera para intentar suplantar la IP de otro y esquivar su propio limite, asi que solo se
 * lee cuando la conexion TCP llega de verdad de un proxy en [trustedProxies] (el proxy inverso
 * propio, nunca un origen desconocido). Sin proxies de confianza configurados (por defecto),
 * el comportamiento es identico al de antes de este fix: siempre [directRemoteHost].
 *
 * Con varios proxies en cadena, `X-Forwarded-For` es "cliente, proxy1, proxy2, ..."; se toma
 * el primer valor (el cliente original) porque solo se soporta un unico proxy de confianza
 * justo delante del servidor. Un proxy inverso mal configurado que anexe el header en vez de
 * sobrescribirlo dejaria colar un primer valor falso puesto por el propio cliente — hay que
 * configurar el proxy para que lo sobrescriba, nunca para que lo anexe.
 */
fun resolveClientAddress(
    directRemoteHost: String,
    forwardedForHeader: String?,
    trustedProxies: Set<String>,
): String {
    if (directRemoteHost !in trustedProxies) return directRemoteHost
    val originalClient = forwardedForHeader?.substringBefore(',')?.trim()
    return originalClient?.takeIf { it.isNotEmpty() } ?: directRemoteHost
}
