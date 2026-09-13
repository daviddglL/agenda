# Estado del proyecto "agenda"

Documento vivo de continuidad entre sesiones. Léelo antes de tocar nada: recoge las
decisiones ya tomadas para no repetir trabajo ni contradecirlas sin querer. La spec
técnica obligatoria (no negociable) sigue estando en [markdown.md](markdown.md); este
documento es el "qué se ha hecho, qué falta y cómo se arranca" sobre esa base.

Última actualización: 2026-09-13 (sesión 3: tests unitarios + cliente conectado al servidor).

## 0. Resumen en una frase

App de agenda/tareas con rachas (KMP: Android + iOS futuro) + backend propio en Ktor con
usuarios, login JWT y tareas completas (categoría, prioridad, recordatorio, incremento
progresivo), con borrado conjunto en todos los niveles. **El cliente ya habla con el
servidor de verdad** (offline-first con Room como SSOT) y hay **119 tests unitarios en
verde**. Probado en caliente en el emulador de Android contra el servidor real, no solo
compilado.

## 1. Arrancar todo (lo primero que querrás hacer)

Tres comandos, cada uno en su terminal. En VS Code están también como tareas: `Ctrl+Shift+P`
> *Tasks: Run Task* > "1. Servidor Ktor", "2. Emulador Android", "3. Instalar y abrir la app"
(ver [.vscode/tasks.json](.vscode/tasks.json) y la guía de la sección 8).

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
**`e2e@test.com` / `secreta123`** (tiene 2 tareas de hoy). Si borras `server/data/`, se
regenera vacía y hay que registrarse de nuevo desde la app.

Tests: `./gradlew check` (compila todo + ktlint + detekt + lint + los 119 tests).

## 2. Estructura de módulos

```
core/common        -> base MVI (UiState/UiIntent/UiEffect, MviViewModel) + randomEntityId()
core/designsystem  -> tema Compose Multiplatform (paleta de Figma)
core/domain        -> modelos, enums, interfaces de repositorio, casos de uso
core/network       -> Ktor Client, refresco de token real, ApiException, AuthApi/TaskApi, WebSockets
core/database      -> Room KMP (offline-first), TaskEntity con pendingSync
core/data          -> implementaciones de repositorio (SSOT), mapeos, Keychain/EncryptedPrefs
feature/login, register, calendar, tasks, streaks -> UI + MVI por pantalla (Compose)
shared             -> navegación, arranque de Koin, entry point iOS (mainViewController)
androidApp         -> app Android final
server             -> backend Ktor Server + Exposed + H2
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
enum class IncrementUnit { REPETICIONES, DIAS, SEMANAS, MESES }
data class IncrementConfig(val amount: Int, val everyValue: Int, val everyUnit: IncrementUnit)
```

`IncrementConfig` cubre "es incremental / cuánto se incrementa / cada cuánto": p.ej.
`amount=5, everyValue=2, everyUnit=SEMANAS` = "+5 cada 2 semanas" (hábito con sobrecarga
progresiva, tipo "10 flexiones, sube 5 cada 2 semanas").

**Si añades o cambias un campo de tarea, hay que tocarlo en estos cuatro sitios:**
1. `core/domain/.../model/Models.kt` (fuente de verdad conceptual)
2. `core/database/.../TaskEntity.kt` + `TaskDao.kt` (Room; enums como `.name` en texto)
3. `core/network/.../dto/TaskDtos.kt` + `core/data/.../task/TaskDtoMapper.kt` (contrato de red)
4. `server/.../dto/Dtos.kt` + `server/.../db/Tables.kt` + `server/.../repository/TaskRepository.kt`

Borrado conjunto disponible en todos los niveles:
- Dominio: `TaskRepository.deleteTasks(ids)`, `.deleteAllTasks()`, `AuthRepository.deleteAccount()`
- Casos de uso: `DeleteTasksUseCase`, `DeleteAllTasksUseCase`, `DeleteAccountUseCase`
- Servidor: `POST /tasks/bulk-delete`, `DELETE /tasks`, `DELETE /users/me` (cascada real por `ON DELETE CASCADE`)

## 4. Backend (`server/`)

Stack: Ktor Server (Netty) + Exposed + H2 en fichero (cero configuración, sin Docker ni
Postgres) + JWT (`com.auth0:java-jwt`) + bcrypt (jbcrypt).

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

Auth: `Authorization: Bearer <accessToken>`; access token 30 min, refresh 30 días, claim
`type` para que uno no sirva como el otro.

Que `POST /tasks` sea idempotente por `id` es lo que permite reintentar sin duplicar la
subida de una tarea creada sin red (ver sección 5).

## 5. Cómo está conectado el cliente con el servidor (lo nuevo de esta sesión)

**URL base por plataforma** (`core/data/.../di/DataModule.android.kt` / `.ios.kt`):
- Android: `http://10.0.2.2:8080/` — 10.0.2.2 es como el emulador ve el `localhost` del PC.
- iOS: `http://localhost:8080/` (el simulador comparte red con el Mac).
- Móvil físico: pon ahí la IP del PC en la red local (`http://192.168.x.y:8080/`).
- Producción: URL `https` real + `certificatePinsSha256` en el mismo `NetworkConfig`.

Android solo permite HTTP en claro para esas direcciones de desarrollo, por
[network_security_config.xml](androidApp/src/main/res/xml/network_security_config.xml).

**Offline-first de verdad** (`core/data/.../task/TaskRepositoryImpl.kt`): Room es la única
fuente de verdad; la UI nunca espera a la red.
- Toda escritura va primero a Room con `pendingSync = true` y después se empuja al servidor;
  si el empuje funciona, se marca `pendingSync = false`.
- `syncTasks()` (caso de uso `SyncTasksUseCase`): (1) sube lo pendiente, (2) baja `GET /tasks`
  a Room, (3) borra de local lo que el servidor ya no tiene **sin tocar lo pendiente de subir**.
- Se llama al abrir la pantalla de tareas (silencioso si falla) y con el botón de refrescar
  de "Mis tareas" (ahí sí avisa por snackbar).
- Limitación conocida: los borrados hechos sin red no se reintentan (no hay tabla de
  tombstones), así que una tarea borrada offline puede reaparecer en el siguiente sync.

**Sesión y tokens**:
- `AuthRepositoryImpl` usa `AuthApi` y guarda el par de tokens en almacenamiento cifrado
  (`EncryptedSharedPreferences` / Keychain) vía `TokenProvider`.
- El refresco automático está conectado de verdad: ante un 401, el plugin `Auth` de Ktor
  llama a `POST /auth/refresh`, guarda el par nuevo y reintenta la petición; si el refresco
  también falla, limpia los tokens (`core/network/.../AgendaHttpClientConfig.kt`).
- Tras login/registro/logout se llama a `AuthApi.forgetCachedTokens()` para que el plugin
  Bearer no siga usando el token cacheado (sin esto, la primera petición protegida gastaba
  un 401 + refresco inútiles; verificado en el log del servidor).
- El splash intenta `restoreSession()` (`GET /users/me`): si el token guardado sigue siendo
  válido entra directo a Home, si no va al login.

**Errores legibles**: el cliente usa `expectSuccess = true` y `apiCall {}` traduce cualquier
4xx/5xx a `ApiException(statusCode, message)` con el `message` que manda el servidor, así la
UI muestra "Email o contrasena incorrectos" y no "Client request invalid: 401".

## 6. Tests (119, todos en verde)

`./gradlew check` lo ejecuta todo. Por partes:

| Módulo | Tests | Qué cubre | Comando |
|---|---|---|---|
| `:core:domain` | 21 | casos de uso con Fake Repositories, defaults del modelo, borrado conjunto | `./gradlew :core:domain:testDebugUnitTest` |
| `:core:data` | 42 | mapeos dominio↔Room y dominio↔DTO, repositorio offline-first (MockEngine), login/sesión, **refresco de token** | `./gradlew :core:data:testDebugUnitTest` |
| `:feature:*` | 28 | reductores MVI de login, registro, tareas, calendario y rachas | `./gradlew :feature:tasks:testDebugUnitTest` |
| `:server` | 28 | API completa con `testApplication` + H2 en memoria, JWT y bcrypt | `./gradlew :server:test` |

Cómo están montados (para escribir más igual):
- Todo en `commonTest` con `kotlin("test")` + `kotlinx-coroutines-test`; se ejecutan en el
  target Android (`testDebugUnitTest`).
- Los ViewModel necesitan `Dispatchers.setMain(UnconfinedTestDispatcher())` en `@BeforeTest`
  (el `viewModelScope` usa `Dispatchers.Main`), y los efectos se recogen con
  `viewModel.effect.onEach { ... }.launchIn(backgroundScope)` **antes** de lanzar la intención.
- La red se simula con `MockEngine` **pero instalando los plugins reales** de la app
  (`mockHttpClient()` en `core/data/src/commonTest/.../fake/MockHttpClient.kt`), así los tests
  ejercitan JSON, Bearer, `expectSuccess` y el refresco de token.
- Fakes reutilizables: `FakeTaskDao`, `FakeTokenProvider` (`core/data`), `FakeTaskRepository`,
  `FakeAuthRepository` (`core/domain` y por feature).
- El servidor se prueba entero con `withApi { }` (`server/src/test/.../api/TestApi.kt`), que
  levanta la app real sobre una H2 en memoria distinta en cada test.
- `assertEquals(null, x)` y `assertEquals(emptyList(), x)` no compilan en KMP (inferencia):
  usa `assertNull` / `assertTrue(x.isEmpty())`, o `listOf<Tipo>(...)` explícito.

## 7. Verificado en caliente el 2026-09-13 (no solo compilado)

- `./gradlew check` -> BUILD SUCCESSFUL (incluye ktlint, detekt, lint y los 119 tests).
- `./gradlew :androidApp:assembleDebug` -> BUILD SUCCESSFUL.
- Emulador Pixel_6a + `:server:run`, flujo completo desde la UI:
  - Registro desde la app -> `201 POST /auth/register`.
  - Login -> `200 POST /auth/login` y a continuación `200 GET /tasks` (sin el 401 inútil).
  - Crear tarea en la app -> `201 POST /tasks`; comprobado con curl que la tarea está en el
    servidor con el id de 32 hex que generó el cliente.
  - Tarea creada en el servidor con curl -> aparece en la app al pulsar el botón de refrescar.
  - Marcar una tarea como completada en la app -> `204 PATCH /tasks/{id}/toggle-completed`.
  - Refresco de token real: con el token cacheado vacío se vio `401 GET /tasks` ->
    `200 POST /auth/refresh` -> `200 GET /tasks`.

## 8. Guía corta: ver la app desde Visual Studio Code

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
5. Regístrate (o entra con `e2e@test.com` / `secreta123`), crea una tarea y verás el
   `POST /tasks` en la terminal del servidor. El icono de refrescar de "Mis tareas" baja lo
   que haya en el servidor.
6. Para depurar: **Run Task** > "Logs de la app (logcat)". Para los tests: `Ctrl+Shift+P` >
   *Tasks: Run Test Task*.

Si no quieres crear ningún AVD a mano: Android Studio sigue siendo la vía más rápida para
gestionar emuladores (Device Manager), pero una vez creado, todo el ciclo se hace desde VS
Code con las tareas de arriba.

## 9. Lo que NO está hecho todavía (por orden de prioridad sensata)

1. **La UI no expone los campos nuevos.** `NewTaskFormDialog` (en
   `feature/tasks/.../TasksScreen.kt`) solo tiene título y descripción: faltan selectores de
   categoría, prioridad, hora/duración, recordatorio e incremento, que ya existen en
   dominio/Room/API/servidor.
2. **Borrado conjunto sin UI**: `DeleteTasksUseCase`/`DeleteAllTasksUseCase` y los endpoints
   existen, pero falta la selección múltiple en la lista de tareas.
3. **Logout y borrar cuenta no tienen entrada en la UI** (no hay pantalla de ajustes/perfil);
   los casos de uso `LogoutUseCase` y `DeleteAccountUseCase` están listos y testeados.
4. **Borrados offline sin reintento** (tombstones) — ver limitación en la sección 5.
5. **WebSockets sin usar**: `WebSocketService` existe en el cliente y el servidor no expone
   todavía ningún canal en tiempo real (la spec lo pide, punto 3).
6. **Sin tests de UI de Compose** (la spec los pide en el punto 5); los reductores MVI sí
   están cubiertos.
7. **Racha "de ayer"**: si hoy no hay nada completado, `currentStreak` es 0 aunque ayer
   hubiera racha (comportamiento actual, cubierto por un test que lo documenta). Decidir si
   debe mantenerse hasta el final del día.
8. **iOS sigue siendo solo Kotlin**: el proyecto Xcode real no se puede crear en Windows;
   instrucciones en [iosApp/README.md](iosApp/README.md). Pinning SSL y Keychain de iOS
   están escritos pero no compilados en Xcode.
9. **Sin control de versiones**: la carpeta no es un repo git, así que no hay commits
   (la spec pide Conventional Commits). `git init` cuando se decida.

## 10. Decisiones tomadas que conviene no deshacer sin pensarlo

- **KMP + Koin + Ktor + Room KMP**, no Hilt/Retrofit (exigido por `markdown.md`).
- **H2 en fichero** en el servidor en vez de Postgres/Docker: pragmatismo para arrancar sin
  infraestructura. Pasar a Postgres es cambiar URL/driver en `DatabaseFactory.kt`.
- **Los ids de tarea los genera el cliente** (`randomEntityId()`, 32 hex) y el servidor los
  respeta; `POST /tasks` hace upsert por id. Es lo que hace idempotente la sincronización.
- **Enums como texto (`.name`)** en Room y en el servidor, no ids numéricos, para que las
  migraciones y el debug sean legibles. Un enum desconocido que llegue del servidor cae al
  valor por defecto en vez de romper la lista entera (`TaskDtoMapper`).
- **Fechas entre plataformas**: Room guarda `dateEpochDay`/`timeMinuteOfDay`, el servidor usa
  `java.time`, y el puente siempre es un `String` ISO-8601 en las DTO ("yyyy-MM-dd"/"HH:mm";
  el cliente acepta también "HH:mm:ss" que es como serializa el servidor).
- **JWT con claim `type`** (access/refresh) para que un refresh token no sirva de access token.
- **Room versión 3** con `fallbackToDestructiveMigration(dropAllTables = false)`: al añadir
  `pendingSync` se decidió no escribir migración manual (app aún sin publicar); si algún día
  hay datos de usuarios reales, hay que escribir migraciones de verdad.
- **ktlint y detekt en verde en todos los módulos** (fue una pelea real: excluyen el código
  generado por KSP/Compose Resources vía `.editorconfig` y excludes de Gradle; no lo toques
  sin motivo). En `config/detekt/detekt.yml` se subió `TooManyFunctions` a 20 porque un DAO
  de Room es por naturaleza una lista larga de consultas.
