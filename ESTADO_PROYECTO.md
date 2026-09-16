# Estado del proyecto "agenda"

Documento vivo de continuidad entre sesiones. Léelo antes de tocar nada: recoge las
decisiones ya tomadas para no repetir trabajo ni contradecirlas sin querer. La spec
técnica obligatoria (no negociable) sigue estando en [markdown.md](markdown.md); este
documento es el "qué se ha hecho, qué falta y cómo se arranca" sobre esa base.

Última actualización: 2026-09-16 (cierre del plan `calidad-seguridad-recordatorios`:
logging con Napier, accesibilidad del calendario, CI en GitHub Actions, validación y rate
limiting del servidor, recordatorios push reales con Firebase, registro del token FCM en el
cliente Android y buscador/filtro de tareas — todo verificado junto por primera vez, ver
sección 7quinquies).

## 0. Resumen en una frase

App de agenda/tareas con rachas (KMP: Android + iOS futuro) + backend propio en Ktor con
usuarios, login JWT, tareas completas con todos sus campos editables desde la UI, borrado
conjunto, tiempo real por WebSocket, ajustes de cuenta, buscador/filtro de tareas y
recordatorios push reales (Firebase). Offline-first de verdad (Room como SSOT + tombstones
de borrado), validación y rate limiting en el servidor, logging centralizado con Napier,
CI en GitHub Actions, **repo git inicializado**, y **214 tests automáticos en verde**
(196 unitarios + 18 instrumentados de Compose UI en el emulador). Todo verificado en
caliente en el emulador Android contra el servidor real, no solo compilado.

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

Tests: `./gradlew check` (compila todo + ktlint + detekt + lint + los 144 tests unitarios).
Tests de UI de Compose (necesitan el emulador arrancado, sección 6):
`./gradlew :feature:login:connectedDebugAndroidTest :feature:tasks:connectedDebugAndroidTest`.

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

## 6. Tests (214 automáticos, todos en verde)

`./gradlew check` ejecuta los 196 unitarios (+ktlint+detekt+lint). Los 18 instrumentados de
Compose necesitan el emulador arrancado (ver sección 1).

| Módulo | Tests | Qué cubre |
|---|---|---|
| `:core:common` | 2 | `AgendaLogger` (envoltorio de Napier usado en los fallos silenciosos de sync y de registro del token FCM) |
| `:core:domain` | 29 | casos de uso con Fake Repositories, defaults del modelo, borrado conjunto, `GenerateTaskRepetitionsUseCase` (fechas, ids, campos copiados, casos sin incremento), `RegisterFcmTokenUseCase` |
| `:core:data` | 49 | mapeos, repositorio offline-first **con tombstones**, login/sesión, refresco de token, **periodo de gracia de rachas** |
| `:feature:login` | 8 unit + 6 UI | reductor MVI (incluye **registrar el token FCM tras un login correcto, y no llamar al servidor si no hay token disponible**) + **Compose: campos, error, login OK/fallido, navegación** |
| `:feature:register` | 5 | reductor MVI de registro |
| `:feature:tasks` | 26 unit + 8 UI | reductor MVI (formulario completo, selección múltiple, **crear tarea incremental genera sus copias, editar una existente no las regenera**, **buscador por título y filtro por categoría, `SelectAll` respeta el filtro activo**) + **Compose: estado vacío, crear tarea, validación, fecha inicial desde el calendario, el formulario es deslizable al activar "incremental", escribir en el buscador oculta las tareas que no coinciden** |
| `:feature:calendar` | 7 unit + 4 UI | reductor MVI de calendario (incluye conteo de tareas por día) + `CalendarLayoutTest` (tamaño de celda cuadrado dinámico, función pura) + **Compose: la cuadrícula no superpone días, tocar un día concreto selecciona ese día y no otro, un día con tareas muestra cuántas tiene, los botones de navegación de mes tienen descripción accesible** |
| `:feature:streaks` | 3 | reductor MVI de rachas |
| `:feature:settings` | 6 | logout, borrar cuenta (éxito y fallo del servidor) |
| `:shared` | 7 | `HomeNavigator` (4: qué pestaña se ve y qué fecha queda pendiente al abrir un día del calendario) + `SplashSessionHandler` (3: **registra el token FCM también al recuperar sesión en el splash**) |
| `:server` | 54 | API completa (incluida `/tasks/ws`), JWT y bcrypt, **validación de tareas y de email, rate limiting de `/auth`, `ReminderScheduler` (lógica pura de cuándo toca un recordatorio, 10), `ReminderJob` (bucle en segundo plano, 4), `PushSender` (Firebase, 2), `FcmTokenRoutes` (3)** |

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
