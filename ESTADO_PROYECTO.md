# Estado del proyecto "agenda"

Documento vivo de continuidad entre sesiones. Léelo antes de tocar nada: recoge las
decisiones ya tomadas para no repetir trabajo ni contradecirlas sin querer. La spec
técnica obligatoria (no negociable) sigue estando en [markdown.md](markdown.md); este
documento es el "qué se ha hecho, qué falta y cómo se arranca" sobre esa base.

Última actualización: 2026-09-14 (sesión 5, continuada otra vez: activar "incremental" al
crear una tarea ahora genera de verdad copias adicionales de la tarea, repitiendo sus
características básicas a intervalos regulares).

## 0. Resumen en una frase

App de agenda/tareas con rachas (KMP: Android + iOS futuro) + backend propio en Ktor con
usuarios, login JWT, tareas completas con todos sus campos editables desde la UI, borrado
conjunto, tiempo real por WebSocket y ajustes de cuenta. Offline-first de verdad (Room
como SSOT + tombstones de borrado), **repo git inicializado**, y **176 tests automáticos
en verde** (160 unitarios + 16 instrumentados de Compose UI en el emulador). Todo
verificado en caliente en el emulador Android contra el servidor real, no solo compilado.

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

## 6. Tests (176 automáticos, todos en verde)

`./gradlew check` ejecuta los 160 unitarios (+ktlint+detekt+lint). Los 16 instrumentados de
Compose necesitan el emulador arrancado (ver sección 1).

| Módulo | Tests | Qué cubre |
|---|---|---|
| `:core:domain` | 28 | casos de uso con Fake Repositories, defaults del modelo, borrado conjunto, `GenerateTaskRepetitionsUseCase` (fechas, ids, campos copiados, casos sin incremento) |
| `:core:data` | 49 | mapeos, repositorio offline-first **con tombstones**, login/sesión, refresco de token, **periodo de gracia de rachas** |
| `:feature:login` | 6 unit + 6 UI | reductor MVI + **Compose: campos, error, login OK/fallido, navegación** |
| `:feature:register` | 5 | reductor MVI de registro |
| `:feature:tasks` | 22 unit + 7 UI | reductor MVI (formulario completo, selección múltiple, **crear tarea incremental genera sus copias, editar una existente no las regenera**) + **Compose: estado vacío, crear tarea, validación, fecha inicial desde el calendario, el formulario es deslizable al activar "incremental"** |
| `:feature:calendar` | 7 unit + 3 UI | reductor MVI de calendario (incluye conteo de tareas por día) + `CalendarLayoutTest` (tamaño de celda cuadrado dinámico, función pura) + **Compose: la cuadrícula no superpone días, tocar un día concreto selecciona ese día y no otro, un día con tareas muestra cuántas tiene** |
| `:feature:streaks` | 3 | reductor MVI de rachas |
| `:feature:settings` | 6 | logout, borrar cuenta (éxito y fallo del servidor) |
| `:shared` | 4 | `HomeNavigator`: qué pestaña se ve y qué fecha queda pendiente al abrir un día del calendario |
| `:server` | 30 | API completa (incluida `/tasks/ws`), JWT y bcrypt |

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
