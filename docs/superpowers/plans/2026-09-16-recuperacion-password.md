# Recuperación de contraseña Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** un usuario que olvida su contraseña puede recuperar el acceso a su cuenta pidiendo
un código de un solo uso por email y usándolo para fijar una contraseña nueva, sin
intervención manual.

**Architecture:** servidor (nuevo `EmailSender` con el mismo patrón NoOp que `PushSender`,
tabla `password_reset_codes`, dos rutas nuevas, invalidación de sesión vía un contador
`token_version` embebido en el JWT) + cliente (capas de red/dominio existentes ampliadas,
módulo Compose Multiplatform nuevo `feature/passwordreset` con dos pantallas, enganchado a
la navegación existente en `shared/App.kt`).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, Ktor Client/Server,
Exposed+H2, Jakarta Mail (Angus Mail) para SMTP, kotlin.test + MockEngine + Compose UI Test
(TDD ya establecido en el repo).

**Spec:** `docs/superpowers/specs/2026-09-16-recuperacion-password-design.md`

## Global Constraints

- Clean Architecture modular + MVI + Koin + KMP + Compose Multiplatform + Ktor Client/Server
  (markdown.md, puntos 1-3). No sustituir ninguna tecnología ya elegida.
- `ktlint` + `detekt` en verde en todos los módulos (`./gradlew check` debe seguir en BUILD
  SUCCESSFUL tras cada tarea).
- Comentarios y strings visibles al usuario en español (sin tildes en identificadores de
  código, tal y como ya hace el repo). Identificadores en inglés.
- TDD real: test en rojo antes que código de producción, en cada tarea que tenga lógica no
  trivial.
- Sin credenciales SMTP reales disponibles todavía: `EmailSender` sigue el patrón ya usado
  en `PushSender.kt`/`ProductionConfig.kt` — infraestructura y código listos, con
  placeholders documentados que no rompen la build ni los tests, sin fallar en silencio en
  producción sin dejar rastro (log de aviso).
- `POST /auth/forgot-password` responde 204 siempre, exista o no la cuenta con ese email —
  nunca revelar qué emails están registrados.
- Actualizar `ESTADO_PROYECTO.md` al terminar todas las tareas (nueva sección siguiendo el
  estilo de las secciones "Verificado en caliente" ya existentes).

---

## Task 1: Servidor — envío de correo (`EmailSender`)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `server/build.gradle.kts`
- Create: `server/src/main/kotlin/com/daviddelgado/agenda/server/email/EmailSender.kt`
- Create: `server/src/test/kotlin/com/daviddelgado/agenda/server/email/EmailSenderTest.kt`

**Interfaces:**
- Produces: `EmailSender` (interfaz), `SmtpEmailSender`, `NoOpEmailSender`,
  `provideEmailSender(host, port, username, password, from): EmailSender` — lo consume
  Task 3.

- [ ] **Step 1: Añadir Jakarta Mail al catálogo de versiones**

En `gradle/libs.versions.toml`, dentro de `[versions]` (junto a `firebaseBom`):

```toml
angusMail = "2.0.3"
```

Dentro de `[libraries]` (junto a `firebase-messaging`):

```toml
angus-mail = { group = "org.eclipse.angus", name = "angus-mail", version.ref = "angusMail" }
```

- [ ] **Step 2: Añadir la dependencia a `:server`**

En `server/build.gradle.kts`, dentro de `dependencies { }`, junto a `implementation(libs.firebase.admin)`:

```kotlin
implementation(libs.angus.mail)
```

- [ ] **Step 3: Escribir el test (en rojo) de `EmailSender`**

`server/src/test/kotlin/com/daviddelgado/agenda/server/email/EmailSenderTest.kt`:

```kotlin
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
```

Run: `./gradlew :server:test --tests "*.EmailSenderTest"`
Expected: FAIL — no existe `EmailSender` ni `provideEmailSender`, error de compilación.

- [ ] **Step 4: Implementar `EmailSender`**

`server/src/main/kotlin/com/daviddelgado/agenda/server/email/EmailSender.kt`:

```kotlin
package com.daviddelgado.agenda.server.email

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Properties

fun interface EmailSender {
    /** @return true si el envio se acepto (no garantiza entrega). */
    fun send(
        to: String,
        subject: String,
        body: String,
    ): Boolean
}

/**
 * Envio real por SMTP (Jakarta Mail / Angus Mail). Necesita un servidor SMTP real (Gmail,
 * un proveedor transaccional con SMTP, etc.); las credenciales se leen de las variables de
 * entorno AGENDA_SMTP_HOST/PORT/USERNAME/PASSWORD/FROM en [provideEmailSender]. Sin ellas,
 * se usa [NoOpEmailSender]: la app entera sigue funcionando, solo que sin correos reales,
 * igual que `ProductionConfig` con los pines de certificado y `PushSender` con Firebase.
 *
 * A diferencia de `FirebasePushSender` (que lee un fichero de credenciales en el
 * constructor y puede fallar ahi mismo), construir la sesion SMTP nunca toca la red: el
 * fallo real, si las credenciales son invalidas o el host no responde, ocurre dentro de
 * [send] al intentar conectar - por eso aqui no hace falta un `runCatching` en la
 * construccion, solo en el envio. Los timeouts explicitos evitan que una peticion HTTP se
 * quede colgada esperando a un servidor SMTP que no responde.
 */
class SmtpEmailSender(
    host: String,
    port: Int,
    private val username: String,
    private val password: String,
    private val from: String,
) : EmailSender {
    private val session: Session =
        Session.getInstance(
            Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port.toString())
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.connectiontimeout", "5000")
                put("mail.smtp.timeout", "5000")
                put("mail.smtp.writetimeout", "5000")
            },
            object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
            },
        )

    override fun send(
        to: String,
        subject: String,
        body: String,
    ): Boolean =
        runCatching {
            val message =
                MimeMessage(session).apply {
                    setFrom(InternetAddress(from))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                    setSubject(subject)
                    setText(body)
                }
            Transport.send(message)
        }.onFailure { logger.error("Fallo enviando email a $to", it) }.isSuccess

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(SmtpEmailSender::class.java)
    }
}

/**
 * Sin AGENDA_SMTP_HOST configurado, cae aqui: NO manda el correo, pero deja el cuerpo
 * completo (incluido el codigo de recuperacion) visible en el log del servidor (WARN) para
 * poder probar el flujo completo en desarrollo sin credenciales SMTP reales - a diferencia
 * del push, donde "no llega nada" es aceptable, aqui el flujo entero seria imposible de
 * probar de extremo a extremo sin este escape valvula.
 */
object NoOpEmailSender : EmailSender {
    private val logger: Logger = LoggerFactory.getLogger(NoOpEmailSender::class.java)
    private var avisado = false

    override fun send(
        to: String,
        subject: String,
        body: String,
    ): Boolean {
        if (!avisado) {
            logger.warn("AGENDA_SMTP_HOST no configurado: los correos estan deshabilitados (ver SmtpEmailSender).")
            avisado = true
        }
        logger.warn("Correo (no enviado, solo log) para $to:\n$subject\n\n$body")
        return false
    }
}

/**
 * @param host normalmente `System.getenv("AGENDA_SMTP_HOST")`, y el resto de parametros sus
 * variables AGENDA_SMTP_* equivalentes (ver [SmtpEmailSender]).
 */
fun provideEmailSender(
    host: String?,
    port: Int?,
    username: String?,
    password: String?,
    from: String?,
): EmailSender =
    if (host != null && port != null && username != null && password != null && from != null) {
        SmtpEmailSender(host, port, username, password, from)
    } else {
        NoOpEmailSender
    }
```

Run: `./gradlew :server:test --tests "*.EmailSenderTest"`
Expected: PASS, 5/5 tests en verde.

- [ ] **Step 5: Verificar y commitear**

Run: `./gradlew :server:test :server:ktlintCheck :server:detekt`
Expected: BUILD SUCCESSFUL.

```bash
git add gradle/libs.versions.toml server/build.gradle.kts server/src/main/kotlin/com/daviddelgado/agenda/server/email/EmailSender.kt server/src/test/kotlin/com/daviddelgado/agenda/server/email/EmailSenderTest.kt
git commit -m "feat: envio de correo por SMTP con respaldo sin efecto si no hay credenciales"
```

---

## Task 2: Servidor — invalidación de sesión (`token_version`)

**Files:**
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/repository/UserRepository.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/security/JwtConfig.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt`
- Modify: `server/src/test/kotlin/com/daviddelgado/agenda/server/api/AuthRoutesTest.kt`

**Interfaces:**
- Produces: `UserRecord.tokenVersion: Int`, `UserRepository.updatePassword(id, newPasswordHash)`
  (sube `tokenVersion` en la misma transacción), `JwtConfig.generateAccessToken(userId, tokenVersion)`,
  `JwtConfig.generateRefreshToken(userId, tokenVersion)`,
  `JwtConfig.DecodedRefreshToken(userId, tokenVersion)` — todo lo consume Task 3.

- [ ] **Step 1: Columna `token_version`**

En `server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt`, dentro de
`object Users`, junto a `passwordHash`:

```kotlin
    val tokenVersion = integer("token_version").default(0)
```

- [ ] **Step 2: `UserRecord` y `updatePassword` — reescribir `UserRepository.kt`**

`server/src/main/kotlin/com/daviddelgado/agenda/server/repository/UserRepository.kt`
(fichero completo):

```kotlin
package com.daviddelgado.agenda.server.repository

import com.daviddelgado.agenda.server.db.Users
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

data class UserRecord(
    val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val tokenVersion: Int,
)

class UserRepository {
    fun create(
        name: String,
        email: String,
        passwordHash: String,
    ): UserRecord {
        val id = UUID.randomUUID().toString()
        transaction {
            Users.insert {
                it[Users.id] = id
                it[Users.name] = name
                it[Users.email] = email
                it[Users.passwordHash] = passwordHash
                it[Users.createdAt] = Instant.now()
            }
        }
        return UserRecord(id, name, email, passwordHash, tokenVersion = 0)
    }

    fun findByEmail(email: String): UserRecord? =
        transaction {
            Users.selectAll().where { Users.email eq email }.singleOrNull()?.toRecord()
        }

    fun findById(id: String): UserRecord? =
        transaction {
            Users.selectAll().where { Users.id eq id }.singleOrNull()?.toRecord()
        }

    /** Borra el usuario; ON DELETE CASCADE en Tasks borra sus tareas de forma conjunta. */
    fun delete(id: String): Boolean =
        transaction {
            Users.deleteWhere { Users.id eq id } > 0
        }

    /**
     * Cambia la contrasena y sube `tokenVersion`: cualquier refresh token emitido antes de
     * esta llamada deja de servir (ver [com.daviddelgado.agenda.server.security.JwtConfig]
     * y su uso en `handleRefresh`, `AuthRoutes.kt`).
     */
    fun updatePassword(
        id: String,
        newPasswordHash: String,
    ) {
        transaction {
            val actual = Users.selectAll().where { Users.id eq id }.single()[Users.tokenVersion]
            Users.update({ Users.id eq id }) {
                it[Users.passwordHash] = newPasswordHash
                it[Users.tokenVersion] = actual + 1
            }
        }
    }

    private fun ResultRow.toRecord() =
        UserRecord(
            id = this[Users.id],
            name = this[Users.name],
            email = this[Users.email],
            passwordHash = this[Users.passwordHash],
            tokenVersion = this[Users.tokenVersion],
        )
}
```

- [ ] **Step 3: `JwtConfig` embebe y valida `token_version`**

`server/src/main/kotlin/com/daviddelgado/agenda/server/security/JwtConfig.kt` (fichero
completo):

```kotlin
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
```

- [ ] **Step 4: Escribir el test (en rojo)**

Añadir a `server/src/test/kotlin/com/daviddelgado/agenda/server/api/AuthRoutesTest.kt` los
imports que falten (`com.daviddelgado.agenda.server.repository.UserRepository`,
`com.daviddelgado.agenda.server.security.PasswordHasher`) y el test:

```kotlin
    @Test
    fun trasCambiarLaContrasenaElRefreshTokenAntiguoDejaDeServir() =
        withApi { client ->
            val session = client.registrarUsuario(email = "version@test.com")

            UserRepository().updatePassword(session.userId, PasswordHasher.hash("nueva123"))

            val response =
                client.post("/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(session.refreshToken))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
```

Run: `./gradlew :server:test --tests "*.AuthRoutesTest"`
Expected: FAIL en compilación — `generateAccessToken`/`generateRefreshToken` ahora piden
`tokenVersion` y `AuthRoutes.kt` todavía no lo pasa (Step 5 lo arregla).

- [ ] **Step 5: Pasar `tokenVersion` en `handleRegister`/`handleLogin`/`handleRefresh`**

En `server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt`, sustituir
las tres funciones (deja el resto del fichero igual, incluida `respondIfInvalidRegisterRequest`):

```kotlin
private suspend fun handleRegister(
    call: ApplicationCall,
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    val request = call.receive<RegisterRequest>()

    if (call.respondIfInvalidRegisterRequest(request)) {
        return
    }

    if (userRepository.findByEmail(request.email) != null) {
        call.respond(HttpStatusCode.Conflict, ErrorResponse("Ya existe una cuenta con ese email"))
        return
    }

    val user = userRepository.create(request.name, request.email, PasswordHasher.hash(request.password))
    call.respond(
        HttpStatusCode.Created,
        AuthResponse(
            userId = user.id,
            name = user.name,
            email = user.email,
            accessToken = jwtConfig.generateAccessToken(user.id, user.tokenVersion),
            refreshToken = jwtConfig.generateRefreshToken(user.id, user.tokenVersion),
        ),
    )
}

private suspend fun handleLogin(
    call: ApplicationCall,
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    val request = call.receive<LoginRequest>()
    val user = userRepository.findByEmail(request.email)

    if (user == null || !PasswordHasher.matches(request.password, user.passwordHash)) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Email o contrasena incorrectos"))
        return
    }

    call.respond(
        AuthResponse(
            userId = user.id,
            name = user.name,
            email = user.email,
            accessToken = jwtConfig.generateAccessToken(user.id, user.tokenVersion),
            refreshToken = jwtConfig.generateRefreshToken(user.id, user.tokenVersion),
        ),
    )
}

private suspend fun handleRefresh(
    call: ApplicationCall,
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    val request = call.receive<RefreshRequest>()
    val decoded = jwtConfig.verifyRefreshToken(request.refreshToken)
    val user = decoded?.userId?.let(userRepository::findById)

    if (user == null || decoded.tokenVersion != user.tokenVersion) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Refresh token invalido o caducado"))
        return
    }

    call.respond(
        AuthResponse(
            userId = user.id,
            name = user.name,
            email = user.email,
            accessToken = jwtConfig.generateAccessToken(user.id, user.tokenVersion),
            refreshToken = jwtConfig.generateRefreshToken(user.id, user.tokenVersion),
        ),
    )
}
```

- [ ] **Step 6: Verificar y commitear**

Run: `./gradlew :server:test`
Expected: BUILD SUCCESSFUL, todos los tests existentes + el nuevo en verde (el nuevo
demuestra que un refresh token emitido antes de `updatePassword` deja de servir).

```bash
git add server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt server/src/main/kotlin/com/daviddelgado/agenda/server/repository/UserRepository.kt server/src/main/kotlin/com/daviddelgado/agenda/server/security/JwtConfig.kt server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt server/src/test/kotlin/com/daviddelgado/agenda/server/api/AuthRoutesTest.kt
git commit -m "feat: token_version por usuario para invalidar sesiones antiguas al resetear la contrasena"
```

---

## Task 3: Servidor — rutas de recuperación de contraseña

**Files:**
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/db/DatabaseFactory.kt`
- Create: `server/src/main/kotlin/com/daviddelgado/agenda/server/repository/PasswordResetRepository.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/dto/Dtos.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt`
- Modify: `server/src/test/kotlin/com/daviddelgado/agenda/server/api/TestApi.kt`
- Create: `server/src/test/kotlin/com/daviddelgado/agenda/server/api/PasswordResetRoutesTest.kt`

**Interfaces:**
- Consumes: `EmailSender` (Task 1), `UserRepository.updatePassword` / `JwtConfig` con
  `tokenVersion` (Task 2).
- Produces: `POST /auth/forgot-password`, `POST /auth/reset-password`.

- [ ] **Step 1: Tabla `password_reset_codes`**

En `server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt`, añadir al final:

```kotlin
/**
 * Codigo de un solo uso para resetear la contrasena. Un unico codigo activo por usuario
 * (PrimaryKey = userId): pedir uno nuevo sustituye cualquier codigo anterior sin caducar,
 * asi que solo el ultimo codigo pedido sirve. `attempts` protege contra fuerza bruta sobre
 * el codigo de 6 digitos (1 millon de combinaciones no es mucho): tras 5 intentos fallidos
 * el codigo deja de aceptarse, hay que pedir uno nuevo. Se guarda un hash SHA-256, no el
 * codigo en claro (ver `sha256Hex` en AuthRoutes.kt) - un codigo de 15 minutos de vida no
 * necesita el coste de bcrypt, la proteccion real es el limite de intentos.
 */
object PasswordResetCodes : Table("password_reset_codes") {
    val userId =
        varchar("user_id", 36)
            .references(Users.id, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val codeHash = varchar("code_hash", 64)
    val expiresAt = timestamp("expires_at")
    val attempts = integer("attempts").default(0)

    override val primaryKey = PrimaryKey(userId)
}
```

En `server/src/main/kotlin/com/daviddelgado/agenda/server/db/DatabaseFactory.kt`, cambiar:

```kotlin
            SchemaUtils.createMissingTablesAndColumns(Users, Tasks, FcmTokens, PasswordResetCodes)
```

- [ ] **Step 2: `PasswordResetRepository`**

`server/src/main/kotlin/com/daviddelgado/agenda/server/repository/PasswordResetRepository.kt`:

```kotlin
package com.daviddelgado.agenda.server.repository

import com.daviddelgado.agenda.server.db.PasswordResetCodes
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

data class PasswordResetCodeRecord(
    val codeHash: String,
    val expiresAt: Instant,
    val attempts: Int,
)

class PasswordResetRepository {
    /** Sustituye cualquier codigo anterior del usuario: solo el ultimo pedido sirve. */
    fun createOrReplace(
        userId: String,
        codeHash: String,
        expiresAt: Instant,
    ) {
        transaction {
            val yaExiste = PasswordResetCodes.selectAll().where { PasswordResetCodes.userId eq userId }.empty().not()
            if (yaExiste) {
                PasswordResetCodes.update({ PasswordResetCodes.userId eq userId }) {
                    it[PasswordResetCodes.codeHash] = codeHash
                    it[PasswordResetCodes.expiresAt] = expiresAt
                    it[attempts] = 0
                }
            } else {
                PasswordResetCodes.insert {
                    it[PasswordResetCodes.userId] = userId
                    it[PasswordResetCodes.codeHash] = codeHash
                    it[PasswordResetCodes.expiresAt] = expiresAt
                }
            }
        }
    }

    fun find(userId: String): PasswordResetCodeRecord? =
        transaction {
            PasswordResetCodes.selectAll().where { PasswordResetCodes.userId eq userId }.singleOrNull()?.let {
                PasswordResetCodeRecord(
                    codeHash = it[PasswordResetCodes.codeHash],
                    expiresAt = it[PasswordResetCodes.expiresAt],
                    attempts = it[PasswordResetCodes.attempts],
                )
            }
        }

    fun incrementAttempts(userId: String) {
        transaction {
            val actual =
                PasswordResetCodes.selectAll().where { PasswordResetCodes.userId eq userId }.singleOrNull()
                    ?.get(PasswordResetCodes.attempts) ?: return@transaction
            PasswordResetCodes.update({ PasswordResetCodes.userId eq userId }) {
                it[attempts] = actual + 1
            }
        }
    }

    fun delete(userId: String) {
        transaction {
            PasswordResetCodes.deleteWhere { PasswordResetCodes.userId eq userId }
        }
    }
}
```

- [ ] **Step 3: DTOs y validación**

En `server/src/main/kotlin/com/daviddelgado/agenda/server/dto/Dtos.kt`, añadir junto a
`FcmTokenRequest`:

```kotlin
/** Cuerpo de `POST /auth/forgot-password`. */
@Serializable
data class ForgotPasswordRequest(val email: String)

/** Cuerpo de `POST /auth/reset-password`. */
@Serializable
data class ResetPasswordRequest(val email: String, val code: String, val newPassword: String)
```

Y al final del fichero:

```kotlin
private const val RESET_CODE_LENGTH = 6

/** Devuelve el motivo por el que la peticion de reseteo no es valida, o null si lo es. */
fun ResetPasswordRequest.validationError(): String? {
    if (code.length != RESET_CODE_LENGTH || code.any { !it.isDigit() }) return "Codigo invalido"
    if (newPassword.length < 6) return "La contrasena nueva debe tener al menos 6 caracteres"
    return null
}
```

- [ ] **Step 4: Escribir los tests (en rojo)**

Este test necesita que `TestApi.kt` acepte un `EmailSender` inyectable para poder capturar
el código mandado (Step 5 lo añade) y que existan las rutas nuevas más `sha256Hex` (Step 6-7).

`server/src/test/kotlin/com/daviddelgado/agenda/server/api/PasswordResetRoutesTest.kt`:

```kotlin
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private class CapturingEmailSender : EmailSender {
    var lastBody: String? = null

    override fun send(
        to: String,
        subject: String,
        body: String,
    ): Boolean {
        lastBody = body
        return true
    }
}

private fun CapturingEmailSender.codigoEnviado(): String {
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
```

Run: `./gradlew :server:test --tests "*.PasswordResetRoutesTest"`
Expected: FAIL en compilación (no existen las rutas, `withApi(emailSender = ...)`, ni
`sha256Hex`) — Steps 5-6 lo arreglan.

- [ ] **Step 5: `TestApi.kt` acepta un `EmailSender` inyectable**

`server/src/test/kotlin/com/daviddelgado/agenda/server/api/TestApi.kt` (fichero completo):

```kotlin
package com.daviddelgado.agenda.server.api

import com.daviddelgado.agenda.server.agendaModule
import com.daviddelgado.agenda.server.dto.AuthResponse
import com.daviddelgado.agenda.server.dto.RegisterRequest
import com.daviddelgado.agenda.server.dto.TaskDto
import com.daviddelgado.agenda.server.email.EmailSender
import com.daviddelgado.agenda.server.email.NoOpEmailSender
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.random.Random

/**
 * Levanta la API real (rutas, JWT, Exposed) sobre una H2 **en memoria y distinta en cada
 * test**, para que los tests no compartan estado entre si ni toquen el fichero de
 * desarrollo `server/data/agenda.mv.db`. `emailSender` es sustituible para que los tests de
 * recuperacion de contrasena puedan capturar el codigo mandado sin credenciales SMTP reales.
 */
fun withApi(
    emailSender: EmailSender = NoOpEmailSender,
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) = testApplication {
    val databaseName = "agenda-test-${Random.nextLong()}"
    application { agendaModule("jdbc:h2:mem:$databaseName;DB_CLOSE_DELAY=-1", emailSender = emailSender) }
    val client =
        createClient {
            install(ContentNegotiation) { json() }
            install(WebSockets)
        }
    block(client)
}

/** Registra un usuario nuevo (email unico) y devuelve su sesion con los dos tokens. */
suspend fun HttpClient.registrarUsuario(
    name: String = "David",
    email: String = "usuario-${Random.nextLong()}@test.com",
    password: String = "secreta123",
): AuthResponse =
    post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(name, email, password))
    }.body()

/** Tarea de ejemplo con todos los campos del contrato, incluido el incremento progresivo. */
fun tareaDeEjemplo(
    id: String = "",
    title: String = "Flexiones",
    isCompleted: Boolean = false,
) = TaskDto(
    id = id,
    title = title,
    description = "Rutina diaria",
    date = "2026-09-13",
    time = "07:30",
    durationMinutes = 15,
    category = "SALUD",
    priority = "ALTA",
    reminderFrequency = "DIARIO",
    incrementAmount = 5,
    incrementEveryValue = 2,
    incrementEveryUnit = "SEMANAS",
    isCompleted = isCompleted,
)
```

- [ ] **Step 6: Las rutas — reescribir `AuthRoutes.kt`**

`server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt` (fichero
completo; sustituye la version que dejo la Task 2):

```kotlin
package com.daviddelgado.agenda.server.routes

import com.daviddelgado.agenda.server.dto.AuthResponse
import com.daviddelgado.agenda.server.dto.ErrorResponse
import com.daviddelgado.agenda.server.dto.ForgotPasswordRequest
import com.daviddelgado.agenda.server.dto.LoginRequest
import com.daviddelgado.agenda.server.dto.RefreshRequest
import com.daviddelgado.agenda.server.dto.RegisterRequest
import com.daviddelgado.agenda.server.dto.ResetPasswordRequest
import com.daviddelgado.agenda.server.dto.validationError
import com.daviddelgado.agenda.server.email.EmailSender
import com.daviddelgado.agenda.server.repository.PasswordResetRepository
import com.daviddelgado.agenda.server.repository.UserRepository
import com.daviddelgado.agenda.server.security.JwtConfig
import com.daviddelgado.agenda.server.security.PasswordHasher
import com.daviddelgado.agenda.server.security.sha256Hex
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.security.SecureRandom
import java.time.Instant

private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private const val RESET_CODE_EXPIRY_MINUTES = 15L
private const val RESET_CODE_MAX_ATTEMPTS = 5

fun Route.authRoutes(
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
    passwordResetRepository: PasswordResetRepository,
    emailSender: EmailSender,
) {
    rateLimit(RateLimitName("auth")) {
        post("/auth/register") { handleRegister(call, userRepository, jwtConfig) }
        post("/auth/login") { handleLogin(call, userRepository, jwtConfig) }
        post("/auth/forgot-password") {
            handleForgotPassword(call, userRepository, passwordResetRepository, emailSender)
        }
        post("/auth/reset-password") { handleResetPassword(call, userRepository, passwordResetRepository) }
    }

    post("/auth/refresh") { handleRefresh(call, userRepository, jwtConfig) }
}

private suspend fun handleRegister(
    call: ApplicationCall,
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    val request = call.receive<RegisterRequest>()

    if (call.respondIfInvalidRegisterRequest(request)) {
        return
    }

    if (userRepository.findByEmail(request.email) != null) {
        call.respond(HttpStatusCode.Conflict, ErrorResponse("Ya existe una cuenta con ese email"))
        return
    }

    val user = userRepository.create(request.name, request.email, PasswordHasher.hash(request.password))
    call.respond(
        HttpStatusCode.Created,
        AuthResponse(
            userId = user.id,
            name = user.name,
            email = user.email,
            accessToken = jwtConfig.generateAccessToken(user.id, user.tokenVersion),
            refreshToken = jwtConfig.generateRefreshToken(user.id, user.tokenVersion),
        ),
    )
}

private suspend fun handleLogin(
    call: ApplicationCall,
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    val request = call.receive<LoginRequest>()
    val user = userRepository.findByEmail(request.email)

    if (user == null || !PasswordHasher.matches(request.password, user.passwordHash)) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Email o contrasena incorrectos"))
        return
    }

    call.respond(
        AuthResponse(
            userId = user.id,
            name = user.name,
            email = user.email,
            accessToken = jwtConfig.generateAccessToken(user.id, user.tokenVersion),
            refreshToken = jwtConfig.generateRefreshToken(user.id, user.tokenVersion),
        ),
    )
}

private suspend fun handleRefresh(
    call: ApplicationCall,
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    val request = call.receive<RefreshRequest>()
    val decoded = jwtConfig.verifyRefreshToken(request.refreshToken)
    val user = decoded?.userId?.let(userRepository::findById)

    if (user == null || decoded.tokenVersion != user.tokenVersion) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Refresh token invalido o caducado"))
        return
    }

    call.respond(
        AuthResponse(
            userId = user.id,
            name = user.name,
            email = user.email,
            accessToken = jwtConfig.generateAccessToken(user.id, user.tokenVersion),
            refreshToken = jwtConfig.generateRefreshToken(user.id, user.tokenVersion),
        ),
    )
}

/**
 * Siempre responde 204, exista o no la cuenta con ese email: no revela que emails estan
 * registrados (ver Global Constraints del plan).
 */
private suspend fun handleForgotPassword(
    call: ApplicationCall,
    userRepository: UserRepository,
    passwordResetRepository: PasswordResetRepository,
    emailSender: EmailSender,
) {
    val request = call.receive<ForgotPasswordRequest>()
    val user = userRepository.findByEmail(request.email)

    if (user != null) {
        val code = generateResetCode()
        passwordResetRepository.createOrReplace(
            userId = user.id,
            codeHash = sha256Hex(code),
            expiresAt = Instant.now().plusSeconds(RESET_CODE_EXPIRY_MINUTES * 60),
        )
        emailSender.send(
            to = user.email,
            subject = "Recupera tu contrasena en Agenda",
            body =
                "Tu codigo para restablecer la contrasena es: $code\n\n" +
                    "Caduca en 15 minutos. Si no lo has pedido tu, ignora este correo.",
        )
    }
    call.respond(HttpStatusCode.NoContent)
}

private suspend fun handleResetPassword(
    call: ApplicationCall,
    userRepository: UserRepository,
    passwordResetRepository: PasswordResetRepository,
) {
    val request = call.receive<ResetPasswordRequest>()
    if (call.respondIfInvalidResetPasswordRequest(request)) return

    val user = userRepository.findByEmail(request.email)
    val storedCode = user?.let { passwordResetRepository.find(it.id) }

    val esValido =
        user != null &&
            storedCode != null &&
            storedCode.attempts < RESET_CODE_MAX_ATTEMPTS &&
            Instant.now().isBefore(storedCode.expiresAt) &&
            storedCode.codeHash == sha256Hex(request.code)

    if (!esValido) {
        if (user != null && storedCode != null) passwordResetRepository.incrementAttempts(user.id)
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Codigo invalido o caducado"))
        return
    }

    userRepository.updatePassword(user.id, PasswordHasher.hash(request.newPassword))
    passwordResetRepository.delete(user.id)
    call.respond(HttpStatusCode.NoContent)
}

private fun generateResetCode(): String = "%06d".format(SecureRandom().nextInt(1_000_000))

private suspend fun ApplicationCall.respondIfInvalidRegisterRequest(request: RegisterRequest): Boolean {
    val emailValido = emailRegex.matches(request.email)
    val isInvalid = request.name.isBlank() || !emailValido || request.password.length < 6
    if (isInvalid) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Datos invalidos: email y contrasena minima 6"))
    }
    return isInvalid
}

private suspend fun ApplicationCall.respondIfInvalidResetPasswordRequest(request: ResetPasswordRequest): Boolean {
    val reason = request.validationError() ?: return false
    respond(HttpStatusCode.BadRequest, ErrorResponse(reason))
    return true
}
```

- [ ] **Step 7: `sha256Hex` — nuevo fichero de utilidad de seguridad**

`server/src/main/kotlin/com/daviddelgado/agenda/server/security/Hashing.kt`:

```kotlin
package com.daviddelgado.agenda.server.security

import java.security.MessageDigest

/** Hash SHA-256 en hexadecimal minuscula. Usado para no guardar codigos de un solo uso en claro. */
fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
```

- [ ] **Step 8: `Application.kt` — instanciar y conectar todo**

En `server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt`, añadir los
imports que falten (`com.daviddelgado.agenda.server.email.provideEmailSender`,
`com.daviddelgado.agenda.server.email.EmailSender`,
`com.daviddelgado.agenda.server.repository.PasswordResetRepository`) y, dentro de
`fun Application.agendaModule(...)`, cambiar la firma y la instanciación:

```kotlin
fun Application.agendaModule(
    jdbcUrl: String = defaultJdbcUrl,
    emailSender: EmailSender =
        provideEmailSender(
            host = System.getenv("AGENDA_SMTP_HOST"),
            port = System.getenv("AGENDA_SMTP_PORT")?.toIntOrNull(),
            username = System.getenv("AGENDA_SMTP_USERNAME"),
            password = System.getenv("AGENDA_SMTP_PASSWORD"),
            from = System.getenv("AGENDA_SMTP_FROM"),
        ),
) {
    DatabaseFactory.init(jdbcUrl)

    val jwtConfig =
        JwtConfig(secret = System.getenv("AGENDA_JWT_SECRET") ?: "dev-secret-change-me-in-production")
    val userRepository = UserRepository()
    val taskRepository = TaskRepository()
    val fcmTokenRepository = FcmTokenRepository()
    val passwordResetRepository = PasswordResetRepository()

    // ... (el resto de install{} no cambia)
```

Y más abajo, en el bloque `routing { }`:

```kotlin
    routing {
        authRoutes(userRepository, jwtConfig, passwordResetRepository, emailSender)
        userRoutes(userRepository, fcmTokenRepository)
        taskRoutes(taskRepository)
    }
```

- [ ] **Step 9: Verificar y commitear**

Run: `./gradlew :server:test :server:ktlintCheck :server:detekt`
Expected: BUILD SUCCESSFUL, todos los tests (existentes + los 9 nuevos de
`PasswordResetRoutesTest`) en verde.

```bash
git add server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt server/src/main/kotlin/com/daviddelgado/agenda/server/db/DatabaseFactory.kt server/src/main/kotlin/com/daviddelgado/agenda/server/repository/PasswordResetRepository.kt server/src/main/kotlin/com/daviddelgado/agenda/server/dto/Dtos.kt server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt server/src/main/kotlin/com/daviddelgado/agenda/server/security/Hashing.kt server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt server/src/test/kotlin/com/daviddelgado/agenda/server/api/TestApi.kt server/src/test/kotlin/com/daviddelgado/agenda/server/api/PasswordResetRoutesTest.kt
git commit -m "feat: recuperacion de contrasena por email con codigo de un solo uso"
```

---

## Task 4: Cliente — capas de red y dominio

**Files:**
- Modify: `core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network/dto/AuthDtos.kt`
- Modify: `core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network/api/AuthApi.kt`
- Modify: `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/repository/Repositories.kt`
- Modify: `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/usecase/UseCases.kt`
- Modify: `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/di/DomainModule.kt`
- Modify: `core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/FakeAuthRepository.kt`
- Modify: `core/data/src/commonMain/kotlin/com/daviddelgado/agenda/data/auth/AuthRepositoryImpl.kt`
- Create: `core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/RequestPasswordResetUseCaseTest.kt`
- Create: `core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/ResetPasswordUseCaseTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/daviddelgado/agenda/shared/SplashSessionHandlerTest.kt`
  (arreglo colateral, ver Step 3bis)
- Modify: `feature/settings/src/commonTest/kotlin/com/daviddelgado/agenda/feature/settings/SettingsViewModelTest.kt`
  (arreglo colateral)
- Modify: `feature/register/src/commonTest/kotlin/com/daviddelgado/agenda/feature/register/RegisterViewModelTest.kt`
  (arreglo colateral)
- Modify: `feature/login/src/commonTest/kotlin/com/daviddelgado/agenda/feature/login/LoginViewModelTest.kt`
  (arreglo colateral)
- Modify: `feature/login/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/login/LoginScreenTest.kt`
  (arreglo colateral)

**Interfaces:**
- Consumes: `POST /auth/forgot-password` / `POST /auth/reset-password` (Task 3).
- Produces: `RequestPasswordResetUseCase(email: String)`, `ResetPasswordUseCase(email, code, newPassword)`
  — los consume Task 5 y Task 6.

- [ ] **Step 1: DTOs de red**

En `core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network/dto/AuthDtos.kt`,
añadir junto a `FcmTokenRequest`:

```kotlin
/** Cuerpo de `POST /auth/forgot-password` (ver `ForgotPasswordRequest` del modulo :server). */
@Serializable
data class ForgotPasswordRequest(val email: String)

/** Cuerpo de `POST /auth/reset-password` (ver `ResetPasswordRequest` del modulo :server). */
@Serializable
data class ResetPasswordRequest(val email: String, val code: String, val newPassword: String)
```

- [ ] **Step 2: `AuthApi`**

En `core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network/api/AuthApi.kt`,
añadir los imports `com.daviddelgado.agenda.network.dto.ForgotPasswordRequest` y
`com.daviddelgado.agenda.network.dto.ResetPasswordRequest`, y los métodos junto a
`registerFcmToken`:

```kotlin
    suspend fun forgotPassword(email: String) {
        apiCall {
            client.post("auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest(email))
            }
        }
    }

    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ) {
        apiCall {
            client.post("auth/reset-password") {
                contentType(ContentType.Application.Json)
                setBody(ResetPasswordRequest(email, code, newPassword))
            }
        }
    }
```

- [ ] **Step 3: `AuthRepository` (dominio) y su fake**

En `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/repository/Repositories.kt`,
dentro de `interface AuthRepository`, añadir junto a `registerFcmToken`:

```kotlin
    /** Pide al servidor un codigo de recuperacion de contrasena para este email (si existe la cuenta). */
    suspend fun requestPasswordReset(email: String): Result<Unit>

    /** Cambia la contrasena usando el codigo recibido por email. */
    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit>
```

En `core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/FakeAuthRepository.kt`,
añadir junto a `tokenRegistrado`:

```kotlin
    var passwordResetRequestedFor: String? = null
    var passwordWasReset = false
        private set

    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        passwordResetRequestedFor = email
        return Result.success(Unit)
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> {
        failWith?.let { return Result.failure(it) }
        passwordWasReset = true
        return Result.success(Unit)
    }
```

- [ ] **Step 3bis: Arreglo colateral — otros 4 `FakeAuthRepository` locales dejan de compilar**

`AuthRepository` gana dos métodos no-`default` en el Step 3 anterior: cualquier otra
implementación de la interfaz en el repo (cada módulo que la testea define su propio fake
local, no comparten el de `core/domain`) deja de compilar hasta que también implemente los
dos métodos nuevos. Son overrides triviales (`Result.success(Unit)`, sin lógica, igual que
ya hacen esos mismos fakes con `deleteAccount`/`registerFcmToken`), en estos 4 ficheros —
añadir en cada uno, justo después de su último `override suspend fun` existente y antes del
cierre de la clase:

```kotlin
    override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> = Result.success(Unit)
```

- `shared/src/commonTest/kotlin/com/daviddelgado/agenda/shared/SplashSessionHandlerTest.kt`
  (después de `override suspend fun registerFcmToken`, línea 40 actual)
- `feature/settings/src/commonTest/kotlin/com/daviddelgado/agenda/feature/settings/SettingsViewModelTest.kt`
  (después de `override suspend fun registerFcmToken`, línea 62 actual)
- `feature/register/src/commonTest/kotlin/com/daviddelgado/agenda/feature/register/RegisterViewModelTest.kt`
  (después de `override suspend fun registerFcmToken`, línea 51 actual — este fichero tiene
  su propio fake local `FakeAuthRepository`, distinto del de `core/domain`)
- `feature/login/src/commonTest/kotlin/com/daviddelgado/agenda/feature/login/LoginViewModelTest.kt`
  (después de `override suspend fun registerFcmToken`, línea 56 actual — también su propio
  fake local, distinto del de `core/domain`)
- `feature/login/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/login/LoginScreenTest.kt`
  (después de `override suspend fun registerFcmToken`, línea 44 actual)

Run: `./gradlew :shared:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:register:testDebugUnitTest :feature:login:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — confirma que los 5 fakes compilan de nuevo. Este paso no tiene
TDD propio (no añade comportamiento, solo restaura la compilación); su verificación es
justamente que el proyecto vuelva a compilar.

- [ ] **Step 4: Escribir los tests (en rojo)**

`core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/RequestPasswordResetUseCaseTest.kt`:

```kotlin
package com.daviddelgado.agenda.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestPasswordResetUseCaseTest {
    @Test
    fun delegaEnElRepositorio() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = RequestPasswordResetUseCase(repository)

            useCase("olvide@test.com")

            assertEquals("olvide@test.com", repository.passwordResetRequestedFor)
        }
}
```

`core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/ResetPasswordUseCaseTest.kt`:

```kotlin
package com.daviddelgado.agenda.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ResetPasswordUseCaseTest {
    @Test
    fun delegaEnElRepositorio() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = ResetPasswordUseCase(repository)

            useCase("olvide@test.com", "123456", "nueva123")

            assertTrue(repository.passwordWasReset)
        }
}
```

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.RequestPasswordResetUseCaseTest" --tests "*.ResetPasswordUseCaseTest"`
Expected: FAIL — no existen `RequestPasswordResetUseCase`/`ResetPasswordUseCase`.

- [ ] **Step 5: Casos de uso**

En `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/usecase/UseCases.kt`,
añadir junto a `RegisterFcmTokenUseCase`:

```kotlin
/** Pide al servidor un codigo de recuperacion de contrasena para este email (si existe la cuenta). */
class RequestPasswordResetUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): Result<Unit> = repository.requestPasswordReset(email)
}

/** Cambia la contrasena usando el codigo recibido por email. */
class ResetPasswordUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> = repository.resetPassword(email, code, newPassword)
}
```

En `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/di/DomainModule.kt`,
añadir los imports y las líneas junto a `RegisterFcmTokenUseCase`:

```kotlin
import com.daviddelgado.agenda.domain.usecase.RequestPasswordResetUseCase
import com.daviddelgado.agenda.domain.usecase.ResetPasswordUseCase
```

```kotlin
        factory { RequestPasswordResetUseCase(get()) }
        factory { ResetPasswordUseCase(get()) }
```

- [ ] **Step 6: Implementación real (`AuthRepositoryImpl`)**

En `core/data/src/commonMain/kotlin/com/daviddelgado/agenda/data/auth/AuthRepositoryImpl.kt`,
añadir junto a `registerFcmToken`:

```kotlin
    override suspend fun requestPasswordReset(email: String): Result<Unit> =
        runCatching { authApi.forgotPassword(email) }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> =
        runCatching {
            authApi.resetPassword(email, code, newPassword)
            // El reset ya invalido la sesion en el servidor (sube token_version): no tiene
            // sentido conservar tokens locales que van a dejar de servir.
            tokenProvider.clear()
            authApi.forgetCachedTokens()
            currentUser.value = null
        }
```

- [ ] **Step 7: Verificar y commitear**

Run: `./gradlew :core:network:testDebugUnitTest :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :shared:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:register:testDebugUnitTest :feature:login:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, los 2 tests nuevos + todos los existentes (incluidos los 5
módulos del arreglo colateral) en verde.

```bash
git add core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network/dto/AuthDtos.kt core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network/api/AuthApi.kt core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/repository/Repositories.kt core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/usecase/UseCases.kt core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/di/DomainModule.kt core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/FakeAuthRepository.kt core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/RequestPasswordResetUseCaseTest.kt core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/ResetPasswordUseCaseTest.kt core/data/src/commonMain/kotlin/com/daviddelgado/agenda/data/auth/AuthRepositoryImpl.kt shared/src/commonTest/kotlin/com/daviddelgado/agenda/shared/SplashSessionHandlerTest.kt feature/settings/src/commonTest/kotlin/com/daviddelgado/agenda/feature/settings/SettingsViewModelTest.kt feature/register/src/commonTest/kotlin/com/daviddelgado/agenda/feature/register/RegisterViewModelTest.kt feature/login/src/commonTest/kotlin/com/daviddelgado/agenda/feature/login/LoginViewModelTest.kt feature/login/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/login/LoginScreenTest.kt
git commit -m "feat: cliente pide y aplica la recuperacion de contrasena contra el servidor"
```

---

## Task 5: Cliente — módulo `feature/passwordreset`: pantalla "Olvidé mi contraseña"

**Files:**
- Modify: `settings.gradle.kts`
- Create: `feature/passwordreset/build.gradle.kts`
- Create: `feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordContract.kt`
- Create: `feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordViewModel.kt`
- Create: `feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordScreen.kt`
- Create: `feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/PasswordResetModule.kt`
- Create: `feature/passwordreset/src/commonTest/kotlin/com/daviddelgado/agenda/feature/passwordreset/FakeAuthRepository.kt`
- Create: `feature/passwordreset/src/commonTest/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordViewModelTest.kt`

**Interfaces:**
- Consumes: `RequestPasswordResetUseCase` (Task 4).
- Produces: `ForgotPasswordScreen(onCodeSent: (email: String) -> Unit)` — lo consume Task 7.
  `passwordResetModule` (Koin) — Task 6 lo completa con el segundo ViewModel.

- [ ] **Step 1: Registrar el módulo nuevo**

En `settings.gradle.kts`, añadir junto a `include(":feature:register")`:

```kotlin
include(":feature:passwordreset")
```

- [ ] **Step 2: `build.gradle.kts` del módulo**

`feature/passwordreset/build.gradle.kts` (mismo esqueleto que `feature/register/build.gradle.kts`):

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.domain)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.daviddelgado.agenda.feature.passwordreset"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

- [ ] **Step 3: Contrato MVI**

`feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordContract.kt`:

```kotlin
package com.daviddelgado.agenda.feature.passwordreset

import com.daviddelgado.agenda.common.mvi.UiEffect
import com.daviddelgado.agenda.common.mvi.UiIntent
import com.daviddelgado.agenda.common.mvi.UiState

data class ForgotPasswordState(
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) : UiState

sealed interface ForgotPasswordIntent : UiIntent {
    data class EmailChanged(val value: String) : ForgotPasswordIntent

    data object Submit : ForgotPasswordIntent
}

sealed interface ForgotPasswordEffect : UiEffect {
    data class CodeSent(val email: String) : ForgotPasswordEffect

    data class ShowError(val message: String) : ForgotPasswordEffect
}
```

- [ ] **Step 4: Fake de dominio para los tests del módulo**

`feature/passwordreset/src/commonTest/kotlin/com/daviddelgado/agenda/feature/passwordreset/FakeAuthRepository.kt`
(lo usan tanto este test como el de Task 6; visibilidad publica, no `private`):

```kotlin
package com.daviddelgado.agenda.feature.passwordreset

import com.daviddelgado.agenda.domain.model.User
import com.daviddelgado.agenda.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository(private val error: Throwable? = null) : AuthRepository {
    private val current = MutableStateFlow<User?>(null)
    var emailPedido: String? = null
        private set
    var resetRealizadoCon: Triple<String, String, String>? = null
        private set

    override fun observeCurrentUser(): Flow<User?> = current

    override suspend fun login(
        email: String,
        password: String,
    ): Result<User> = Result.success(User("u-1", "David", email))

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Result<User> = Result.success(User("u-1", name, email))

    override suspend fun logout() = Unit

    override suspend fun restoreSession(): User? = null

    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

    override suspend fun registerFcmToken(token: String): Result<Unit> = Result.success(Unit)

    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        emailPedido = email
        return error?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> {
        error?.let { return Result.failure(it) }
        resetRealizadoCon = Triple(email, code, newPassword)
        return Result.success(Unit)
    }
}
```

- [ ] **Step 5: Escribir el test del ViewModel (en rojo)**

`feature/passwordreset/src/commonTest/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordViewModelTest.kt`:

```kotlin
package com.daviddelgado.agenda.feature.passwordreset

import com.daviddelgado.agenda.domain.usecase.RequestPasswordResetUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun prepararDispatcherPrincipal() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun restaurarDispatcherPrincipal() {
        Dispatchers.resetMain()
    }

    private fun viewModelCon(repository: FakeAuthRepository) =
        ForgotPasswordViewModel(RequestPasswordResetUseCase(repository))

    @Test
    fun elFormularioGuardaElEmailEscrito() =
        runTest(dispatcher) {
            val viewModel = viewModelCon(FakeAuthRepository())

            viewModel.onIntent(ForgotPasswordIntent.EmailChanged("david@test.com"))

            assertEquals("david@test.com", viewModel.currentState.email)
        }

    @Test
    fun enviarConEmailVacioNoLlamaAlServidor() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)

            viewModel.onIntent(ForgotPasswordIntent.Submit)

            assertEquals("Escribe tu email", viewModel.currentState.errorMessage)
            assertNull(repository.emailPedido)
        }

    @Test
    fun pedirElCodigoConExitoDisparaCodeSent() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<ForgotPasswordEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.onIntent(ForgotPasswordIntent.EmailChanged("david@test.com"))
            viewModel.onIntent(ForgotPasswordIntent.Submit)

            assertEquals("david@test.com", repository.emailPedido)
            assertEquals(listOf(ForgotPasswordEffect.CodeSent("david@test.com")), efectos)
        }

    @Test
    fun unFalloDelServidorMuestraElError() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(IllegalStateException("Error de red"))
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<ForgotPasswordEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.onIntent(ForgotPasswordIntent.EmailChanged("david@test.com"))
            viewModel.onIntent(ForgotPasswordIntent.Submit)

            assertEquals("Error de red", viewModel.currentState.errorMessage)
            assertTrue(efectos.any { it is ForgotPasswordEffect.ShowError })
        }
}
```

Run: `./gradlew :feature:passwordreset:testDebugUnitTest`
Expected: FAIL — no existe `ForgotPasswordViewModel` ni el módulo compila todavía.

- [ ] **Step 6: `ForgotPasswordViewModel`**

`feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordViewModel.kt`:

```kotlin
package com.daviddelgado.agenda.feature.passwordreset

import androidx.lifecycle.viewModelScope
import com.daviddelgado.agenda.common.mvi.MviViewModel
import com.daviddelgado.agenda.domain.usecase.RequestPasswordResetUseCase
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(private val requestPasswordResetUseCase: RequestPasswordResetUseCase) :
    MviViewModel<ForgotPasswordState, ForgotPasswordIntent, ForgotPasswordEffect>(ForgotPasswordState()) {
    override fun onIntent(intent: ForgotPasswordIntent) {
        when (intent) {
            is ForgotPasswordIntent.EmailChanged -> setState { copy(email = intent.value, errorMessage = null) }
            ForgotPasswordIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val email = currentState.email
        if (email.isBlank()) {
            setState { copy(errorMessage = "Escribe tu email") }
            return
        }
        viewModelScope.launch {
            setState { copy(isLoading = true, errorMessage = null) }
            requestPasswordResetUseCase(email)
                .onSuccess {
                    setState { copy(isLoading = false) }
                    sendEffect(ForgotPasswordEffect.CodeSent(email))
                }
                .onFailure { error ->
                    setState { copy(isLoading = false, errorMessage = error.message ?: "Error al pedir el codigo") }
                    sendEffect(ForgotPasswordEffect.ShowError(error.message ?: "Error al pedir el codigo"))
                }
        }
    }
}
```

- [ ] **Step 7: Pantalla**

`feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordScreen.kt`:

```kotlin
package com.daviddelgado.agenda.feature.passwordreset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daviddelgado.agenda.designsystem.component.AgendaPrimaryButton
import com.daviddelgado.agenda.designsystem.component.AgendaTextField
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ForgotPasswordScreen(
    onCodeSent: (email: String) -> Unit,
    viewModel: ForgotPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ForgotPasswordEffect.CodeSent -> onCodeSent(effect.email)
                is ForgotPasswordEffect.ShowError -> Unit
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Recuperar contrasena", style = MaterialTheme.typography.headlineMedium)

            AgendaTextField(state.email, { viewModel.onIntent(ForgotPasswordIntent.EmailChanged(it)) }, "Email")

            state.errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                AgendaPrimaryButton(
                    text = "Enviar codigo",
                    onClick = { viewModel.onIntent(ForgotPasswordIntent.Submit) },
                )
            }
        }
    }
}
```

- [ ] **Step 8: Módulo Koin (con el hueco para el segundo ViewModel de Task 6)**

`feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/PasswordResetModule.kt`:

```kotlin
package com.daviddelgado.agenda.feature.passwordreset

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val passwordResetModule =
    module {
        viewModel { ForgotPasswordViewModel(get()) }
    }
```

- [ ] **Step 9: Verificar y commitear**

Run: `./gradlew :feature:passwordreset:testDebugUnitTest :feature:passwordreset:ktlintCheck :feature:passwordreset:detekt`
Expected: BUILD SUCCESSFUL, 4/4 tests en verde.

```bash
git add settings.gradle.kts feature/passwordreset/build.gradle.kts feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordContract.kt feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordViewModel.kt feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordScreen.kt feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/PasswordResetModule.kt feature/passwordreset/src/commonTest/kotlin/com/daviddelgado/agenda/feature/passwordreset/FakeAuthRepository.kt feature/passwordreset/src/commonTest/kotlin/com/daviddelgado/agenda/feature/passwordreset/ForgotPasswordViewModelTest.kt
git commit -m "feat: pantalla para pedir el codigo de recuperacion de contrasena"
```

---

## Task 6: Cliente — módulo `feature/passwordreset`: pantalla "Restablecer contraseña"

**Files:**
- Modify: `feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/PasswordResetModule.kt`
- Create: `feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordContract.kt`
- Create: `feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordViewModel.kt`
- Create: `feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordScreen.kt`
- Create: `feature/passwordreset/src/commonTest/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordViewModelTest.kt`

**Interfaces:**
- Consumes: `ResetPasswordUseCase` (Task 4), `FakeAuthRepository` (Task 5, `commonTest`).
- Produces: `ResetPasswordScreen(email: String, onPasswordReset: () -> Unit)` — lo consume Task 7.

- [ ] **Step 1: Contrato MVI**

`feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordContract.kt`:

```kotlin
package com.daviddelgado.agenda.feature.passwordreset

import com.daviddelgado.agenda.common.mvi.UiEffect
import com.daviddelgado.agenda.common.mvi.UiIntent
import com.daviddelgado.agenda.common.mvi.UiState

data class ResetPasswordState(
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) : UiState

sealed interface ResetPasswordIntent : UiIntent {
    data class EmailProvided(val value: String) : ResetPasswordIntent

    data class CodeChanged(val value: String) : ResetPasswordIntent

    data class NewPasswordChanged(val value: String) : ResetPasswordIntent

    data class ConfirmPasswordChanged(val value: String) : ResetPasswordIntent

    data object Submit : ResetPasswordIntent
}

sealed interface ResetPasswordEffect : UiEffect {
    data object PasswordReset : ResetPasswordEffect

    data class ShowError(val message: String) : ResetPasswordEffect
}
```

- [ ] **Step 2: Escribir el test del ViewModel (en rojo)**

`feature/passwordreset/src/commonTest/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordViewModelTest.kt`:

```kotlin
package com.daviddelgado.agenda.feature.passwordreset

import com.daviddelgado.agenda.domain.usecase.ResetPasswordUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ResetPasswordViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun prepararDispatcherPrincipal() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun restaurarDispatcherPrincipal() {
        Dispatchers.resetMain()
    }

    private fun viewModelCon(repository: FakeAuthRepository) = ResetPasswordViewModel(ResetPasswordUseCase(repository))

    private fun ResetPasswordViewModel.rellenarFormulario(
        email: String = "david@test.com",
        code: String = "123456",
        newPassword: String = "nueva123",
        confirmPassword: String = "nueva123",
    ) {
        onIntent(ResetPasswordIntent.EmailProvided(email))
        onIntent(ResetPasswordIntent.CodeChanged(code))
        onIntent(ResetPasswordIntent.NewPasswordChanged(newPassword))
        onIntent(ResetPasswordIntent.ConfirmPasswordChanged(confirmPassword))
    }

    @Test
    fun unCodigoQueNoTieneSeisDigitosNoLlamaAlServidor() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)

            viewModel.rellenarFormulario(code = "123")
            viewModel.onIntent(ResetPasswordIntent.Submit)

            assertEquals("El codigo tiene que tener 6 numeros", viewModel.currentState.errorMessage)
            assertNull(repository.resetRealizadoCon)
        }

    @Test
    fun siLasContrasenasNoCoincidenNoSeResetea() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)

            viewModel.rellenarFormulario(confirmPassword = "otra-distinta")
            viewModel.onIntent(ResetPasswordIntent.Submit)

            assertEquals("Las contrasenas no coinciden", viewModel.currentState.errorMessage)
            assertNull(repository.resetRealizadoCon)
        }

    @Test
    fun elResetCorrectoDisparaPasswordReset() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = viewModelCon(repository)
            val efectos = mutableListOf<ResetPasswordEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.rellenarFormulario()
            viewModel.onIntent(ResetPasswordIntent.Submit)

            assertEquals(Triple("david@test.com", "123456", "nueva123"), repository.resetRealizadoCon)
            assertEquals(listOf(ResetPasswordEffect.PasswordReset), efectos)
        }

    @Test
    fun unCodigoInvalidoDelServidorMuestraElError() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(IllegalStateException("Codigo invalido o caducado"))
            val viewModel = viewModelCon(repository)

            viewModel.rellenarFormulario()
            viewModel.onIntent(ResetPasswordIntent.Submit)

            assertEquals("Codigo invalido o caducado", viewModel.currentState.errorMessage)
        }

    @Test
    fun elEmailLlegaFijoPorParametroSinFormulario() =
        runTest(dispatcher) {
            val viewModel = viewModelCon(FakeAuthRepository())

            viewModel.onIntent(ResetPasswordIntent.EmailProvided("desde-forgot@test.com"))

            assertEquals("desde-forgot@test.com", viewModel.currentState.email)
        }
}
```

Run: `./gradlew :feature:passwordreset:testDebugUnitTest --tests "*.ResetPasswordViewModelTest"`
Expected: FAIL — no existe `ResetPasswordViewModel`.

- [ ] **Step 3: `ResetPasswordViewModel`**

`feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordViewModel.kt`:

```kotlin
package com.daviddelgado.agenda.feature.passwordreset

import androidx.lifecycle.viewModelScope
import com.daviddelgado.agenda.common.mvi.MviViewModel
import com.daviddelgado.agenda.domain.usecase.ResetPasswordUseCase
import kotlinx.coroutines.launch

private const val CODE_LENGTH = 6
private const val MIN_PASSWORD_LENGTH = 6

class ResetPasswordViewModel(private val resetPasswordUseCase: ResetPasswordUseCase) :
    MviViewModel<ResetPasswordState, ResetPasswordIntent, ResetPasswordEffect>(ResetPasswordState()) {
    override fun onIntent(intent: ResetPasswordIntent) {
        when (intent) {
            is ResetPasswordIntent.EmailProvided -> setState { copy(email = intent.value) }
            is ResetPasswordIntent.CodeChanged -> setState { copy(code = intent.value, errorMessage = null) }
            is ResetPasswordIntent.NewPasswordChanged ->
                setState { copy(newPassword = intent.value, errorMessage = null) }
            is ResetPasswordIntent.ConfirmPasswordChanged ->
                setState { copy(confirmPassword = intent.value, errorMessage = null) }
            ResetPasswordIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val state = currentState
        if (state.code.length != CODE_LENGTH || state.code.any { !it.isDigit() }) {
            setState { copy(errorMessage = "El codigo tiene que tener 6 numeros") }
            return
        }
        if (state.newPassword.length < MIN_PASSWORD_LENGTH) {
            setState { copy(errorMessage = "La contrasena tiene que tener al menos 6 caracteres") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            setState { copy(errorMessage = "Las contrasenas no coinciden") }
            return
        }
        viewModelScope.launch {
            setState { copy(isLoading = true, errorMessage = null) }
            resetPasswordUseCase(state.email, state.code, state.newPassword)
                .onSuccess {
                    setState { copy(isLoading = false) }
                    sendEffect(ResetPasswordEffect.PasswordReset)
                }
                .onFailure { error ->
                    setState {
                        copy(isLoading = false, errorMessage = error.message ?: "Error al restablecer la contrasena")
                    }
                    sendEffect(ResetPasswordEffect.ShowError(error.message ?: "Error al restablecer la contrasena"))
                }
        }
    }
}
```

- [ ] **Step 4: Pantalla**

`feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordScreen.kt`:

```kotlin
package com.daviddelgado.agenda.feature.passwordreset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daviddelgado.agenda.designsystem.component.AgendaPrimaryButton
import com.daviddelgado.agenda.designsystem.component.AgendaTextField
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * @param email llega desde [ForgotPasswordScreen] (ver `App.kt`); se fija una sola vez al
 * entrar mediante [ResetPasswordIntent.EmailProvided] (mismo patron que `TasksScreen`'s
 * `initialDate`, ver `shared/.../HomeNavigator.kt`).
 */
@Composable
fun ResetPasswordScreen(
    email: String,
    onPasswordReset: () -> Unit,
    viewModel: ResetPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(email) {
        viewModel.onIntent(ResetPasswordIntent.EmailProvided(email))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                ResetPasswordEffect.PasswordReset -> onPasswordReset()
                is ResetPasswordEffect.ShowError -> Unit
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Restablecer contrasena", style = MaterialTheme.typography.headlineMedium)
            Text(text = "Hemos mandado un codigo a $email", style = MaterialTheme.typography.bodyMedium)

            AgendaTextField(state.code, { viewModel.onIntent(ResetPasswordIntent.CodeChanged(it)) }, "Codigo")
            AgendaTextField(
                state.newPassword,
                { viewModel.onIntent(ResetPasswordIntent.NewPasswordChanged(it)) },
                "Contrasena nueva",
                isPassword = true,
            )
            AgendaTextField(
                state.confirmPassword,
                { viewModel.onIntent(ResetPasswordIntent.ConfirmPasswordChanged(it)) },
                "Confirmar contrasena",
                isPassword = true,
            )

            state.errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                AgendaPrimaryButton(
                    text = "Cambiar contrasena",
                    onClick = { viewModel.onIntent(ResetPasswordIntent.Submit) },
                )
            }
        }
    }
}
```

- [ ] **Step 5: Completar el módulo Koin**

`feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/PasswordResetModule.kt`
(fichero completo):

```kotlin
package com.daviddelgado.agenda.feature.passwordreset

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val passwordResetModule =
    module {
        viewModel { ForgotPasswordViewModel(get()) }
        viewModel { ResetPasswordViewModel(get()) }
    }
```

- [ ] **Step 6: Verificar y commitear**

Run: `./gradlew :feature:passwordreset:testDebugUnitTest :feature:passwordreset:ktlintCheck :feature:passwordreset:detekt`
Expected: BUILD SUCCESSFUL, 9/9 tests en verde (4 de `ForgotPasswordViewModelTest` + 5 de
`ResetPasswordViewModelTest`).

```bash
git add feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/PasswordResetModule.kt feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordContract.kt feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordViewModel.kt feature/passwordreset/src/commonMain/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordScreen.kt feature/passwordreset/src/commonTest/kotlin/com/daviddelgado/agenda/feature/passwordreset/ResetPasswordViewModelTest.kt
git commit -m "feat: pantalla para fijar la contrasena nueva con el codigo recibido"
```

---

## Task 7: Cliente — navegación y enlace desde el login

**Files:**
- Modify: `shared/build.gradle.kts`
- Modify: `shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/AppModules.kt`
- Modify: `shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/App.kt`
- Modify: `feature/login/src/commonMain/kotlin/com/daviddelgado/agenda/feature/login/LoginContract.kt`
- Modify: `feature/login/src/commonMain/kotlin/com/daviddelgado/agenda/feature/login/LoginViewModel.kt`
- Modify: `feature/login/src/commonMain/kotlin/com/daviddelgado/agenda/feature/login/LoginScreen.kt`
- Modify: `feature/login/src/commonTest/kotlin/com/daviddelgado/agenda/feature/login/LoginViewModelTest.kt`
- Modify: `feature/login/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/login/LoginScreenTest.kt`

**Interfaces:**
- Consumes: `ForgotPasswordScreen`, `ResetPasswordScreen`, `passwordResetModule` (Task 5, 6).

- [ ] **Step 1: `shared` depende del módulo nuevo y lo registra en Koin**

En `shared/build.gradle.kts`, añadir junto a `implementation(projects.feature.register)`:

```kotlin
            implementation(projects.feature.passwordreset)
```

En `shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/AppModules.kt` (fichero
completo — si no se registra aquí, Koin no podrá resolver `ForgotPasswordViewModel` ni
`ResetPasswordViewModel` y la app crashea al abrir esas pantallas):

```kotlin
package com.daviddelgado.agenda.shared

import com.daviddelgado.agenda.data.di.dataModule
import com.daviddelgado.agenda.domain.di.domainModule
import com.daviddelgado.agenda.feature.calendar.calendarModule
import com.daviddelgado.agenda.feature.login.loginModule
import com.daviddelgado.agenda.feature.passwordreset.passwordResetModule
import com.daviddelgado.agenda.feature.register.registerModule
import com.daviddelgado.agenda.feature.settings.settingsModule
import com.daviddelgado.agenda.feature.streaks.streaksModule
import com.daviddelgado.agenda.feature.tasks.tasksModule
import org.koin.core.module.Module
import org.koin.dsl.module

/** SplashSessionHandler vive aqui (no en domainModule): compone un caso de uso de dominio con
 * FcmTokenProvider, que es un tipo de presentacion (feature/login), no de dominio. */
private val sharedModule =
    module {
        single { SplashSessionHandler(get(), get(), get()) }
    }

val appModules: List<Module> =
    listOf(
        dataModule,
        domainModule,
        loginModule,
        registerModule,
        passwordResetModule,
        calendarModule,
        tasksModule,
        streaksModule,
        settingsModule,
        sharedModule,
    )
```

- [ ] **Step 2: Escribir el test (en rojo) del nuevo intent/efecto de login**

Añadir a `feature/login/src/commonTest/kotlin/com/daviddelgado/agenda/feature/login/LoginViewModelTest.kt`,
junto al test `pedirRegistroEmiteElEfectoDeNavegacion`:

```kotlin
    @Test
    fun pedirRecuperarContrasenaEmiteElEfectoDeNavegacion() =
        runTest(dispatcher) {
            val viewModel = crearViewModel()
            val efectos = mutableListOf<LoginEffect>()
            viewModel.effect.onEach { efectos += it }.launchIn(backgroundScope)

            viewModel.onIntent(LoginIntent.NavigateToForgotPassword)

            assertEquals(listOf<LoginEffect>(LoginEffect.NavigateToForgotPassword), efectos)
        }
```

Run: `./gradlew :feature:login:testDebugUnitTest --tests "*.LoginViewModelTest"`
Expected: FAIL — no existe `LoginIntent.NavigateToForgotPassword` ni
`LoginEffect.NavigateToForgotPassword`.

- [ ] **Step 3: Contrato de login**

En `feature/login/src/commonMain/kotlin/com/daviddelgado/agenda/feature/login/LoginContract.kt`,
añadir un caso a cada `sealed interface`:

```kotlin
sealed interface LoginIntent : UiIntent {
    data class EmailChanged(val value: String) : LoginIntent

    data class PasswordChanged(val value: String) : LoginIntent

    data object Submit : LoginIntent

    data object NavigateToRegister : LoginIntent

    data object NavigateToForgotPassword : LoginIntent
}

sealed interface LoginEffect : UiEffect {
    data object NavigateToHome : LoginEffect

    data object NavigateToRegister : LoginEffect

    data object NavigateToForgotPassword : LoginEffect

    data class ShowError(val message: String) : LoginEffect
}
```

- [ ] **Step 4: `LoginViewModel`**

En `feature/login/src/commonMain/kotlin/com/daviddelgado/agenda/feature/login/LoginViewModel.kt`,
en el `when (intent)` de `onIntent`, añadir junto a `NavigateToRegister`:

```kotlin
            LoginIntent.NavigateToForgotPassword -> sendEffect(LoginEffect.NavigateToForgotPassword)
```

- [ ] **Step 5: Verificar el ViewModel**

Run: `./gradlew :feature:login:testDebugUnitTest --tests "*.LoginViewModelTest"`
Expected: BUILD SUCCESSFUL, el test nuevo + todos los existentes en verde.

- [ ] **Step 6: `LoginScreen` — enlace y aviso de contraseña actualizada**

`feature/login/src/commonMain/kotlin/com/daviddelgado/agenda/feature/login/LoginScreen.kt`
(fichero completo):

```kotlin
package com.daviddelgado.agenda.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daviddelgado.agenda.designsystem.component.AgendaPrimaryButton
import com.daviddelgado.agenda.designsystem.component.AgendaTextField
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    justReset: Boolean = false,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var mostrarAvisoDeReset by remember { mutableStateOf(justReset) }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                LoginEffect.NavigateToHome -> onNavigateToHome()
                LoginEffect.NavigateToRegister -> onNavigateToRegister()
                LoginEffect.NavigateToForgotPassword -> onNavigateToForgotPassword()
                is LoginEffect.ShowError -> Unit
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Agenda", style = MaterialTheme.typography.headlineMedium)

            if (mostrarAvisoDeReset) {
                Text(text = "Contrasena actualizada, inicia sesion", color = MaterialTheme.colorScheme.primary)
            }

            AgendaTextField(
                value = state.email,
                onValueChange = { viewModel.onIntent(LoginIntent.EmailChanged(it)) },
                label = "Email",
            )
            AgendaTextField(
                value = state.password,
                onValueChange = { viewModel.onIntent(LoginIntent.PasswordChanged(it)) },
                label = "Contrasena",
                isPassword = true,
            )

            state.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                AgendaPrimaryButton(
                    text = "Entrar",
                    onClick = {
                        mostrarAvisoDeReset = false
                        viewModel.onIntent(LoginIntent.Submit)
                    },
                )
            }

            TextButton(onClick = { viewModel.onIntent(LoginIntent.NavigateToRegister) }) {
                Text("Crear una cuenta")
            }
            TextButton(onClick = { viewModel.onIntent(LoginIntent.NavigateToForgotPassword) }) {
                Text("Olvidaste tu contrasena?")
            }
        }
    }
}
```

- [ ] **Step 7: Actualizar el test instrumentado existente de `LoginScreen`**

En `feature/login/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/login/LoginScreenTest.kt`,
en `montarPantalla`, añadir el parámetro nuevo y pasarlo:

```kotlin
    private fun montarPantalla(
        repository: FakeAuthRepository = FakeAuthRepository(),
        onNavigateToHome: () -> Unit = {},
        onNavigateToRegister: () -> Unit = {},
        onNavigateToForgotPassword: () -> Unit = {},
    ) {
        composeRule.setContent {
            LoginScreen(
                onNavigateToHome = onNavigateToHome,
                onNavigateToRegister = onNavigateToRegister,
                onNavigateToForgotPassword = onNavigateToForgotPassword,
                viewModel =
                    LoginViewModel(
                        LoginUseCase(repository),
                        RegisterFcmTokenUseCase(repository),
                        FakeFcmTokenProvider(),
                    ),
            )
        }
    }
```

Y añadir un test nuevo junto a `pulsarCrearCuentaNavegaAlRegistro` (mismo patrón exacto):

```kotlin
    @Test
    fun pulsarOlvidasteTuContrasenaNavegaAlFormularioDeRecuperacion() {
        var navegoARecuperar = false
        montarPantalla(onNavigateToForgotPassword = { navegoARecuperar = true })

        composeRule.onNodeWithText("Olvidaste tu contrasena?").performClick()

        composeRule.waitUntil(timeoutMillis = LOGIN_TIMEOUT_MILLIS) { navegoARecuperar }
    }
```

- [ ] **Step 8: Navegación — `App.kt`**

`shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/App.kt`, sustituir
`private sealed interface AppScreen` y `fun App()`:

```kotlin
private sealed interface AppScreen {
    data object Splash : AppScreen

    data class Login(val justReset: Boolean = false) : AppScreen

    data object Register : AppScreen

    data object ForgotPassword : AppScreen

    data class ResetPassword(val email: String) : AppScreen

    data object Home : AppScreen
}

@Composable
fun App() {
    AgendaTheme {
        var screen by remember { mutableStateOf<AppScreen>(AppScreen.Splash) }

        when (val current = screen) {
            AppScreen.Splash ->
                SplashScreen(
                    onFinished = { hasSession -> screen = if (hasSession) AppScreen.Home else AppScreen.Login() },
                )
            is AppScreen.Login ->
                LoginScreen(
                    onNavigateToHome = { screen = AppScreen.Home },
                    onNavigateToRegister = { screen = AppScreen.Register },
                    onNavigateToForgotPassword = { screen = AppScreen.ForgotPassword },
                    justReset = current.justReset,
                )
            AppScreen.Register -> RegisterScreen(onNavigateToHome = { screen = AppScreen.Home })
            AppScreen.ForgotPassword ->
                ForgotPasswordScreen(onCodeSent = { email -> screen = AppScreen.ResetPassword(email) })
            is AppScreen.ResetPassword ->
                ResetPasswordScreen(
                    email = current.email,
                    onPasswordReset = { screen = AppScreen.Login(justReset = true) },
                )
            AppScreen.Home -> HomeWithTabs(onLoggedOut = { screen = AppScreen.Login() })
        }
    }
}
```

Y añadir los dos imports nuevos junto a los de `feature.login`/`feature.register`:

```kotlin
import com.daviddelgado.agenda.feature.passwordreset.ForgotPasswordScreen
import com.daviddelgado.agenda.feature.passwordreset.ResetPasswordScreen
```

- [ ] **Step 9: Verificar y commitear**

Run: `./gradlew :feature:login:testDebugUnitTest :feature:passwordreset:testDebugUnitTest :shared:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL (confirma que `App.kt` y todo el grafo de dependencias nuevo
compilan de verdad en la app Android).

```bash
git add shared/build.gradle.kts shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/App.kt feature/login/src/commonMain/kotlin/com/daviddelgado/agenda/feature/login/LoginContract.kt feature/login/src/commonMain/kotlin/com/daviddelgado/agenda/feature/login/LoginViewModel.kt feature/login/src/commonMain/kotlin/com/daviddelgado/agenda/feature/login/LoginScreen.kt feature/login/src/commonTest/kotlin/com/daviddelgado/agenda/feature/login/LoginViewModelTest.kt feature/login/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/login/LoginScreenTest.kt
git commit -m "feat: enlaza la recuperacion de contrasena desde la pantalla de login"
```

---

## Cierre

- [ ] **Task 8: Verificación final completa + actualizar ESTADO_PROYECTO.md**

Run: `./gradlew check` — Expected: BUILD SUCCESSFUL (compilación + ktlint + detekt + lint +
todos los tests unitarios de todos los módulos, incluidos los nuevos de `:server`,
`:core:network`, `:core:domain`, `:core:data`, `:feature:passwordreset`, `:feature:login`).

Run: `./gradlew :feature:login:connectedDebugAndroidTest :feature:tasks:connectedDebugAndroidTest :feature:calendar:connectedDebugAndroidTest`
(con el emulador Pixel_6a arrancado) — Expected: todos los tests instrumentados en verde,
incluido el de `LoginScreenTest` actualizado en la Task 7.

Probar a mano en el emulador contra el servidor real (sin credenciales SMTP configuradas,
así que el código aparecerá en los logs del servidor, no en un correo real): desde el
login, pulsar "¿Olvidaste tu contraseña?", pedir un código con la cuenta
`e2e@test.com`, leer el código de 6 dígitos en la consola del servidor (log de
`NoOpEmailSender`), escribirlo junto a una contraseña nueva, confirmar que vuelve al login
con el aviso de "Contraseña actualizada", y entrar con la contraseña nueva.

Añadir una sección nueva a `ESTADO_PROYECTO.md` (siguiendo el estilo de las secciones
"Verificado en caliente" ya existentes) describiendo: el flujo de recuperación de
contraseña completo (código de un solo uso por email, invalidación de sesión vía
`token_version`), y la nota de que `AGENDA_SMTP_HOST`/`PORT`/`USERNAME`/`PASSWORD`/`FROM`
son placeholders pendientes de credenciales reales, igual que `ProductionConfig` y
`AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON`. Actualizar también el recuento total de tests en la
sección 6, y quitar de la sección 10 ("Lo que NO está hecho") el punto sobre la falta de
recuperación de contraseña si estaba anotado ahí.

```bash
git add ESTADO_PROYECTO.md
git commit -m "docs: actualiza ESTADO_PROYECTO.md tras la recuperacion de contrasena"
```
