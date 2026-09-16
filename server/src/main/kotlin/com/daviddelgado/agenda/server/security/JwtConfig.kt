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

    /** `tokenVersion` viaja en el claim "tv"; ver [DecodedRefreshToken] y su uso al refrescar. */
    fun generateAccessToken(
        userId: String,
        tokenVersion: Int,
    ): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("type", "access")
            .withClaim("tv", tokenVersion)
            .withSubject(userId)
            .withExpiresAt(Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)))
            .sign(algorithm)

    fun generateRefreshToken(
        userId: String,
        tokenVersion: Int,
    ): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("type", "refresh")
            .withClaim("tv", tokenVersion)
            .withSubject(userId)
            .withExpiresAt(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)))
            .sign(algorithm)

    data class DecodedRefreshToken(val userId: String, val tokenVersion: Int)

    /** Valida un refresh token y devuelve su payload, o null si no es valido. */
    fun verifyRefreshToken(token: String): DecodedRefreshToken? =
        runCatching {
            val decoded =
                JWT.require(algorithm)
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .withClaim("type", "refresh")
                    .build()
                    .verify(token)
            DecodedRefreshToken(decoded.subject, decoded.getClaim("tv").asInt())
        }.getOrNull()
}
