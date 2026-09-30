# Migración a Clean Architecture modular por feature (estilo Squadfy_KMM)

- **Fecha:** 2026-09-30
- **Estado:** diseño aprobado en conversación, pendiente de revisión de la spec escrita
- **Referencia:** https://github.com/kikepb7/Squadfy_KMM

## 1. Objetivo

Reestructurar los módulos del cliente KMP de la agenda siguiendo el modelo de Squadfy_KMM:

- `core/` contiene **solo lo transversal**, lo que usa cualquier parte de la app: red, sesión/token,
  logger, la base del ViewModel MVI y el design system.
- `feature/` contiene **cada parte de la app** (auth, tareas, rachas), y cada una se parte en capas
  Clean Architecture: `domain`, `data`, `presentation` y, si tiene persistencia propia, `database`.
- `build-logic/` agrupa la configuración de Gradle repetida en *convention plugins*.

Es un **refactor puro**: la app hace lo mismo antes y después.

### Criterios de éxito

1. `./gradlew check` en verde al terminar **cada** fase (ktlint + detekt + tests).
2. Todos los tests que hay hoy siguen existiendo y pasando. Solo cambian su `package` y sus `import`,
   nunca sus aserciones ni su lógica.
3. La base de datos Room de un usuario que ya tiene la app instalada (esquema v4) se abre sin perder
   datos (misma identidad de esquema, sin migración nueva).
4. La prueba manual en el emulador funciona igual que ahora: login → crear tarea → completarla → ver
   la racha → calendario → ajustes/logout → recibir un push.
5. El CI (`.github/workflows/ci.yml`) sigue en verde.

### Fuera de alcance

- El servidor (`server/`) no se toca.
- No se cambia el patrón de presentación: se mantiene **MVI** (`MviViewModel` + `UiState`/`UiIntent`/
  `UiEffect`), como exige `markdown.md`.
- No hay funcionalidades nuevas ni cambios visuales.
- `shared`, `androidApp` e `iosApp` mantienen su nombre (el framework de iOS se llama `Shared` y
  Xcode depende de él).

## 2. Árbol de módulos resultante

```
agenda/
├── build-logic/convention/              convention plugins (included build)
├── androidApp/                          app Android (Application, MainActivity, FCM service)
├── shared/                              raíz de la app compartida: App.kt, di/, navigation/
├── iosApp/                              sin cambios
├── server/                              sin cambios
│
├── core/
│   ├── domain/        logger/  util/
│   ├── data/          networking/  session/  di/
│   ├── presentation/  mvi/
│   └── designsystem/  component/  theme/
│
└── feature/
    ├── auth/
    │   ├── domain/        model/  repository/  usecase/  di/
    │   ├── data/          remote/  dto/  repository/  fcm/  di/
    │   └── presentation/  login/  register/  forgot_password/  reset_password/  settings/  di/
    ├── tasks/
    │   ├── domain/        model/  repository/  usecase/  di/
    │   ├── data/          remote/  dto/  websocket/  mapper/  repository/  di/
    │   ├── database/      dao/  entity/  AgendaDatabase  di/   (+ schemas/)
    │   └── presentation/  tasks/  calendar/  di/
    └── streaks/
        ├── domain/        model/  repository/  usecase/  di/
        ├── data/          repository/  di/
        └── presentation/  streaks/  di/
```

Se eliminan `core:common`, `core:network`, `core:database` y los 7 módulos de feature actuales
(`login`, `register`, `passwordreset`, `calendar`, `tasks`, `streaks`, `settings`).

### Paquetes

`com.daviddelgado.agenda.<core|feature>.[<feature>.]<capa>.<subpaquete>`

Ejemplos: `com.daviddelgado.agenda.core.data.networking`,
`com.daviddelgado.agenda.feature.tasks.presentation.calendar`.

El `namespace` Android de cada módulo sale de su path de Gradle (`:feature:tasks:data` →
`com.daviddelgado.agenda.feature.tasks.data`).

## 3. Reglas de dependencia

```
feature:X:presentation ──► feature:X:domain ──► core:domain
        │                          ▲
        └──► core:presentation     │
        └──► core:designsystem     │
feature:X:data ────────────────────┘──► core:data ──► core:domain
feature:X:data ──► feature:X:database   (si existe)
shared ──► todo (es el único que cablea Koin con todos los módulos)
androidApp ──► shared
```

- `domain` no depende de `data`, `presentation`, Room, Ktor ni Compose.
- `presentation` **nunca** depende de `data` ni de `database`.
- Una feature no depende de otra, con **una única excepción documentada**:
  `feature:streaks:data ──► feature:tasks:database`, porque la racha se calcula con las fechas de las
  tareas completadas (`TaskDao.observeCompletedDateEpochDays()`). Es una dependencia de datos a datos
  y no rompe la regla de capas.
- `feature:auth:domain` sí la pueden usar otras features y `shared`, porque `User` y la sesión son
  la entrada a toda la app. Hoy solo la usan `shared` (splash, registrar el token FCM) y
  `presentation` de auth.

## 4. Qué va a cada módulo

### core:domain (sin dependencias de plataforma)
| Hoy | Destino |
|---|---|
| `core/common/.../logging/AgendaLogger.kt` (+ test) | `core.domain.logger` |
| `core/common/.../util/Ids.kt` | `core.domain.util` |

### core:data
| Hoy | Destino |
|---|---|
| `core/network/.../AgendaHttpClientConfig.kt`, `HttpClientFactory.{android,ios}.kt`, `NetworkConfig.kt`, `ProductionConfig.kt`, `ApiException.kt` | `core.data.networking` |
| DTOs del refresh (`RefreshRequest`, `AuthResponse`) que usa el `HttpClient` para renovar el token | `core.data.networking.dto` |
| `TokenProvider` (interfaz, hoy en `core:network`), `TokenProviderImpl`, `SecureStorage` (+ `android`/`ios`) | `core.data.session` |
| `TokenRefreshTest`, `MockHttpClient`, `FakeTokenProvider` | tests de `core:data` |
| Parte común de `DataModule` (+ `.android`/`.ios`): SecureStorage, NetworkConfig, TokenProvider, HttpClient | `core.data.di.coreDataModule` + `platformCoreDataModule` |

`AuthResponse` se queda en core porque el refresh del token lo necesita cualquier petición
autenticada. `feature:auth:data` lo reutiliza desde core, no lo duplica.

### core:presentation (nuevo)
| Hoy | Destino |
|---|---|
| `core/common/.../mvi/Mvi.kt`, `MviViewModel.kt` | `core.presentation.mvi` |

### core:designsystem
Solo cambia el paquete (`core.designsystem.component`, `core.designsystem.theme`) y pasa a usar
el convention plugin.

### feature:auth
| Hoy | Destino |
|---|---|
| `User` (en `Models.kt`) | `auth.domain.model` |
| `AuthRepository` (en `Repositories.kt`) | `auth.domain.repository` |
| Login, Register, RestoreSession, ObserveCurrentUser, Logout, DeleteAccount, RegisterFcmToken, RequestPasswordReset, ResetPassword (hoy en `UseCases.kt`) | `auth.domain.usecase`, un fichero por caso de uso |
| Sus tests (`AuthUseCasesTest`, `RegisterFcmTokenUseCaseTest`, `RequestPasswordResetUseCaseTest`, `ResetPasswordUseCaseTest`, `FakeAuthRepository`) | tests de `auth:domain` |
| `AuthApi`, `AuthDtos` (salvo lo que queda en core) | `auth.data.remote`, `auth.data.dto` |
| `AuthRepositoryImpl` (+ test) | `auth.data.repository` |
| `FcmTokenProvider` + `LoginModule.{android,ios}` (su `actual`) | `auth.data.fcm` + `auth.data.di` (plataforma) |
| `feature/login`, `register`, `passwordreset`, `settings` (Contract/Screen/ViewModel/Module + tests unitarios e instrumentados) | `auth.presentation.{login,register,forgot_password,reset_password,settings}` |
| Los 4 `*Module.kt` de presentación | `auth.presentation.di.authPresentationModule` |

### feature:tasks
| Hoy | Destino |
|---|---|
| `Task`, `TaskCategory`, `TaskPriority`, `ReminderFrequency`, `IncrementUnit`, `IncrementConfig` (+ `TaskTest`) | `tasks.domain.model` |
| `TaskRepository` | `tasks.domain.repository` |
| ObserveTasks, UpsertTask, GenerateTaskRepetitions, DeleteTask(s), DeleteAllTasks, ToggleTaskCompletion, SyncTasks, ObserveTaskChanges (+ `TaskUseCasesTest`, `GenerateTaskRepetitionsUseCaseTest`, `FakeTaskRepository`) | `tasks.domain.usecase` |
| `TaskApi`, `TaskDtos` | `tasks.data.remote`, `tasks.data.dto` |
| `WebSocketService` (solo transporta eventos de tareas) | `tasks.data.websocket` |
| `TaskMapper`, `TaskDtoMapper` (+ tests) | `tasks.data.mapper` |
| `TaskRepositoryImpl` (+ test, `FakeTaskDao`, `FakePendingDeletionDao`) | `tasks.data.repository` |
| `AgendaDatabase`, `DatabaseBuilderFactory`, `DatabaseFactory.{android,ios}` | `tasks.database` (+ `di/` de plataforma) |
| `TaskDao`, `PendingDeletionDao` | `tasks.database.dao` |
| `TaskEntity`, `PendingDeletionEntity` | `tasks.database.entity` |
| `core/database/schemas/` | `feature/tasks/database/schemas/` (ver §6) |
| `feature/tasks`, `feature/calendar` (+ tests unitarios e instrumentados, `CalendarLayout`) | `tasks.presentation.tasks`, `tasks.presentation.calendar` |

Si al mover `WebSocketService` resulta que lo usa algo que no son tareas, se queda en
`core.data.networking` y se anota en el plan. Esta es la única ubicación que depende de verificar el
código.

### feature:streaks
| Hoy | Destino |
|---|---|
| `StreakSummary`, `StreakRepository`, `ObserveStreakUseCase` | `streaks.domain.{model,repository,usecase}` |
| `StreakRepositoryImpl` (+ test; copia de `FakeTaskDao`) | `streaks.data.repository` |
| `feature/streaks` (+ test) | `streaks.presentation.streaks` |

### shared
| Hoy | Destino |
|---|---|
| `AppModules.kt`, `KoinInitializer.kt` | `shared.di` |
| `HomeNavigator.kt`, `SplashSessionHandler.kt` (+ tests) | `shared.navigation` |
| `App.kt`, `MainViewController.kt` | se quedan en `shared` |

## 5. Inyección de dependencias (Koin)

`DomainModule` y `DataModule`, que hoy son únicos, se dividen: cada módulo Gradle expone el suyo.

| Módulo Gradle | Módulo Koin |
|---|---|
| `core:data` | `coreDataModule` (incluye `platformCoreDataModule`) |
| `feature:auth:domain` / `data` / `presentation` | `authDomainModule` / `authDataModule` (+ plataforma, FCM) / `authPresentationModule` |
| `feature:tasks:domain` / `data` / `database` / `presentation` | `tasksDomainModule` / `tasksDataModule` / `tasksDatabaseModule` (+ plataforma) / `tasksPresentationModule` |
| `feature:streaks:domain` / `data` / `presentation` | `streaksDomainModule` / `streaksDataModule` / `streaksPresentationModule` |

`shared/di/AppModules.kt` junta todos. Los `single`/`factory`/`viewModel` son los mismos que hoy;
solo cambia en qué módulo están declarados.

## 6. Room: conservar la base de datos instalada

- La carpeta de esquemas de Room lleva el nombre completo de la clase:
  `schemas/com.daviddelgado.agenda.database.AgendaDatabase/{1..4}.json`. Al cambiar el paquete de
  `AgendaDatabase`, la carpeta se **renombra** al nombre nuevo
  (`com.daviddelgado.agenda.feature.tasks.database.AgendaDatabase`). Los JSON no se editan.
- La base de datos del dispositivo se identifica por su `identityHash`, que depende de las
  tablas y columnas, no del paquete. Por eso hay que comprobar que el `4.json` que se regenera al
  compilar es **idéntico byte a byte** al anterior. Si difiere, la fase se para.
- El nombre del fichero `.db` (en `DatabaseFactory`) no cambia.
- Las `AutoMigration`s y `Migration`s que haya siguen en `AgendaDatabase` sin cambios.

## 7. build-logic

`build-logic/` como *included build* (`pluginManagement { includeBuild("build-logic") }`) que usa el
mismo `libs.versions.toml`.

| Plugin id | Aplica | Lo usan |
|---|---|---|
| `agenda.kmp.library` | kotlin-multiplatform + android-library; `jvmToolchain(17)`; targets `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`; `compileSdk 34`, `minSdk 26`, Java 17; `namespace` sacado del path; `kotlin("test")` + coroutines-test en `commonTest` | `core:domain`, `core:data`, `feature:*:domain`, `feature:*:data`, `feature:tasks:database` |
| `agenda.cmp.library` | `agenda.kmp.library` + compose-multiplatform + compose-compiler + runtime/foundation/material3/ui | `core:designsystem`, `core:presentation` |
| `agenda.cmp.feature` | `agenda.cmp.library` + koin core/compose/viewmodel + `core:presentation` + `core:designsystem` + icons-extended + `testInstrumentationRunner` y dependencias de `androidInstrumentedTest` | `feature:*:presentation` |
| `agenda.room` | ksp + room; `schemaDirectory("$projectDir/schemas")`; `ksp{Android,IosX64,IosArm64,IosSimulatorArm64}` con room-compiler; `api(room-runtime)` | `feature:tasks:database` |
| `agenda.android.application` | android-application + kotlin-android + compose-compiler; `compileSdk`/`minSdk`/`targetSdk`, Java 17 | `androidApp` |

Lo que no es común (p. ej. `buildConfig = true` en `core:data`, `kotlin-serialization`, el plugin
de google-services en `androidApp`, el framework `Shared`) se queda en el `build.gradle.kts` del
módulo. ktlint y detekt siguen configurados en el `subprojects {}` del `build.gradle.kts` raíz, sin
cambios. `build-logic` se excluye de ktlint/detekt, igual que en Squadfy.

## 8. Fases

Cada fase termina con `./gradlew check` en verde y un commit convencional. Todo se mueve con `git mv`
para conservar el historial.

1. **build-logic**: crear los 5 plugins y pasar los módulos **actuales** a usarlos, sin mover
   código. Commit: `build: añade build-logic con convention plugins`.
2. **core**: crear `core:presentation`; mover `core:common` y `core:network` a `core:domain`,
   `core:data` y `core:presentation`; separar el DI de core; borrar `core:common` y `core:network`.
   Temporalmente, `core:data` sigue conteniendo los repositorios de auth, tareas y rachas hasta su
   fase. Commit: `refactor(core): ...`.
3. **feature/auth**: crear `auth/{domain,data,presentation}`; mover lo de §4; borrar
   `feature:login`, `register`, `passwordreset` y `settings`. Commit: `refactor(auth): ...`.
4. **feature/tasks**: crear `tasks/{domain,data,database,presentation}`; mover lo de §4 y §6;
   borrar `core:database`, `feature:tasks` y `feature:calendar`. Verificar el `4.json`. Commit:
   `refactor(tasks): ...`.
5. **feature/streaks**: crear `streaks/{domain,data,presentation}`; con esto `core:domain` y
   `core:data` quedan solo con lo transversal. Borrar `feature:streaks`. Commit:
   `refactor(streaks): ...`.
6. **shared + cierre**: `shared/{di,navigation}`; revisar `ci.yml`; comprobar que ningún módulo
   rompe las reglas de §3 con `grep` de imports; prueba manual en el emulador (§1, criterio 4);
   actualizar `markdown.md` (lista de módulos del punto de arquitectura) y `ESTADO_PROYECTO.md`
   (árbol nuevo y decisión tomada). Commit: `docs: ...`.

Después de la fase 6 se hace una revisión con contexto nuevo antes del push (regla de PR de
AGENTS.md).

## 9. Pruebas y verificación

- **No se escriben tests nuevos de comportamiento**, porque no cambia ningún comportamiento. Los
  tests existentes son la red de seguridad: si hay que tocar la lógica de alguno, la fase lo ha roto.
- En cada fase: `./gradlew check`. Si la fase mueve pantallas con test instrumentado (auth,
  tasks), también `./gradlew :feature:<x>:presentation:connectedAndroidTest` con el emulador
  levantado.
- Fase 4: comparar el `4.json` regenerado con el original.
- Fase 6: comprobar las reglas de capas buscando imports prohibidos. Por ejemplo, cualquier
  `import ...feature.*.data` dentro de un `presentation`, o cualquier `import androidx.room`/
  `io.ktor`/`androidx.compose` dentro de un `domain`, debe dar 0 resultados.
- iOS: en Windows no se puede enlazar el framework. Se valida hasta donde se puede
  (`compileKotlinIosX64` / metadata de `commonMain`). Queda anotado en `ESTADO_PROYECTO.md` que
  falta compilarlo en un Mac.

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Room no encuentra los esquemas o cambia la identidad de la BD | §6: renombrar la carpeta y comparar el `4.json` regenerado |
| Ciclo de dependencias `core:data` ↔ features durante la transición | Orden de fases: core primero con los repositorios dentro y luego salen feature a feature; nunca hay un core que dependa de una feature |
| Koin falla en tiempo de ejecución (una definición se pierde al dividir módulos) | Prueba manual completa en el emulador en la fase 6; conviene además probar el arranque al final de las fases 3 y 4 |
| Un fake duplicado (`FakeTaskDao`, `FakeAuthRepository`) diverge | Son copias literales; se anota que la fuente de la verdad es la de `data`/`domain` de su feature |
| El proyecto iOS no compila | Validación parcial en Windows y aviso explícito en `ESTADO_PROYECTO.md` |
| El diff es grande y cuesta revisarlo | Un commit por fase y renombres con `git mv` (git los muestra como `R`) |
