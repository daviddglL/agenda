# Recuperación de contraseña — Design

**Goal:** un usuario que olvida su contraseña puede recuperar el acceso a su cuenta sin
intervención manual, pidiendo un código por email y usándolo para fijar una contraseña
nueva. Cierra el hueco funcional más básico señalado en el análisis de la app: hoy no
existe ningún camino de vuelta si alguien olvida su contraseña, salvo borrar la cuenta y
volver a registrarse.

**Spec de referencia:** este documento. Referencias técnicas no negociables: `markdown.md`
y `ESTADO_PROYECTO.md` en la raíz del repo (Clean Architecture modular + MVI + Koin + KMP +
Compose Multiplatform + Ktor Client/Server, ya establecidos — este diseño no introduce
ninguna tecnología nueva salvo la librería de envío de correo).

## Decisiones ya acordadas con el usuario

- **Entrega por email real vía SMTP** (Jakarta Mail), con una implementación "sin efecto"
  (`NoOpEmailSender`) si no hay credenciales SMTP configuradas — mismo patrón que
  `PushSender`/`NoOpPushSender` de Firebase (`server/.../push/PushSender.kt`): la app entera
  sigue funcionando y no falla en silencio, solo que sin correos reales hasta que se
  configuren las credenciales.
- **Código numérico de un solo uso (6 dígitos)**, no un enlace/deep link: la app no tiene
  hoy ninguna infraestructura de App Links/Universal Links, y un enlace no se podría probar
  en iOS (sin Xcode/Mac disponible, ver `ESTADO_PROYECTO.md` sección 10). El código se
  escribe a mano en la app junto a la contraseña nueva.
- **Se invalidan las sesiones existentes al resetear la contraseña.** Como los JWT son hoy
  sin estado (ningún registro de sesión en el servidor), esto se implementa con un contador
  `token_version` por usuario: cada JWT lleva la versión vigente en el momento de emitirlo;
  resetear la contraseña sube el contador. El **refresh token** deja de servir de inmediato
  (el servidor comprueba la versión en `POST /auth/refresh`); el **access token** ya emitido
  sigue siendo válido hasta que caduca por sí solo (máximo 30 minutos, su vida natural hoy).
  No se comprueba la versión en cada petición autenticada (evitaría una lectura a base de
  datos extra en cada llamada de la app); comprobarla solo al refrescar es el punto de
  control estándar y suficiente: en el peor caso, una sesión robada sigue funcionando un
  máximo de 30 minutos tras el reseteo, nunca más.

## Servidor (`server/`)

### Esquema nuevo

En `server/.../db/Tables.kt`:

```kotlin
// Dentro de object Users, junto a passwordHash:
val tokenVersion = integer("token_version").default(0)
```

```kotlin
/**
 * Codigo de un solo uso para resetear la contrasena. Un unico codigo activo por usuario
 * (PrimaryKey = userId): pedir uno nuevo sustituye cualquier codigo anterior sin caducar,
 * asi que solo el ultimo codigo pedido sirve. `attempts` protege contra fuerza bruta sobre
 * el codigo de 6 digitos (1 millon de combinaciones no es mucho): tras 5 intentos fallidos
 * el codigo deja de aceptarse, hay que pedir uno nuevo.
 */
object PasswordResetCodes : Table("password_reset_codes") {
    val userId =
        varchar("user_id", 36)
            .references(Users.id, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val codeHash = varchar("code_hash", 64) // SHA-256 hex, ver justificacion abajo
    val expiresAt = timestamp("expires_at")
    val attempts = integer("attempts").default(0)

    override val primaryKey = PrimaryKey(userId)
}
```

`SchemaUtils.createMissingTablesAndColumns` (ya usado en `DatabaseFactory.init`) crea la
tabla y la columna nueva sola, sin migración manual — mismo patrón que `FcmTokens` y
`lastReminderSentAt` en tareas anteriores de este proyecto.

**Por qué hash SHA-256 y no bcrypt para el código**: el código vive un máximo de 15 minutos
y solo tiene 10⁶ combinaciones; bcrypt (pensado para contraseñas de larga vida, con coste
computacional deliberado) es coste innecesario aquí. SHA-256 evita guardar el código en
claro en la base de datos sin ese coste — el límite de intentos (5) es la protección real
contra fuerza bruta, no el hash.

### `EmailSender` (nuevo, `server/.../email/EmailSender.kt`)

Mismo patrón que `PushSender.kt`:

```kotlin
fun interface EmailSender {
    /** @return true si el envio se acepto (no garantiza entrega). */
    fun send(to: String, subject: String, body: String): Boolean
}

class SmtpEmailSender(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val from: String,
) : EmailSender {
    // Jakarta Mail (jakarta.mail-api + org.eclipse.angus:angus-mail), sesion SMTP con
    // auth + STARTTLS. send() hace runCatching { Transport.send(...) }, loguea con SLF4J
    // en caso de fallo (igual que FirebasePushSender.send), devuelve el resultado.
}

/**
 * Sin AGENDA_SMTP_HOST configurado, cae a NoOpEmailSender: NO manda el correo, pero deja
 * el codigo visible en el log del servidor (WARN, una sola vez por arranque para el aviso
 * de "no configurado", pero el codigo se loguea siempre que se pide uno) para poder probar
 * el flujo completo en desarrollo sin credenciales SMTP reales — a diferencia del push,
 * donde "no llega nada" es aceptable, aqui la funcionalidad seria imposible de probar de
 * extremo a extremo sin este escape valvula.
 */
object NoOpEmailSender : EmailSender { /* ... */ }

fun provideEmailSender(
    host: String?,
    port: Int?,
    username: String?,
    password: String?,
    from: String?,
): EmailSender = /* ... construye SmtpEmailSender si host+username+password+from estan
                     todos presentes, con runCatching().onFailure { logger.error(...) }
                     igual que providePushSender; si no, NoOpEmailSender */
```

Variables de entorno nuevas (documentar en `ESTADO_PROYECTO.md` junto a
`AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON`, mismo patrón de "placeholder hasta que haya
credenciales reales"): `AGENDA_SMTP_HOST`, `AGENDA_SMTP_PORT` (default 587),
`AGENDA_SMTP_USERNAME`, `AGENDA_SMTP_PASSWORD`, `AGENDA_SMTP_FROM`.

### Rutas nuevas (`server/.../routes/AuthRoutes.kt`)

Se añaden dentro del mismo bloque `rateLimit(RateLimitName("auth"))` que ya envuelve
`/auth/register` y `/auth/login` (comparten el cupo de 10 peticiones/60s por IP):

```
POST /auth/forgot-password  {email}                          -> 204 (siempre, exista o no la cuenta)
POST /auth/reset-password   {email, code, newPassword}       -> 204 en exito
                                                               -> 400 si code invalido/caducado/agotado
                                                               -> 400 si newPassword no cumple la regla (>= 6, misma que registro)
```

`handleForgotPassword`: busca el usuario por email; si existe, genera un código de 6
dígitos (`SecureRandom`, con ceros a la izquierda), lo guarda vía
`PasswordResetRepository.createOrReplace(userId, sha256(code), now + 15min)` y llama a
`emailSender.send(...)`. Responde 204 **siempre**, exista o no la cuenta — no revela qué
emails están registrados (mismo principio que ya se sigue en el resto de la API).

`handleResetPassword`: busca el registro de `PasswordResetCodes` para el email; si no
existe, ha caducado, o `attempts >= 5`, responde 400 con un mensaje genérico ("código
inválido o caducado") sin distinguir el motivo. Si el hash no coincide, incrementa
`attempts` y responde el mismo 400. Si coincide: actualiza `passwordHash` y sube
`tokenVersion` del usuario (una sola transacción), borra el registro de
`PasswordResetCodes`, responde 204.

### `UserRepository` / `JwtConfig` / `Application.kt`

- `UserRecord` gana `tokenVersion: Int`; `UserRepository` gana
  `updatePassword(id, newHash, newTokenVersion)`.
- `JwtConfig.generateAccessToken`/`generateRefreshToken` ganan un parámetro
  `tokenVersion: Int` y añaden `.withClaim("tv", tokenVersion)`.
- `JwtConfig.verifyRefreshToken` sigue devolviendo el `userId`; el chequeo de versión se
  hace en `handleRefresh` (`AuthRoutes.kt`), que ya carga el `UserRecord` completo: si
  `decoded.getClaim("tv").asInt() != user.tokenVersion`, responde 401 igual que un refresh
  token inválido.
- Todos los sitios que ya llaman a `generateAccessToken`/`generateRefreshToken`
  (`handleRegister`, `handleLogin`, `handleRefresh`) pasan `user.tokenVersion`.
- Nueva ruta `PasswordResetRepository` (`server/.../repository/PasswordResetRepository.kt`),
  mismo estilo que `FcmTokenRepository`: `createOrReplace`, `find(userId)`,
  `incrementAttempts(userId)`, `delete(userId)`.
- `Application.kt`: instancia `PasswordResetRepository()` y `provideEmailSender(...)` (leyendo
  las variables de entorno nuevas), los pasa a `authRoutes(...)`.

## Cliente

### Red y dominio

- `core/network/.../dto/AuthDtos.kt`: `ForgotPasswordRequest(email: String)`,
  `ResetPasswordRequest(email: String, code: String, newPassword: String)` — mismos
  nombres/forma que sus contrapartidas en el servidor.
- `AuthApi.kt` gana `forgotPassword(email)` y `resetPassword(email, code, newPassword)`,
  mismo estilo que `registerFcmToken`.
- `AuthRepository` (interfaz, `core/domain/.../repository/Repositories.kt`) gana:
  ```kotlin
  suspend fun requestPasswordReset(email: String): Result<Unit>
  suspend fun resetPassword(email: String, code: String, newPassword: String): Result<Unit>
  ```
- `AuthRepositoryImpl` los implementa con `runCatching { authApi.____(...) }`, mismo patrón
  que `registerFcmToken`. `resetPassword` en éxito también limpia cualquier token local
  guardado (`AuthApi.forgetCachedTokens()` + lo que ya hace `logout()` para el
  almacenamiento cifrado), porque el reset ya invalidó la sesión en el servidor y no tendría
  sentido conservar tokens locales que dejarán de servir.
- `core/domain/.../usecase/UseCases.kt`: `RequestPasswordResetUseCase`,
  `ResetPasswordUseCase` — delegan al repositorio, mismo patrón que
  `RegisterFcmTokenUseCase`.
- `DomainModule.kt`: registra ambos casos de uso como `factory`.
- `FakeAuthRepository` (tests): implementaciones fake de los dos métodos nuevos, mismo
  patrón que `registerFcmToken`/`tokenRegistrado`.

### Módulo nuevo `feature/passwordreset`

Mismo `build.gradle.kts` que `feature/register` (KMP + Compose Multiplatform + Koin,
dependencias a `core.common`/`core.designsystem`/`core.domain`). Dos pantallas dentro del
mismo módulo — a diferencia de login/register (puntos de entrada alternativos e
independientes), estas dos son pasos secuenciales de un mismo flujo continuo, no
alternativas, así que no se justifica un módulo por pantalla:

```
feature/passwordreset/src/commonMain/kotlin/.../
  ForgotPasswordContract.kt   (State: email, isLoading, errorMessage / Intent: EmailChanged, Submit / Effect: CodeSent, ShowError)
  ForgotPasswordViewModel.kt
  ForgotPasswordScreen.kt
  ResetPasswordContract.kt    (State: email fijo (llega por parametro), code, newPassword, confirmPassword, isLoading, errorMessage / Intent: CodeChanged, NewPasswordChanged, ConfirmPasswordChanged, Submit / Effect: PasswordReset, ShowError)
  ResetPasswordViewModel.kt
  ResetPasswordScreen.kt
  PasswordResetModule.kt      (registra los dos ViewModel en Koin)
```

`ResetPasswordViewModel` valida en el reductor (antes de llamar al caso de uso, igual que
`RegisterViewModel` ya valida `password == confirmPassword`) que `newPassword ==
confirmPassword` y que el código tiene 6 dígitos, para dar el error al momento sin ida y
vuelta al servidor.

`ResetPasswordScreen` recibe el email como parámetro del `@Composable`
(`ResetPasswordScreen(email: String, onNavigateToLogin: () -> Unit, viewModel: ...)`), no
vía Koin: mismo patrón ya establecido para pasar un valor de navegación a un ViewModel
creado por `koinViewModel()` que usa `TasksScreen(initialDate: LocalDate?, ...)` (ver
`shared/.../HomeNavigator.kt`) — un `LaunchedEffect(Unit) { viewModel.onIntent(EmailProvided(email)) }`
que fija el email en el estado una sola vez al entrar.

### Navegación (`shared/App.kt`)

`AppScreen` gana dos estados nuevos, y el `Login` existente pasa de `data object` a `data
class` para poder llevar el aviso de "contraseña actualizada" (ver más abajo):

```kotlin
data class Login(val justReset: Boolean = false) : AppScreen // antes: data object Login
data object ForgotPassword : AppScreen
data class ResetPassword(val email: String) : AppScreen
```

- `LoginScreen` gana un `TextButton` "¿Olvidaste tu contraseña?" bajo el formulario, con un
  nuevo callback `onNavigateToForgotPassword` (mismo patrón que `onNavigateToRegister`ya
  existente) — requiere añadir `LoginIntent.NavigateToForgotPassword`/
  `LoginEffect.NavigateToForgotPassword` al contrato existente, igual que ya existe el par
  para registro.
- `ForgotPasswordScreen` en éxito (`CodeSent`) navega a `AppScreen.ResetPassword(email)`
  pasando el email introducido.
- `ResetPasswordScreen` en éxito (`PasswordReset`) vuelve a `AppScreen.Login(justReset =
  true)` (nuevo parámetro `Boolean` en el `data class`/`data object` `Login`, no se toca
  `LoginContract`/`LoginViewModel`). `LoginScreen` gana un parámetro de `@Composable`
  `justReset: Boolean = false`: si es `true`, muestra un `Text` de confirmación
  ("Contraseña actualizada, inicia sesión") encima del formulario mediante
  `remember { mutableStateOf(justReset) }` propio de la Composable — un valor de UI
  puramente local, no estado de negocio, así que no necesita pasar por el reductor MVI.

## Manejo de errores

- Email no registrado en `/auth/forgot-password`: 204 igual que si existiera (no hay
  distinción visible para el cliente ni para el usuario).
- Código incorrecto, caducado, o agotados los 5 intentos en `/auth/reset-password`: 400 con
  el mismo mensaje genérico en los tres casos, para no revelar cuál de las tres cosas pasó
  (evita que alguien deduzca "el código existe pero está caducado" vs "no pedisteis
  ninguno").
- Límite de peticiones por IP (10/60s) ya cubre `/auth/forgot-password` y
  `/auth/reset-password` al compartir el bloque `rateLimit(RateLimitName("auth"))`.
- Fallo real de SMTP (credenciales mal puestas, servidor caído): se loguea el error real
  (no un mensaje engañoso) y la petición **sigue respondiendo 204** — el usuario no puede
  distinguir "no tenías cuenta" de "tu cuenta existe pero el correo no salió", que es
  deliberado por el mismo principio de no revelar información, aunque signifique que un
  fallo de SMTP real queda invisible para quien lo sufre (visible solo en los logs del
  servidor, que es donde alguien con acceso de operador lo verá).

## Testing

TDD real en todo, siguiendo el estilo ya establecido en el proyecto:

- **Servidor**: nuevo `PasswordResetRoutesTest.kt` (estilo `withApi { client -> ... }` como
  `AuthRoutesTest.kt`/`FcmTokenRoutesTest.kt`): pedir código para email existente -> 204 +
  código en el `NoOpEmailSender`/sender de test capturable; pedir para email inexistente ->
  también 204; reset con código correcto -> 204 + login con la contraseña nueva funciona +
  login con la vieja falla; reset con código incorrecto -> 400, no cambia la contraseña;
  código caducado -> 400; 5 intentos fallidos seguidos -> el 6º ya no acepta ni el código
  correcto; **refresh token emitido antes del reset deja de servir tras el reset** (esta es
  la prueba central de la decisión de invalidar sesiones).
- Test de `EmailSender`: análogo a `PushSenderTest.kt` (`NoOpEmailSender` no lanza,
  `provideEmailSender` cae a NoOp sin host, `SmtpEmailSender` con credenciales
  inválidas no lanza fuera de `runCatching`).
- **Cliente**: `RequestPasswordResetUseCaseTest`/`ResetPasswordUseCaseTest` (estilo
  `RegisterFcmTokenUseCaseTest`, delegan al fake). `ForgotPasswordViewModelTest` y
  `ResetPasswordViewModelTest` (estilo `RegisterViewModelTest`): validación de campos,
  estado de carga, error del servidor mostrado, éxito dispara el efecto de navegación
  correcto. Tests instrumentados de Compose si el módulo los necesita para engancharse al
  patrón ya usado en login/register/tasks/calendar (a decidir en el plan de implementación
  según el tamaño real de la UI).

## Fuera de alcance (documentar como limitación conocida, no resolver ahora)

- No hay verificación de que el dueño del email sea quien pide el reset más allá de tener
  acceso a esa bandeja de entrada — es el comportamiento estándar de cualquier "olvidé mi
  contraseña", no un hueco nuevo de este diseño.
- El access token ya emitido antes de un reset sigue funcionando hasta un máximo de 30
  minutos (ver decisión de `token_version` arriba) — aceptado explícitamente, no un
  descuido.
- Sin verificación de email en el registro (ese es un hueco distinto y anterior a este
  diseño, no se resuelve aquí).
