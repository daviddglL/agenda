package com.daviddelgado.agenda.server

import com.daviddelgado.agenda.server.db.DatabaseFactory
import com.daviddelgado.agenda.server.dto.ErrorResponse
import com.daviddelgado.agenda.server.email.EmailSender
import com.daviddelgado.agenda.server.email.provideEmailSender
import com.daviddelgado.agenda.server.push.providePushSender
import com.daviddelgado.agenda.server.reminder.runReminderLoop
import com.daviddelgado.agenda.server.repository.FcmTokenRepository
import com.daviddelgado.agenda.server.repository.PasswordResetRepository
import com.daviddelgado.agenda.server.repository.TaskRepository
import com.daviddelgado.agenda.server.repository.UserRepository
import com.daviddelgado.agenda.server.routes.authRoutes
import com.daviddelgado.agenda.server.routes.taskRoutes
import com.daviddelgado.agenda.server.routes.userRoutes
import com.daviddelgado.agenda.server.security.JwtConfig
import com.daviddelgado.agenda.server.security.resolveClientAddress
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/** Cada cuanto se hace ping/timeout de los WebSockets de `/tasks/ws` (ver TaskEventBroadcaster). */
private const val WEBSOCKET_PING_TIMEOUT_MILLIS = 15_000L

/** H2 en fichero por defecto; `AGENDA_DB_URL` permite apuntar a otra base (p.ej. Postgres). */
private val defaultJdbcUrl: String
    get() = System.getenv("AGENDA_DB_URL") ?: "jdbc:h2:file:./data/agenda;AUTO_SERVER=TRUE"

/**
 * IPs del/de los proxy(s) inverso(s) propio(s) (nginx, Cloudflare, etc.) delante del servidor,
 * separadas por comas. Vacio por defecto: sin desplegar detras de un proxy, el rate limiting
 * usa siempre la conexion TCP real (ver [resolveClientAddress]).
 */
private val trustedProxiesFromEnv: Set<String>
    get() =
        System.getenv("AGENDA_TRUSTED_PROXIES")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = { agendaModule() })
        .start(wait = false)
    startReminderLoop()
    Thread.currentThread().join()
}

/**
 * Arranca [runReminderLoop] en segundo plano. Solo se llama desde `main()`: los tests usan
 * `agendaModule()` directamente via `testApplication` y nunca pasan por aqui, asi que el bucle
 * real nunca corre durante los tests. `GlobalScope` es deliberado (ver [DelicateCoroutinesApi]):
 * el bucle debe vivir mientras viva el proceso, igual que el propio servidor Netty.
 * `Dispatchers.IO` (y no el `Default` que heredaria `GlobalScope` por defecto) porque el bucle
 * hace trabajo bloqueante de verdad: transacciones JDBC (`transaction {}`) y, por cada token,
 * una llamada HTTP sincrona a Firebase; `Default` esta pensado para trabajo de CPU, no para
 * bloquear hilos con I/O. `dispatcher` es un parametro con valor por defecto (regla detekt
 * InjectDispatcher) en vez de un `Dispatchers.IO` fijo en el cuerpo de la funcion.
 */
@OptIn(DelicateCoroutinesApi::class)
private fun startReminderLoop(dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    val pushSender = providePushSender(System.getenv("AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON"))
    GlobalScope.launch(dispatcher) {
        runReminderLoop(TaskRepository(), FcmTokenRepository(), pushSender)
    }
}

/**
 * @param jdbcUrl base de datos a usar. Los tests pasan una H2 en memoria distinta por test
 * para no compartir estado entre ellos ni tocar el fichero de desarrollo.
 */
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
    trustedProxies: Set<String> = trustedProxiesFromEnv,
) {
    DatabaseFactory.init(jdbcUrl)

    // En desarrollo se usa un secreto por defecto; en produccion SIEMPRE se debe fijar
    // la variable de entorno AGENDA_JWT_SECRET con un valor largo y aleatorio propio.
    val jwtConfig =
        JwtConfig(secret = System.getenv("AGENDA_JWT_SECRET") ?: "dev-secret-change-me-in-production")
    val userRepository = UserRepository()
    val taskRepository = TaskRepository()
    val fcmTokenRepository = FcmTokenRepository()
    val passwordResetRepository = PasswordResetRepository()

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            },
        )
    }

    install(CallLogging)

    install(WebSockets) {
        pingPeriodMillis = WEBSOCKET_PING_TIMEOUT_MILLIS
        timeoutMillis = WEBSOCKET_PING_TIMEOUT_MILLIS
    }

    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
        allowHeader("Authorization")
    }

    installAuthRateLimit(trustedProxies)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Error interno"))
        }
    }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(jwtConfig.verifier)
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Token invalido o caducado"))
            }
        }
    }

    routing {
        authRoutes(userRepository, jwtConfig, passwordResetRepository, emailSender)
        userRoutes(userRepository, fcmTokenRepository)
        taskRoutes(taskRepository)
    }
}

/**
 * Sin la clave por IP el limite seria un unico cubo global para todo el proceso: un solo
 * cliente agotaria las 10 peticiones y bloquearia al resto de usuarios durante 60s.
 * [resolveClientAddress] nunca se fia a ciegas de `X-Forwarded-For` (cualquiera podria
 * mandarla): solo la lee si la conexion llega de verdad de un proxy en [trustedProxies]; sin
 * proxies configurados, usa siempre la conexion TCP real, igual que antes de este fix.
 */
private fun Application.installAuthRateLimit(trustedProxies: Set<String>) {
    install(RateLimit) {
        register(RateLimitName("auth")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call ->
                resolveClientAddress(
                    directRemoteHost = call.request.local.remoteHost,
                    forwardedForHeader = call.request.headers["X-Forwarded-For"],
                    trustedProxies = trustedProxies,
                )
            }
        }
    }
}
