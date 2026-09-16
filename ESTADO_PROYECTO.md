# Estado del proyecto "agenda"

Documento vivo de continuidad entre sesiones. Léelo antes de tocar nada: recoge las
decisiones ya tomadas para no repetir trabajo ni contradecirlas sin querer. La spec
técnica obligatoria (no negociable) sigue estando en [markdown.md](markdown.md); este
documento es el "qué se ha hecho, qué falta y cómo se arranca" sobre esa base.

Última actualización: 2026-09-16 (cierre del plan `recuperacion-password`: recuperación de
contraseña por email con código de un solo uso, invalidación de sesión vía `token_version`,
nuevo módulo `feature:passwordreset` con dos pantallas y enlace desde el login — verificado
de extremo a extremo a mano en el emulador contra el servidor real, ver sección 7sexies).

## 0. Resumen en una frase

App de agenda/tareas con rachas (KMP: Android + iOS futuro) + backend propio en Ktor con
usuarios, login JWT, recuperación de contraseña por email (código de un solo uso), tareas
completas con todos sus campos editables desde la UI, borrado conjunto, tiempo real por
WebSocket, ajustes de cuenta, buscador/filtro de tareas y recordatorios push reales
(Firebase). Offline-first de verdad (Room como SSOT + tombstones de borrado), validación y
rate limiting en el servidor, logging centralizado con Napier, CI en GitHub Actions,
**repo git inicializado**, y **245 tests automáticos en verde** (226 unitarios + 19
instrumentados de Compose UI en el emulador). Todo verificado en caliente en el emulador
Android contra el servidor real, no solo compilado.

## 1. Arrancar todo (lo primero que querrás hacer)

Tres comandos, cada uno en su terminal. En VS Code están también como tareas: `Ctrl+Shift+P`
> *Tasks: Run Task* > "1. Servidor Ktor", "2. Emulador Android", "3. Instalar y abrir la app"
(ver [.vscode/tasks.json](.vscode/tasks.json) y la guía de la sección 9).

```bash
# 1) Backend (deja la terminal abierta; crea server/data/agenda.mv.db la primera vez)
./gradlew :server:run

# 2) Emulador Android (el AVD de este PC se llama Pixel_6a; lista los tuyos con -list-avds)
%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd Pixel_6a

# 3) Compilar, instalar y abrir la app en el emulador ya arrancado
./gradlew :androidApp:installDebug
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe shell am start -n com.daviddelgado.agenda/com.daviddelgado.agenda.android.MainActivity
```

Cuenta de pruebas que ya existe en la base de datos local del servidor:
**`e2e@test.com` / `secreta123`**. Si borras `server/data/`, se regenera vacía y hay que
registrarse de nuevo desde la app.

Tests: `./gradlew check` (compila todo + ktlint + detekt + lint + los 226 tests unitarios).
Tests de UI de Compose (necesitan el emulador arrancado, sección 6):
`./gradlew :feature:login:connectedDebugAndroidTest :feature:tasks:connectedDebugAndroidTest :feature:calendar:connectedDebugAndroidTest`.

## 2. Estructura de módulos

```
core/common        -> base MVI (UiState/UiIntent/UiEffect, MviViewModel) + randomEntityId()
core/designsystem  -> tema Compose Multiplatform (paleta de Figma) + AgendaDropdownField
core/domain        -> modelos, enums, interfaces de repositorio, casos de uso
core/network       -> Ktor Client, refresco de token real, ApiException, AuthApi/TaskApi,
                       WebSocketService, ProductionConfig (URL/pines de produccion)
core/database      -> Room KMP (offline-first), TaskEntity con pendingSync,
                       PendingDeletionEntity (tombstones de borrado sin red)
core/data          -> implementaciones de repositorio (SSOT), mapeos, Keychain/EncryptedPrefs
feature/login, register, calendar, tasks, streaks, settings -> UI + MVI por pantalla (Compose)
shared             -> navegación (4 pestañas + login/registro), arranque de Koin, iOS entry point
androidApp         -> app Android final
server             -> backend Ktor Server + Exposed + H2 + WebSocket de tiempo real
```

Paquete base: `com.daviddelgado.agenda`. `rootProject.name = "agenda"`.

Paleta (SVG de Figma en [design/figma-screens](design/figma-screens)): violeta `#8B5CF6`/`#A78BFA`
(primario), verde `#10B981` (rachas/éxito), rojo `#EF4444` (peligro), azul `#3B82F6`,
cian `#06B6D4` (splash), gris `#9CA3AF` (texto secundario).

Copia pre-migración (plantilla Android Studio + Hilt/Retrofit) en `..\agenda_backup_pre_kmp`.

## 3. Modelo de dominio (`core/domain/.../model/Models.kt`)

```kotlin
data class Task(
    val id: String,                 // lo genera el cliente con randomEntityId() (32 hex)
    val title: String,
    val description: String = "",
    val date: LocalDate,
    val time: LocalTime? = null,
    val durationMinutes: Int? = null,
    val category: TaskCategory = TaskCategory.OTRO,
    val priority: TaskPriority = TaskPriority.MEDIA,
    val reminderFrequency: ReminderFrequency = ReminderFrequency.NINGUNO,
    val increment: IncrementConfig? = null,   // null = no es incremental
    val isCompleted: Boolean = false,
)

enum class TaskCategory { TRABAJO, ESTUDIO, SALUD, PERSONAL, HOGAR, FINANZAS, OTRO }
enum class TaskPriority { ALTA, MEDIA, BAJA }
enum class ReminderFrequency { NINGUNO, UNA_VEZ, DIARIO, SEMANAL, MENSUAL, PERSONALIZADO }
enum class IncrementUnit { DIAS, SEMANAS, MESES }
data class IncrementConfig(val amount: Int, val everyValue: Int, val everyUnit: IncrementUnit)
```

`IncrementConfig` es una tarea "repetitiva": al **crear** (no al editar) una tarea con el
interruptor "Tarea incremental" activo, se generan de golpe `amount` copias adicionales de
la tarea, cada una `everyValue` `everyUnit` después de la anterior, repitiendo sus
características básicas (título, descripción, categoría, prioridad, hora, duración,
recordatorio) — ver `GenerateTaskRepetitionsUseCase` (`core/domain/.../usecase/UseCases.kt`,
puro, sin repositorio) y su uso en `TasksViewModel.saveTask()`. Las copias generadas llevan
`increment = null` (para no volver a generar copias de una copia) e `isCompleted = false`.
**Todos estos campos tienen ya selector en el formulario de tareas**
(`TasksScreen.TaskFormDialog`): categoría, prioridad y recordatorio con
`AgendaDropdownField`; hora como texto libre "HH:mm"; duración numérica; incremento con un
interruptor que revela "Número de repeticiones" / "Cada cuánto" / unidad.

**Si añades o cambias un campo de tarea, hay que tocarlo en estos cuatro sitios:**
1. `core/domain/.../model/Models.kt` (fuente de verdad conceptual)
2. `core/database/.../TaskEntity.kt` + `TaskDao.kt` (Room; enums como `.name` en texto)
3. `core/network/.../dto/TaskDtos.kt` + `core/data/.../task/TaskDtoMapper.kt` (contrato de red)
4. `server/.../dto/Dtos.kt` + `server/.../db/Tables.kt` + `server/.../repository/TaskRepository.kt`
   ...y también el formulario en `feature/tasks/.../TasksScreen.kt` + `TasksContract.kt` +
   `TasksViewModel.kt` si el campo debe ser editable desde la UI.

Borrado conjunto disponible en todos los niveles, **con selección múltiple real en la UI**
(pulsación larga sobre una tarea entra en modo selección; icono de "seleccionar todas" y
de papelera en la barra superior; `TasksScreen.SelectionTopBar`):
- Dominio: `TaskRepository.deleteTasks(ids)`, `.deleteAllTasks()`, `AuthRepository.deleteAccount()`
- Casos de uso: `DeleteTasksUseCase`, `DeleteAllTasksUseCase`, `DeleteAccountUseCase`
- Servidor: `POST /tasks/bulk-delete`, `DELETE /tasks`, `DELETE /users/me` (cascada real por `ON DELETE CASCADE`)

## 4. Backend (`server/`)

Stack: Ktor Server (Netty) + Exposed + H2 en fichero (cero configuración, sin Docker ni
Postgres) + JWT (`com.auth0:java-jwt`) + bcrypt (jbcrypt) + **WebSockets** (tiempo real).

Escucha en `http://localhost:8080` (o `$PORT`). La base se crea sola en
`server/data/agenda.mv.db`; `AGENDA_DB_URL` permite apuntar a otra (los tests usan H2 en
memoria). En producción hay que fijar `AGENDA_JWT_SECRET` (si no, usa un secreto de
desarrollo hardcodeado, ver `Application.kt`).

**Esquema (`server/.../db/Tables.kt`):**
- `users(id, name, email UNIQUE, password_hash, created_at)`
- `tasks(id, user_id -> users.id ON DELETE CASCADE, title, description, date, time, duration_minutes, category, priority, reminder_frequency, increment_amount, increment_every_value, increment_every_unit, is_completed, created_at, updated_at)`

**Endpoints:**
| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/auth/register` | no | `{name,email,password}` -> `AuthResponse` (201) |
| POST | `/auth/login` | no | `{email,password}` -> `AuthResponse` |
| POST | `/auth/refresh` | no | `{refreshToken}` -> par de tokens nuevo |
| POST | `/auth/forgot-password` | no | `{email}` -> 204 siempre (exista o no la cuenta, ver sección 7sexies). Genera un código de 6 dígitos y lo manda por email |
| POST | `/auth/reset-password` | no | `{email,code,newPassword}` -> 204, o 400 si el código es inválido/caducado/ya usado 5 veces |
| GET | `/users/me` | sí | `UserResponse` (lo usa el arranque para recuperar sesión) |
| DELETE | `/users/me` | sí | Borra cuenta + tareas en cascada (204) |
| GET | `/tasks` | sí | Todas las tareas del usuario |
| POST | `/tasks` | sí | Crea tarea (201). **Respeta el `id` del cliente y si ya existe la actualiza** |
| PUT | `/tasks/{id}` | sí | Reemplaza una tarea (404 si no es suya) |
| PATCH | `/tasks/{id}/toggle-completed` | sí | Alterna completada (204) |
| DELETE | `/tasks/{id}` | sí | Borra una tarea (204) |
| POST | `/tasks/bulk-delete` | sí | `{ids:[...]}` -> `{deleted:N}` |
| DELETE | `/tasks` | sí | Borra TODAS las del usuario -> `{deleted:N}` |
| WS | `/tasks/ws` | sí (Bearer en el handshake) | Avisa `"tasks_changed"` cuando cambian las tareas del usuario desde otra sesión/dispositivo |

Auth: `Authorization: Bearer <accessToken>`; access token 30 min, refresh 30 días, claim
`type` para que uno no sirva como el otro.

Que `POST /tasks` sea idempotente por `id` es lo que permite reintentar sin duplicar la
subida de una tarea creada sin red (ver sección 5).

**Tiempo real (`server/.../realtime/TaskEventBroadcaster.kt`):** registro en memoria de
sesiones WebSocket abiertas por `userId` (`ConcurrentHashMap` + `Mutex`); cada mutación de
tareas (`TaskRoutes.kt`) llama a `notifyTasksChanged(userId)` tras responder al cliente que
hizo la petición. El mensaje es solo la señal `"tasks_changed"`, nunca el estado completo:
quien lo recibe llama a su `syncTasks()` habitual. Verificado con un test real que abre el
socket, crea una tarea por HTTP desde otro cliente y comprueba que llega el aviso
(`TaskRealtimeTest.kt`).

## 5. Cómo está conectado el cliente con el servidor

**URL base por plataforma** (`core/data/.../di/DataModule.android.kt` / `.ios.kt`):
- Android debug: `http://10.0.2.2:8080/` — 10.0.2.2 es como el emulador ve el `localhost`
  del PC. **Android release** usa `ProductionConfig.BASE_URL` (ver sección 8).
- iOS: `http://localhost:8080/` (el simulador comparte red con el Mac) — sin distinción
  debug/release todavía, ver el TODO en `DataModule.ios.kt` (sección 8).
- Móvil físico: pon la IP del PC en la red local (`http://192.168.x.y:8080/`) en la
  constante `ANDROID_EMULATOR_BASE_URL`.

Android solo permite HTTP en claro para esas direcciones de desarrollo, por
[network_security_config.xml](androidApp/src/main/res/xml/network_security_config.xml).

**Offline-first de verdad** (`core/data/.../task/TaskRepositoryImpl.kt`): Room es la única
fuente de verdad; la UI nunca espera a la red.
- Toda escritura va primero a Room con `pendingSync = true` y después se empuja al servidor;
  si el empuje funciona, se marca `pendingSync = false`.
- **Los borrados sin red dejan un tombstone** en la tabla `pending_deletions`
  (`PendingDeletionEntity`/`PendingDeletionDao`): antes, una tarea borrada offline podía
  reaparecer en el siguiente `syncTasks()` porque el `GET /tasks` del servidor seguía
  devolviéndola; ahora se reintenta el borrado remoto en cada sincronización hasta que se
  confirme, sin que la tarea vuelva a Room mientras tanto. `AuthRepositoryImpl.logout()` y
  `.deleteAccount()` limpian también esta tabla.
- `syncTasks()` (caso de uso `SyncTasksUseCase`): (1) reintenta los borrados pendientes,
  (2) sube lo pendiente de crear/editar, (3) baja `GET /tasks` a Room, (4) borra de local
  lo que el servidor ya no tiene, sin tocar lo pendiente de subir.
- Se llama al abrir la pantalla de tareas (silencioso si falla), con el botón de refrescar
  (ahí sí avisa por snackbar) y **automáticamente cuando llega un aviso por WebSocket**
  (`ObserveTaskChangesUseCase`, ver sección 4).

**Sesión y tokens**:
- `AuthRepositoryImpl` usa `AuthApi` y guarda el par de tokens en almacenamiento cifrado
  (`EncryptedSharedPreferences` / Keychain) vía `TokenProvider`.
- El refresco automático está conectado de verdad: ante un 401, el plugin `Auth` de Ktor
  llama a `POST /auth/refresh`, guarda el par nuevo y reintenta la petición; si el refresco
  también falla, limpia los tokens (`core/network/.../AgendaHttpClientConfig.kt`).
- Tras login/registro/logout se llama a `AuthApi.forgetCachedTokens()` para que el plugin
  Bearer no siga usando el token cacheado.
- El splash intenta `restoreSession()` (`GET /users/me`): si el token guardado sigue siendo
  válido entra directo a Home, si no va al login. **Ajustes > Cerrar sesión / Borrar cuenta**
  (`feature/settings`) hacen el camino inverso: vuelven al login.

**Errores legibles**: el cliente usa `expectSuccess = true` y `apiCall {}` traduce cualquier
4xx/5xx a `ApiException(statusCode, message)` con el `message` que manda el servidor.

## 6. Tests (245 automáticos, todos en verde)

`./gradlew check` ejecuta los 226 unitarios (+ktlint+detekt+lint). Los 19 instrumentados de
Compose necesitan el emulador arrancado (ver sección 1).

| Módulo | Tests | Qué cubre |
|---|---|---|
| `:core:common` | 2 | `AgendaLogger` (envoltorio de Napier usado en los fallos silenciosos de sync y de registro del token FCM) |
| `:core:domain` | 31 | casos de uso con Fake Repositories, defaults del modelo, borrado conjunto, `GenerateTaskRepetitionsUseCase` (fechas, ids, campos copiados, casos sin incremento), `RegisterFcmTokenUseCase`, **`RequestPasswordResetUseCase` y `ResetPasswordUseCase`** |
| `:core:data` | 49 | mapeos, repositorio offline-first **con tombstones**, login/sesión, refresco de token, **periodo de gracia de rachas** |
| `:feature:login` | 9 unit + 7 UI | reductor MVI (incluye **registrar el token FCM tras un login correcto, y no llamar al servidor si no hay token disponible**) + **Compose: campos, error, login OK/fallido, navegación, y `pulsarOlvidasteTuContrasenaNavegaAlFormularioDeRecuperacion`** |
| `:feature:register` | 5 | reductor MVI de registro |
| `:feature:tasks` | 26 unit + 8 UI | reductor MVI (formulario completo, selección múltiple, **crear tarea incremental genera sus copias, editar una existente no las regenera**, **buscador por título y filtro por categoría, `SelectAll` respeta el filtro activo**) + **Compose: estado vacío, crear tarea, validación, fecha inicial desde el calendario, el formulario es deslizable al activar "incremental", escribir en el buscador oculta las tareas que no coinciden** |
| `:feature:calendar` | 7 unit + 4 UI | reductor MVI de calendario (incluye conteo de tareas por día) + `CalendarLayoutTest` (tamaño de celda cuadrado dinámico, función pura) + **Compose: la cuadrícula no superpone días, tocar un día concreto selecciona ese día y no otro, un día con tareas muestra cuántas tiene, los botones de navegación de mes tienen descripción accesible** |
| `:feature:streaks` | 3 | reductor MVI de rachas |
| `:feature:settings` | 6 | logout, borrar cuenta (éxito y fallo del servidor) |
| `:feature:passwordreset` | 9 | **nuevo módulo**: reductor MVI de `ForgotPasswordViewModel` (4: guarda el email escrito, no llama al servidor con email vacío, éxito dispara `CodeSent`, error del servidor se muestra) y de `ResetPasswordViewModel` (5: código que no tiene 6 dígitos no llama al servidor, contraseñas que no coinciden no resetean, éxito dispara `PasswordReset`, código inválido del servidor se muestra, el email llega fijo por parámetro sin formulario propio) |
| `:shared` | 7 | `HomeNavigator` (4: qué pestaña se ve y qué fecha queda pendiente al abrir un día del calendario) + `SplashSessionHandler` (3: **registra el token FCM también al recuperar sesión en el splash**) |
| `:server` | 72 | API completa (incluida `/tasks/ws`), JWT y bcrypt, validación de tareas y de email, rate limiting de `/auth`, `ReminderScheduler` (lógica pura de cuándo toca un recordatorio, 10), `ReminderJob` (bucle en segundo plano, 4), `PushSender` (Firebase, 2), `FcmTokenRoutes` (3), **`PasswordResetRoutesTest` (9: pedir código con email existente/inexistente siempre responde 204, resetear con código correcto permite loguearse con la contraseña nueva, código incorrecto/caducado/agotado tras 5 intentos se rechaza, pedir código dos veces invalida el primero, resetear invalida el refresh token anterior, contraseña nueva de menos de 6 caracteres se rechaza), `EmailSenderTest` (5: `SmtpEmailSender` con `runCatching` ante un host inalcanzable, `NoOpEmailSender`, `provideEmailSender` con credenciales completas/incompletas), `JwtConfigTest` ampliado con el claim `tv` de `token_version`, `AuthRoutesTest` ampliado con `/auth/refresh` rechazando un `token_version` desactualizado** |

Comandos sueltos: `./gradlew :core:data:testDebugUnitTest`,
`./gradlew :feature:login:connectedDebugAndroidTest :feature:tasks:connectedDebugAndroidTest :feature:calendar:connectedDebugAndroidTest`
(instalan un APK de test en el emulador; tarda ~1-2 min la primera vez).

Cómo están montados (para escribir más igual):
- **Unitarios**: `commonTest` con `kotlin("test")` + `kotlinx-coroutines-test`. Los
  ViewModel necesitan `Dispatchers.setMain(UnconfinedTestDispatcher())` en `@BeforeTest`, y
  los efectos se recogen con `viewModel.effect.onEach { }.launchIn(backgroundScope)` antes
  de lanzar la intención. La red se simula con `MockEngine` instalando los plugins reales
  de la app (`core/data/.../fake/MockHttpClient.kt`). `assertEquals(null, x)` /
  `assertEquals(emptyList(), x)` no compilan por inferencia de tipos en KMP: usa
  `assertNull` / `assertTrue(x.isEmpty())` o `listOf<Tipo>(...)` explícito.
- **Instrumentados de Compose** (`src/androidInstrumentedTest`, en `feature:login`,
  `feature:tasks` y `feature:calendar`): `createAndroidComposeRule<ComponentActivity>()`, montan la pantalla real
  pasando un ViewModel real + un Fake Repository propio del fichero de test (no comparten
  código con `commonTest`: son un source set KMP distinto que no lo hereda por defecto).
  Localizan campos por su `label` (`onNodeWithText("Email")`) porque Material3 fusiona la
  semántica del label dentro del nodo del `OutlinedTextField`. Dependencias: `ui-test-junit4`
  / `ui-test-manifest` **fijadas a la versión exacta de Compose UI que resuelve Compose
  Multiplatform 1.6.11 para Android (1.6.7, verificado en el cache de Gradle)** — mezclarlas
  con un BOM de Jetpack Compose más reciente duplica clases en el classpath del test y falla
  la resolución de dependencias.
- El servidor se prueba entero con `withApi { }` (`server/src/test/.../api/TestApi.kt`), que
  levanta la app real sobre una H2 en memoria distinta en cada test; el cliente de test
  instala también el plugin `WebSockets` para poder probar `/tasks/ws`.

## 7. Verificado en caliente el 2026-09-13 (no solo compilado)

- `./gradlew check` -> BUILD SUCCESSFUL (144 tests unitarios + ktlint + detekt + lint).
- `./gradlew :feature:login:connectedDebugAndroidTest :feature:tasks:connectedDebugAndroidTest`
  -> 11/11 tests de Compose UI pasan en el emulador Pixel_6a real.
- `./gradlew :androidApp:assembleRelease` -> BUILD SUCCESSFUL (con minificación R8; hubo que
  añadir reglas ProGuard para tink/slf4j, ver `androidApp/proguard-rules.pro`).
- Emulador Pixel_6a + `:server:run`, flujo completo desde la UI en esta sesión:
  - Sesión recuperada sola al abrir la app (`restoreSession`).
  - Formulario de nueva tarea con todos los campos: los desplegables de categoría,
    prioridad y recordatorio abren y muestran las 7/3/6 opciones correctamente.
  - Pestaña **Ajustes** nueva: muestra usuario, "Cerrar sesión" navega al login de verdad.
  - **Bug real encontrado y arreglado en vivo**: la cuadrícula de los últimos 28 días de
    "Rachas" (`StreaksScreen.LastDaysGrid`) no tenía `Modifier.weight(1f)` en los `Box` de
    cada semana, así que los 7 días se superponían ocupando cada uno el ancho completo de
    la fila en vez de dividirse en columnas. Arreglado y confirmado visualmente.
- `git log` -> repo inicializado con `git init -b main` y un primer commit con los 167
  ficheros del proyecto (sin `build/`, `local.properties`, `server/data/` ni `.atl/`).

## 7bis. Verificado en caliente el 2026-09-14 (atajo calendario -> editar tarea, con TDD)

- Hecho con TDD real: cada pieza (test en rojo -> código mínimo -> test en verde) antes de
  tocar produccion, incluido el bug de layout que se describe abajo.
- **`HomeNavigator`** (`shared/.../HomeNavigator.kt`, nuevo, con 4 tests en
  `shared/src/commonTest`): decide qué pestaña de Home se ve y, si se llega desde el
  calendario, qué fecha debe preseleccionar la pantalla de tareas. `TasksScreen` gana los
  parámetros opcionales `initialDate`/`onDateConsumed` (cubiertos por un test instrumentado
  nuevo en `feature:tasks`) para consumir esa fecha con `TasksIntent.SelectDate` una sola
  vez. `App.kt` conecta `CalendarScreen(onOpenDay = ...)` con `TasksScreen(initialDate = ...)`
  a través de esto.
- **Bug real encontrado (con test que lo demuestra) y arreglado en vivo**: la cuadrícula de
  `CalendarScreen.MonthGrid` tenía el mismo problema que ya se dio una vez en
  `StreaksScreen` (sección 7) — los `Box` de cada día no tenían `Modifier.weight(1f)`, así
  que los 7 días de cada semana se superponían. No era solo estético: un test instrumentado
  nuevo (`feature:calendar`, que ahora tiene infraestructura de tests de Compose igual que
  `feature:login`/`feature:tasks`) demostró que **tocar el día 5 seleccionaba en realidad el
  día 6** por el solapamiento. Arreglado con el mismo `Modifier.weight(1f)`; confirmado con
  los tests y visualmente en el emulador (la cuadrícula ya se ve en 7 columnas reales).
- Flujo end-to-end probado a mano en el emulador Pixel_6a contra el servidor real: crear una
  tarea desde el día 20 del calendario, confirmar que **no** aparece al ver "hoy" (día 14),
  volver al día 20 desde el calendario y comprobar que la tarea aparece y que tocarla abre
  el formulario en modo "Editar tarea" con sus datos. Tarea de prueba borrada al terminar
  para no dejar basura en la cuenta `e2e@test.com`.
- `./gradlew check` -> BUILD SUCCESSFUL (148 unitarios + ktlint + detekt + lint).
- `./gradlew :feature:login:connectedDebugAndroidTest :feature:tasks:connectedDebugAndroidTest :feature:calendar:connectedDebugAndroidTest`
  -> 14/14 tests de Compose UI en verde en el emulador Pixel_6a real.
- Pendiente de decisión del usuario: commitear estos cambios (working tree tenía cambios sin
  commitear al terminar la sesión).

## 7ter. Verificado en caliente el 2026-09-14 (calendario cuadrado + formulario deslizable)

Continuación de la misma sesión 5, tras pedir tres retoques concretos.

- **Calendario con celdas cuadradas y dinámicas** (`feature/calendar/.../CalendarLayout.kt`,
  nuevo, con `CalendarLayoutTest` con TDD): `squareCellSizeDp(maxWidth, maxHeight, columns,
  rows)` calcula el lado de la celda a partir del espacio realmente disponible (no un valor
  fijo), usando `BoxWithConstraints` en `CalendarScreen`. Como el ancho (7 columnas) suele
  ser la dimensión mas estrecha en un telefono en vertical, `Arrangement.SpaceEvenly` reparte
  el hueco sobrante entre semanas para que la cuadrícula aproveche toda la pantalla en vez de
  amontonarse arriba con medio hueco en blanco debajo — confirmado visualmente en el emulador
  antes/después.
- **Contador de tareas por día**: `CalendarState.datesWithTasks: Set<LocalDate>` pasó a
  `taskCountsByDate: Map<LocalDate, Int>` (`tasks.groupingBy { it.date }.eachCount()`); cada
  celda muestra `"($n)"` bajo el número del día cuando `n > 0`. Cubierto con TDD tanto a
  nivel de `CalendarViewModel` como con un test instrumentado nuevo.
- **Bug real de layout en `TasksScreen.TaskFormDialog`, arreglado con TDD**: al activar
  "Tarea incremental" el formulario no tenía ningún contenedor deslizable; el contenido que
  no cabía en el alto máximo real del `AlertDialog` se recortaba en el borde y el trozo
  cortado se veía como una caja superpuesta con el campo anterior (reproducido a mano y
  confirmado con capturas antes del fix). Arreglado envolviendo los campos en un
  `Column(Modifier.verticalScroll(...))` — **sin fijar un alto máximo a mano**: se probó
  primero con `heightIn(max = 450.dp)` y rompía un test existente porque ni siquiera el
  formulario corto (sin incremental) cabía sin desplazarse: se quitó, dejando que el propio
  límite real del `AlertDialog` decida cuándo hace falta deslizar. Confirmado en el emulador:
  el formulario corto ya no necesita scroll y el largo (con incremental) se desliza limpio
  hasta "Unidad de la cadencia" sin ningún solape.
  - Nota sobre las pruebas automáticas de este último punto: `performScrollTo()` y
    `assertIsDisplayed()` de Compose UI Test 1.6.7 resultaron poco fiables contra contenido
    dentro de un `AlertDialog` (encuentran o no encuentran nodos de forma inconsistente según
    el camino de resolución, aunque el comportamiento real en el dispositivo es correcto). El
    test final (`activarIncrementalHaceQueElFormularioSeaDeslizable`) comprueba en su lugar,
    de forma determinista, que el `Column` (con `Modifier.testTag("taskFormScroll")`) expone
    `SemanticsActions.ScrollBy` — RED/GREEN verificado quitando y devolviendo el fix.
- `./gradlew check` -> BUILD SUCCESSFUL (151 unitarios + ktlint + detekt + lint).
- `./gradlew :feature:login:connectedDebugAndroidTest :feature:tasks:connectedDebugAndroidTest :feature:calendar:connectedDebugAndroidTest`
  -> 16/16 tests de Compose UI en verde en el emulador Pixel_6a real.

## 7quater. Verificado en caliente el 2026-09-14 (tarea incremental genera copias reales)

Continuación de la misma sesión 5, tras pedir que "incremental" haga algo de verdad.

- **`IncrementUnit` pierde `REPETICIONES`**: ya no tenía sentido como unidad de un intervalo
  de fechas (solo quedan `DIAS`/`SEMANAS`/`MESES`). Tocó los cuatro sitios documentados en la
  sección 3 salvo el servidor, que guarda la unidad como texto libre sin validarla.
- **`GenerateTaskRepetitionsUseCase`** (nuevo, `core/domain`, puro — con TDD, 7 tests):
  recibe la tarea recién creada y devuelve `amount` copias, cada una fechada
  `everyValue`×índice `everyUnit` después de la original, con id nuevo, sin completar y sin
  su propia configuración de incremento.
- **`TasksViewModel.saveTask()`**: tras guardar una tarea **nueva** (no al editar) con
  incremento, sube cada copia con el mismo `UpsertTaskUseCase` de siempre. Cubierto con TDD
  a nivel de ViewModel (genera las copias con las fechas/campos correctos; editar una tarea
  incremental existente no regenera nada).
- Probado a mano en el emulador contra el servidor real: tarea "Flexiones" (categoría HOGAR)
  con 3 repeticiones cada 7 días -> aparecieron 4 tareas en el calendario (14, 21, 28 de
  septiembre y 5 de octubre, todas con 1 tarea ese día); abrir la copia del día 21 confirmó
  mismo título/categoría/prioridad y **"Tarea incremental" desactivado** (no genera copias de
  copias). Las 4 tareas de prueba se borraron al terminar para no dejar basura en
  `e2e@test.com`.
- `./gradlew check` -> BUILD SUCCESSFUL (160 unitarios + ktlint + detekt + lint, incluida la
  subida de `LongParameterList.constructorThreshold` a 10 en `config/detekt/detekt.yml`
  porque `TasksViewModel` pasó a recibir 9 casos de uso).
- `./gradlew :feature:login:connectedDebugAndroidTest :feature:tasks:connectedDebugAndroidTest :feature:calendar:connectedDebugAndroidTest`
  -> 16/16 en verde (sin cambios en la UI instrumentada, la generación de copias no se probó
  por Compose UI test, solo a nivel de `TasksViewModel` + a mano en el emulador).

## 7quinquies. Verificado en caliente el 2026-09-16 (cierre del plan: logging, seguridad, recordatorios push, buscador)

Task 12 (cierre) del plan `calidad-seguridad-recordatorios`: primera vez que se verifica
todo el trabajo de las Tasks 1-11 junto, no tarea a tarea como hasta ahora.

- **Logging centralizado con Napier** (`core/common/.../logging/AgendaLogger.kt`, con Napier
  de verdad detrás): los fallos silenciosos de sincronización (`core/data`) y de registro
  del token FCM (`feature/login`, `AgendaFirebaseMessagingService`) quedan en logcat con
  nivel y excepción en vez de perderse en un `catch {}` mudo. 2 tests en `AgendaLoggerTest`.
- **Accesibilidad del calendario**: los botones de navegación de mes de `CalendarScreen`
  tienen `contentDescription`. Confirmado con el test instrumentado
  `losBotonesDeNavegacionDelMesTienenDescripcionAccesible`, corrido de verdad en el
  emulador esta sesión — nunca se había ejecutado en un dispositivo real hasta ahora.
- **CI en GitHub Actions** (`.github/workflows/ci.yml`): job `check` en `ubuntu-latest`,
  JDK 17, cache de `~/.gradle`, corre `./gradlew check --no-daemon` en cada push a `main` y
  en cada PR. No se ha podido verificar la ejecución real en GitHub desde este entorno (no
  hay push a un remoto), pero el mismo comando se ha corrido en local esta sesión (ver
  abajo).
- **Validación del servidor**: `TaskRoutes.kt` (`respondIfInvalid`, extraída aparte para no
  romper el límite de detekt `LongMethod`) valida título/fecha/hora antes de guardar una
  tarea y responde `400 Bad Request` con el motivo. `AuthRoutes.kt` valida el email con
  `emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")` (antes solo comprobaba que no
  estuviera vacío) además de la longitud mínima de contraseña.
- **Rate limiting** (`Application.kt`, plugin `RateLimit` de Ktor): 10 peticiones/60s en
  `/auth/login` y `/auth/register`, con la clave del cubo por
  `call.request.origin.remoteHost` (no un cubo global) para que un cliente agotando su
  cupo no bloquee al resto.
- **Recordatorios push reales**: `ReminderScheduler` (`server/.../reminder/`) es lógica pura
  (10 tests) que decide si toca mandar el recordatorio de una tarea ahora mismo.
  `ReminderJob.runReminderLoop` (4 tests) usa ese scheduler en un bucle en segundo plano
  arrancado solo desde `main()` (nunca en los tests) con `GlobalScope.launch` sobre
  `Dispatchers.IO`. El envío real usa `FirebasePushSender` (Firebase Admin SDK,
  `server/.../push/PushSender.kt`, 2 tests) si `AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON`
  apunta a un JSON de cuenta de servicio válido; si no, cae a `NoOpPushSender` (avisa una
  vez por log y la app sigue funcionando sin push de verdad) — el mismo patrón de
  placeholder que `ProductionConfig` (sección 8). El endpoint `POST /fcm-token`
  (`FcmTokenRoutesTest`, 3 tests) registra el token de cada dispositivo.
- **Cliente Android recibe los push y registra su token FCM**:
  `AgendaFirebaseMessagingService` (androidApp) registra el token nuevo en `onNewToken` y
  muestra la notificación en `onMessageReceived`; el registro también se dispara tras un
  login correcto (`LoginViewModel`, 2 tests: token registrado cuando hay uno disponible, y
  no se llama al servidor si no lo hay) y al recuperar sesión desde el splash
  (`SplashSessionHandlerTest`, 3 tests). Los fallos al registrar el token quedan en log.
  - **Bugs reales encontrados y arreglados en esta sesión de cierre** (no en las Tasks 9/10
    originales: sus revisiones no habían corrido el `./gradlew check` completo de
    `androidApp`/`server`, solo compilación y tests unitarios sueltos):
    - detekt `InjectDispatcher`: `CoroutineScope(Dispatchers.IO)` fijo en
      `AgendaFirebaseMessagingService` y `GlobalScope.launch(Dispatchers.IO)` fijo en
      `Application.startReminderLoop`. Arreglado pasando el dispatcher como parámetro con
      valor por defecto (`Dispatchers.IO`) en los dos sitios: mismo comportamiento en
      producción, permite inyectar uno de test.
    - detekt `HasPlatformType`: los loggers de `PushSender.kt` (2) y `ReminderJob.kt` (1)
      declarados como `LoggerFactory.getLogger(...)` sin tipo explícito — arreglado
      añadiendo `: Logger` a los tres.
    - detekt `UseOrEmpty`: `?: ""` en `AgendaFirebaseMessagingService` reemplazado por
      `.orEmpty()`.
    - Android Lint `MissingPermission`: `NotificationManagerCompat.from(this).notify(...)`
      en `onMessageReceived` no comprobaba el permiso `POST_NOTIFICATIONS` (permiso
      "dangerous" desde API 33) antes de llamarlo; Lint lo marca error porque `notify()`
      puede lanzar `SecurityException` si el usuario lo deniega en tiempo de ejecución.
      Arreglado con un check inline de `ContextCompat.checkSelfPermission(...)` antes de
      `notify()` (tiene que ser inline y no en una función aparte, porque el análisis de
      flujo de datos de Lint no sigue el check a través de una llamada a otro método); sin
      permiso, el recordatorio se descarta con un aviso en log en vez de mostrarse o
      crashear.
    - **Gap real encontrado, no arreglado esta sesión** (construir el flujo de petición de
      permiso es una decisión de UX/producto, no una verificación): el manifest declara
      `POST_NOTIFICATIONS` pero la app nunca lo pide en tiempo de ejecución (no hay ningún
      `ActivityResultContracts.RequestPermission` en el código). En Android 13+ el permiso
      empieza denegado y se queda así hasta que el usuario lo conceda a mano desde Ajustes,
      así que los recordatorios push no se van a ver en la práctica en esas versiones sin
      ese flujo. Añadido a la sección 10.
- **Buscador y filtro de tareas** (`feature/tasks`): campo de texto que filtra por título y
  selector de categoría que filtra la lista visible; `SelectAll` respeta el filtro activo
  (no selecciona tareas ocultas por él). Cubierto con TDD a nivel de `TasksViewModel` (4
  tests) y con el test instrumentado `escribirEnElBuscadorOcultaLasTareasQueNoCoinciden`,
  corrido de verdad en el emulador por primera vez esta sesión — en verde.

Verificación de esta sesión:

- `./gradlew check` -> **FAILED** en el primer intento: 2 issues de detekt en `androidApp`
  (`InjectDispatcher`, `UseOrEmpty`, ambos en `AgendaFirebaseMessagingService`). Arreglados,
  se repitió -> **FAILED** de nuevo: Android Lint `MissingPermission` en el mismo fichero.
  Arreglado, se repitió -> **FAILED** una tercera vez: 2 issues de detekt más en `server`
  (`InjectDispatcher` en `Application.kt`, `HasPlatformType` en `PushSender.kt` y
  `ReminderJob.kt`, este último no lo había reportado el intento anterior). Arreglados
  todos, cuarto intento -> **BUILD SUCCESSFUL**.
- `./gradlew check` (verde) -> **196 tests unitarios** (antes 160) + ktlint + detekt + lint,
  todos los módulos, incluidos `:core:common` (2, `AgendaLoggerTest`, nuevo) y `:server`
  (54, antes 30: +24 de `ReminderSchedulerTest` (10), `ReminderJobTest` (4),
  `PushSenderTest` (2), `FcmTokenRoutesTest` (3) y la validación/rate limiting ya cubiertas
  dentro de `AuthRoutesTest`/`TaskRoutesTest`).
- Emulador Pixel_6a (`emulator-5554`, ya arrancado, confirmado con `adb devices` antes de
  empezar) + `./gradlew :feature:login:connectedDebugAndroidTest :feature:tasks:connectedDebugAndroidTest :feature:calendar:connectedDebugAndroidTest`
  -> **18/18 en verde** (antes 16): login 6/6, calendar 4/4 (incluida
  `losBotonesDeNavegacionDelMesTienenDescripcionAccesible`, Task 2, primera vez en un
  dispositivo real), tasks 8/8 (incluida `escribirEnElBuscadorOcultaLasTareasQueNoCoinciden`,
  Task 11, primera vez en un dispositivo real). 0 fallos, 0 saltados.
- Placeholders pendientes de credenciales reales, igual que `ProductionConfig` (sección 8):
  `AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON` (variable de entorno del servidor; sin ella cae a
  `NoOpPushSender`) y `androidApp/google-services.json` (contiene valores de relleno
  explícitos como `"PLACEHOLDER_API_KEY_SUSTITUIR_CON_EL_REAL_DE_FIREBASE"` y
  `"project_id": "agenda-placeholder"`). Ninguno de los dos se ha rellenado con credenciales
  reales en esta sesión, así que los recordatorios push no se han podido probar de extremo
  a extremo contra un dispositivo real recibiendo una notificación real — solo la lógica
  (`ReminderScheduler`, `ReminderJob`, `PushSender`) con tests y compilación.

## 7sexies. Verificado en caliente el 2026-09-16 (recuperación de contraseña por email)

Task 8 (cierre) del plan `recuperacion-password`: primera vez que se verifica todo el trabajo
de las Tasks 1-7 junto, no tarea a tarea como hasta ahora.

- **Envío de correo con SMTP real + respaldo sin efecto** (`server/.../email/EmailSender.kt`):
  `SmtpEmailSender` (Jakarta Mail/Angus Mail) manda el correo real si hay las cinco credenciales
  SMTP completas; `provideEmailSender` cae a `NoOpEmailSender` si falta cualquiera de las
  variables de entorno `AGENDA_SMTP_HOST`/`AGENDA_SMTP_PORT`/`AGENDA_SMTP_USERNAME`/
  `AGENDA_SMTP_PASSWORD`/`AGENDA_SMTP_FROM` — mismo patrón de placeholder pendiente de
  credenciales reales que `ProductionConfig` (sección 8) y `AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON`
  (sección 7quinquies). `NoOpEmailSender` deja el cuerpo completo del correo (incluido el
  código) en el log del servidor a nivel WARN, para poder probar el flujo entero en desarrollo
  sin credenciales reales — así se ha probado a mano en esta sesión (ver más abajo).
- **Invalidación de sesión por `token_version`** (`Users.token_version`,
  `UserRepository.updatePassword`): cambiar la contraseña sube el `token_version` del usuario
  en 1; el claim `tv` viaja en el access y el refresh token (`JwtConfig`), y `handleRefresh` en
  `AuthRoutes.kt` compara el `tv` del refresh token contra el de la base de datos,
  rechazándolo con 401 si no coincide. **Matiz importante para no sobrevalorar el mecanismo**:
  esto invalida los *refresh tokens* emitidos antes del cambio, no los *access tokens* ya
  emitidos, que (con su expiración de 30 minutos, sección 4) siguen siendo válidos hasta que
  caduquen por sí solos — `auth-jwt` (`Application.kt`) valida solo la firma/caducidad del JWT,
  no consulta el `token_version` en cada petición. Un access token robado sigue teniendo hasta
  30 minutos de ventana tras el cambio de contraseña; cerrar esa ventana necesitaría comprobar
  `token_version` en cada petición autenticada (una consulta extra a base de datos por
  request), fuera del alcance de este plan.
- **Dos rutas nuevas** (`server/.../routes/AuthRoutes.kt`, dentro del mismo `rateLimit("auth")`
  que `/auth/login`/`/auth/register`, ver secciones 4 y 10 punto 10): `POST /auth/forgot-password`
  responde 204 **siempre**, exista o no la cuenta con ese email, para no filtrar qué emails
  están registrados; el envío real del correo se lanza en `GlobalScope.launch(dispatcher)`
  (dispatcher inyectable con valor por defecto `Dispatchers.IO`, sin esperarse dentro de la
  petición) precisamente para que la rama "email no existe" (que solo hace una consulta rápida)
  no tarde perceptiblemente menos que la rama "email existe y manda el correo" — si no, la
  diferencia de latencia sería un canal lateral por temporización que permitiría enumerar
  cuentas registradas. `POST /auth/reset-password` valida el código (comparando su hash
  SHA-256, nunca el código en claro), su caducidad (15 minutos) y un tope de 5 intentos
  fallidos antes de invalidarlo.
- **Cliente**: `AuthApi`/`AuthDtos` (`core/network`) y `AuthRepositoryImpl.requestPasswordReset`/
  `.resetPassword` (`core/data`) + `RequestPasswordResetUseCase`/`ResetPasswordUseCase`
  (`core/domain`, 1 test cada uno). **Nuevo módulo Gradle `feature/passwordreset`** (con su
  propio `commonTest`, ver sección 6) con dos pantallas MVI: `ForgotPasswordScreen`
  (email -> código) y `ResetPasswordScreen` (código + contraseña nueva -> confirmación),
  conectadas en `shared/.../App.kt` (`AppScreen.ForgotPassword`/`.ResetPassword`).
- **Enlace desde el login**: `LoginScreen` gana el texto "Olvidaste tu contrasena?" bajo
  "Crear una cuenta"; pulsarlo navega a `ForgotPasswordScreen`. Cubierto por el test
  instrumentado nuevo `pulsarOlvidasteTuContrasenaNavegaAlFormularioDeRecuperacion`
  (`feature:login`), corrido de verdad en el emulador por primera vez esta sesión.

Verificación de esta sesión:

- `./gradlew check` -> **FAILED** en el primer intento: `:server:detektMain` con 6 issues
  ponderados en código de la Task 1 de este plan (el commit con el informe de éxito fabricado
  que el brief de esta Task 8 avisó de antemano) — `GlobalScope.launch(Dispatchers.IO)` fijo en
  `AuthRoutes.handleForgotPassword` (detekt `InjectDispatcher`) y
  `SmtpEmailSender(host!!, port!!, username!!, password!!, from!!)` con cinco `!!` seguidos en
  `EmailSender.provideEmailSender` (detekt `UnsafeCallOnNullableType`). Arreglado:
  `handleForgotPassword` gana un parámetro `dispatcher: CoroutineDispatcher = Dispatchers.IO`
  (mismo patrón que `startReminderLoop` en `Application.kt`, sección 7quinquies) en vez de
  `Dispatchers.IO` fijo en el cuerpo; y `provideEmailSender` se reestructura en dos funciones
  con como mucho tres condiciones `&&` por `if` (para no chocar con detekt `ComplexCondition`)
  que dejan que el compilador haga smart-cast real de las variables, sin ningún `!!`. Repetido
  -> **BUILD SUCCESSFUL**.
- `./gradlew check` (verde) -> **226 tests unitarios** (antes 196) + ktlint + detekt + lint,
  todos los módulos, incluido el nuevo `:feature:passwordreset` (9) y `:server` con 18 tests
  más (72, antes 54: ver sección 6 para el detalle).
- `./gradlew :server:test` -> confirmado sin regresión de comportamiento tras el arreglo de
  detekt (mismos 72 tests, todos en verde).
- Emulador Pixel_6a (`emulator-5554`, confirmado con `adb devices` antes de empezar) +
  `./gradlew :feature:login:connectedDebugAndroidTest :feature:tasks:connectedDebugAndroidTest :feature:calendar:connectedDebugAndroidTest`
  -> **19/19 en verde** (antes 18): login 7/7 (incluida
  `pulsarOlvidasteTuContrasenaNavegaAlFormularioDeRecuperacion`, primera vez en un dispositivo
  real), tasks 8/8, calendar 4/4. 0 fallos, 0 saltados.
- Flujo completo probado a mano en el emulador Pixel_6a contra el servidor real (sin
  credenciales SMTP configuradas, así que el "correo" solo aparece en el log del servidor vía
  `NoOpEmailSender`): cuenta `e2e@test.com`/`secreta123` registrada de nuevo desde la app (la
  base de datos del servidor no la tenía todavía en esta sesión) -> cerrar sesión -> pulsar
  "Olvidaste tu contrasena?" desde el login -> pedir el código para `e2e@test.com` -> leído el
  código de 6 dígitos en el log del servidor (`745699`, WARN de `NoOpEmailSender`) -> escrito
  junto a una contraseña nueva en `ResetPasswordScreen` -> `POST /auth/reset-password` -> 204
  No Content -> vuelta automática al login con el aviso "Contrasena actualizada, inicia
  sesion" -> login con la contraseña nueva -> `POST /auth/login` -> 200 OK, sesión abierta
  normalmente (`GET /tasks` -> 200 OK). Capturas de pantalla tomadas en cada paso con
  `adb shell screencap`.
  - Nota de esta sesión: el emulador se cayó a mitad de la verificación manual (el proceso del
    emulador desapareció sin más) y al reiniciarlo cargó el snapshot `default_boot`, que
    revirtió el disco a un estado anterior sin el APK recién instalado — se detectó porque el
    enlace "Olvidaste tu contrasena?" había desaparecido del login, y se resolvió con un
    segundo `./gradlew :androidApp:installDebug` sobre el emulador ya arrancado. El servidor
    (proceso aparte) no se vio afectado y mantuvo la cuenta y el código generados.
- Placeholders pendientes de credenciales reales, igual que `ProductionConfig` (sección 8) y
  `AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON` (sección 7quinquies): `AGENDA_SMTP_HOST`,
  `AGENDA_SMTP_PORT`, `AGENDA_SMTP_USERNAME`, `AGENDA_SMTP_PASSWORD` y `AGENDA_SMTP_FROM`
  (variables de entorno del servidor; sin las cinco completas cae a `NoOpEmailSender`). No se
  han rellenado con credenciales reales en esta sesión, así que el envío real por SMTP
  (`SmtpEmailSender`) no se ha probado contra un servidor SMTP de verdad — solo la lógica con
  tests (`EmailSenderTest`) y el flujo completo con `NoOpEmailSender` en desarrollo.

## 8. Producción: URL y certificate pinning (infraestructura lista, sin dominio real)

No hay todavía un backend desplegado en un dominio real, así que no hay pines de
certificado de verdad que fijar. Lo que sí está listo:

- `core/network/.../ProductionConfig.kt`: `BASE_URL` y `CERTIFICATE_PINS_SHA256` como
  placeholders documentados, con el comando `openssl` exacto para sacar el pin SHA-256 de
  un certificado real cuando exista, y la recomendación de incluir un pin de respaldo.
- **Android**: `core/data`'s `DataModule.android.kt` elige entre el servidor de desarrollo
  y `ProductionConfig` según `BuildConfig.DEBUG` (requiere `buildFeatures.buildConfig = true`
  en `core/data/build.gradle.kts`, ya añadido). Verificado compilando **ambas** variantes
  (`compileDebugKotlinAndroid` y `compileReleaseKotlinAndroid`) y con `assembleRelease`
  completo pasando por R8.
- **iOS**: sigue sin esta distinción (ver TODO en `DataModule.ios.kt`) porque Kotlin/Native
  no tiene un `BuildConfig.DEBUG` automático; hay que leerlo del scheme de Xcode cuando haya
  Mac disponible para probarlo.
- Rellenar `ProductionConfig` de verdad (URL + pines) es la única tarea pendiente antes de
  poder hacer un release de producción real; sin pines, un release compilaría igual pero
  sin certificate pinning (punto 4 de markdown.md), así que hay un comentario explícito en
  el código avisando de que eso sería un fallo de seguridad silencioso.

## 9. Guía corta: ver la app desde Visual Studio Code

VS Code no trae emulador propio; se usa el del Android SDK (ya instalado en
`C:\Users\ragna\AppData\Local\Android\Sdk`, AVD `Pixel_6a`).

1. Abre la carpeta del proyecto en VS Code. Acepta las extensiones recomendadas
   ([.vscode/extensions.json](.vscode/extensions.json)): Kotlin, Gradle for Java y
   "Android iOS Emulator" (opcional, añade un botón para lanzar el AVD).
2. `Ctrl+Shift+P` > **Tasks: Run Task** > **"1. Servidor Ktor"**. Espera a ver
   `Responding at http://0.0.0.0:8080`.
3. Otra vez **Run Task** > **"2. Emulador Android (Pixel_6a)"**. Se abre la ventana del
   emulador (tarda ~1 min la primera vez). Si el AVD se llama de otra forma, ejecuta la tarea
   "Emuladores: listar AVDs" y cambia el nombre en `.vscode/tasks.json`.
4. **Run Task** > **"3. Instalar y abrir la app en el emulador"**: compila el APK, lo instala
   y abre la app.
5. Entra con `e2e@test.com` / `secreta123` (o regístrate), crea una tarea con categoría,
   prioridad, hora e incremento, márcala como completada y mira la pestaña **Rachas**.
   Prueba también pulsación larga sobre una tarea (selección múltiple) y la pestaña
   **Ajustes** (cerrar sesión / borrar cuenta).
6. Para depurar: **Run Task** > "Logs de la app (logcat)". Para los tests: `Ctrl+Shift+P` >
   *Tasks: Run Test Task*.

Si no quieres crear ningún AVD a mano: Android Studio sigue siendo la vía más rápida para
gestionar emuladores (Device Manager), pero una vez creado, todo el ciclo se hace desde VS
Code con las tareas de arriba.

## 10. Lo que NO está hecho todavía

1. **iOS sigue siendo solo Kotlin**: el proyecto Xcode real no se puede crear en Windows;
   instrucciones en [iosApp/README.md](iosApp/README.md). Pinning SSL, Keychain y el
   switch debug/produccion de `DataModule.ios.kt` (sección 8) están escritos pero no
   compilados en Xcode.
2. **`ProductionConfig` sin rellenar de verdad**: falta un dominio real desplegado y sus
   pines de certificado (sección 8).
3. **Tombstones sin límite de reintentos**: si un borrado falla indefinidamente (p.ej. el
   servidor deja de existir), el tombstone se reintenta en cada sync para siempre; no hay
   una cuenta de intentos ni un tope de tiempo tras el cual abandonar.
4. **Falta paginación/paginado en `GET /tasks`**: con muchísimas tareas el `syncTasks()`
   baja la lista entera cada vez; no es un problema con el volumen esperado de una app
   personal, pero no escalaría a un uso muy intensivo.
5. **No se pide el permiso `POST_NOTIFICATIONS` en tiempo de ejecución**: el manifest lo
   declara y `AgendaFirebaseMessagingService` comprueba si está concedido antes de mostrar
   una notificación (ver sección 7quinquies), pero no hay ningún flujo en la app que lo
   pida al usuario (`ActivityResultContracts.RequestPermission` o similar). En Android 13+
   el permiso empieza denegado, así que los recordatorios push no se van a ver en la
   práctica hasta que el usuario lo conceda a mano desde Ajustes del sistema.
6. **`AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON` y `androidApp/google-services.json` sin
   credenciales reales**: sin un proyecto Firebase real, los recordatorios push funcionan
   solo hasta donde llega `NoOpPushSender`/la lógica probada con tests (ver sección
   7quinquies); nunca se ha mandado una notificación real a un dispositivo.
7. **El token FCM no se borra al hacer logout**: `AuthRepositoryImpl.logout()` limpia los
   tokens de sesión y Room, pero no llama a ningún `DELETE /users/me/fcm-token` (no existe
   esa ruta) ni borra la fila de `FcmTokens`. Como `FcmTokens` tiene `PrimaryKey(userId,
   token)`, el mismo token de dispositivo puede quedar asociado a más de un usuario a la vez;
   si en el mismo dispositivo el usuario A cierra sesión y el usuario B inicia sesión,
   `ReminderJob` seguiría mandando también los recordatorios de A a ese dispositivo. Hoy no es
   explotable (sin credenciales Firebase reales ni permiso `POST_NOTIFICATIONS` concedido, el
   push no llega a ningún sitio), pero hay que cerrarlo antes de activar push de verdad:
   añadir la ruta de borrado + repositorio y llamarla desde `logout()`.
8. **Regresión visual en el calendario: la última semana del mes queda centrada en vez de
   alineada por columnas**. `CalendarScreen.kt`'s `MonthGrid` usa
   `Arrangement.Center` en el `Row` de cada semana; como `weeks` solo rellena huecos al
   principio del mes (no al final), la última semana (con menos de 7 días) se centra en vez
   de quedarse alineada bajo sus columnas de día de la semana. Viene de la revisión de
   cuadrícula cuadrada de la sesión 5 (sección 7ter) — antes de eso el `Row` no centraba.
   Solo estético (no afecta a qué día se pulsa), pero visible. Arreglo sugerido: rellenar
   también el final de la última semana con `null`s hasta 7 elementos.
9. **`ReminderScheduler` (servidor) trata todas las fechas/horas de tarea como UTC**: no hay
   ningún campo de zona horaria en `Task`/`Tasks`, así que un recordatorio puesto a las 09:00
   por un usuario en España puede dispararse una o dos horas más tarde/temprano en hora local
   según la época del año. Corregirlo de verdad necesita añadir un campo de zona horaria a la
   tarea (tocaría los cuatro sitios de la sección 3), fuera del alcance de lo hecho hasta
   ahora — hay que tenerlo en cuenta antes de dar por fiable la hora de un recordatorio push
   real.
10. **El rate limiting de `/auth/*` filtra por `call.request.origin.remoteHost`**: correcto
    mientras el servidor no esté detrás de un proxy inverso; si en el futuro se despliega
    detrás de nginx/Cloudflare/similar sin más cambios, todas las peticiones verían la IP del
    proxy como origen y compartirían el mismo cupo de 10 peticiones/60s — hay que añadir
    entonces soporte de `X-Forwarded-For` con una lista de proxies de confianza (nunca
    confiar en esa cabecera sin verificar quién la manda).

(El punto "editar una tarea desde el calendario" que estaba aquí se resolvió en la sesión
5, ver sección 7bis.)

## 11. Decisiones tomadas que conviene no deshacer sin pensarlo

- **KMP + Koin + Ktor + Room KMP**, no Hilt/Retrofit (exigido por `markdown.md`).
- **H2 en fichero** en el servidor en vez de Postgres/Docker: pragmatismo para arrancar sin
  infraestructura. Pasar a Postgres es cambiar URL/driver en `DatabaseFactory.kt`.
- **Los ids de tarea los genera el cliente** (`randomEntityId()`, 32 hex) y el servidor los
  respeta; `POST /tasks` hace upsert por id. Es lo que hace idempotente la sincronización
  y el reintento de tombstones.
- **Enums como texto (`.name`)** en Room y en el servidor, no ids numéricos. Un enum
  desconocido que llegue del servidor cae al valor por defecto (`TaskDtoMapper`).
- **Fechas entre plataformas**: Room guarda `dateEpochDay`/`timeMinuteOfDay`, el servidor usa
  `java.time`, y el puente siempre es un `String` ISO-8601 en las DTO.
- **JWT con claim `type`** (access/refresh) para que un refresh token no sirva de access token.
- **Racha con periodo de gracia**: `currentStreak` no se rompe a las 00:00 en punto si "hoy"
  aún no tiene nada completado — se ancla en hoy si hay algo completado hoy, si no en ayer
  (y solo entonces cuenta hacia atrás), y da 0 solo si ni hoy ni ayer hay nada. Ver
  `StreakRepositoryImpl.currentStreak` y sus tests para la casuística exacta.
- **WebSocket de una sola vía** (servidor -> cliente): el mensaje es solo la señal de "hay
  cambios", nunca el estado; el cliente siempre re-sincroniza con el `GET /tasks` normal en
  vez de fiarse de un payload que viaje por el socket. Simplifica mucho el contrato y evita
  duplicar la lógica de merge offline-first en dos sitios.
- **Room versión 4** (subió de 3 a 4 al añadir `pending_deletions`) con
  `fallbackToDestructiveMigration(dropAllTables = false)`: sin migraciones manuales porque
  la app aún no está publicada; si algún día hay datos de usuarios reales, hay que escribir
  migraciones de verdad antes de tocar el esquema otra vez.
- **ktlint y detekt en verde en todos los módulos**: excluyen el código generado por
  KSP/Compose Resources vía `.editorconfig` y excludes de Gradle. En
  `config/detekt/detekt.yml`, `TooManyFunctions` está en 20 (DAOs/repositorios con muchas
  consultas) y `LongParameterList` en 9/9 función/constructor — ojo, **el compilador de
  Compose añade un parámetro implícito a las funciones `@Composable`**, así que un
  composable con N parámetros declarados necesita `functionThreshold >= N+1`, no `N`.
