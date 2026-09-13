package com.daviddelgado.agenda.server.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Firma HS256. `secret` se lee de la variable de entorno AGENDA_JWT_SECRET en produccion
 * (ver [com.daviddelgado.agenda.server.Application]); el valor por defecto es solo para
 * desarrollo local.
 */
class JwtConfig(
    private val secret: String,
    val issuer: String = "agenda-server",
    val audience: String = "agenda-app",
) {
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: com.auth0.jwt.JWTVerifier =
        JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("type", "access")
            .build()

    fun generateAccessToken(userId: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("type", "access")
            .withSubject(userId)
            .withExpiresAt(Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)))
            .sign(algorithm)

    fun generateRefreshToken(userId: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("type", "refresh")
            .withSubject(userId)
            .withExpiresAt(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)))
            .sign(algorithm)

    /** Valida un refresh token y devuelve el userId si es valido, o null si no lo es. */
    fun verifyRefreshToken(token: String): String? =
        runCatching {
            val decoded =
                JWT.require(algorithm)
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .withClaim("type", "refresh")
                    .build()
                    .verify(token)
            decoded.subject
        }.getOrNull()
}
