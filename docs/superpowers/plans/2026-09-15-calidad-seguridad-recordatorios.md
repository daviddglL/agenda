# Calidad tecnica + seguridad + funcional (recordatorios/buscador) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cerrar los huecos identificados en el analisis del proyecto "agenda" (DAFO + auditoria), en el orden pedido: 3 (calidad tecnica: logging, accesibilidad, CI), 2 (seguridad: validacion del servidor, rate limiting) y 1 (funcional: recordatorios push reales via FCM, buscador/filtro de tareas).

**Architecture:** Cada tarea es un cambio autocontenido sobre la Clean Architecture modular ya existente (`core/*`, `feature/*`, `server`). Los recordatorios se implementan server-side (el modulo `:server` NO depende de `:core:domain`, asi que la logica de "cuando toca avisar" se repite en JVM puro dentro de `:server`) mas un receptor FCM en `:androidApp`. iOS no se toca (sigue sin compilar en Xcode, ver ESTADO_PROYECTO.md seccion 10).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, Ktor Client/Server, Room KMP, Exposed+H2, Napier (logging cliente), Firebase Admin SDK (push servidor) + Firebase Cloud Messaging (push cliente Android), GitHub Actions (CI), kotlin.test + MockEngine + Compose UI Test (TDD ya establecido en el repo).

**Spec:** Este documento es la spec (nace del DAFO + analisis de huecos ya acordado con el usuario en esta conversacion). Referencias tecnicas no negociables: `markdown.md` y `ESTADO_PROYECTO.md` en la raiz del repo.

## Global Constraints

- Clean Architecture modular + MVI + Koin + KMP + Compose Multiplatform + Room KMP offline-first + Ktor Client/Server (markdown.md, puntos 1-3). No sustituir ninguna tecnologia ya elegida.
- ktlint + detekt en verde en todos los modulos (`./gradlew check` debe seguir en BUILD SUCCESSFUL tras cada tarea).
- Comentarios y strings visibles al usuario en español (sin tildes en identificadores de codigo, tal y como ya hace el repo: "seleccion", "recordatorio", etc.). Identificadores en ingles.
- TDD real: test en rojo antes que codigo de produccion, en cada tarea que tenga logica no trivial.
- Sin credenciales reales disponibles todavia (ni Firebase ni SMTP): todo lo que dependa de credenciales sigue el patron ya usado en `ProductionConfig.kt` — infraestructura y codigo listos, con placeholders documentados que no rompen la build ni los tests, y sin fallar silenciosamente en produccion sin dejar rastro (log de aviso).
- Actualizar `ESTADO_PROYECTO.md` al terminar todas las tareas (nueva seccion describiendo lo hecho, siguiendo el estilo de las secciones "Verificado en caliente" ya existentes).

---

## Seccion 3 — Calidad tecnica

### Task 1: Logging centralizado en el cliente (Napier)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/common/build.gradle.kts`
- Create: `core/common/src/commonMain/kotlin/com/daviddelgado/agenda/common/logging/AgendaLogger.kt`
- Create: `core/common/src/commonTest/kotlin/com/daviddelgado/agenda/common/logging/AgendaLoggerTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/KoinInitializer.kt`
- Modify: `core/data/src/commonMain/kotlin/com/daviddelgado/agenda/data/task/TaskRepositoryImpl.kt`

**Interfaces:**
- Produces: `AgendaLogger.start()`, `AgendaLogger.d(tag, message)`, `AgendaLogger.w(tag, message, throwable)`, `AgendaLogger.e(tag, message, throwable)` — usado por cualquier modulo cliente que dependa de `:core:common` (ya lo hacen todos).

- [ ] **Step 1: Añadir Napier al catalogo de versiones**

En `gradle/libs.versions.toml`, dentro de `[versions]` (junto a `logback`):

```toml
napier = "2.7.1"
```

Dentro de `[libraries]` (junto a `kotlinx-coroutines-core`):

```toml
napier = { group = "io.github.aakira", name = "napier", version.ref = "napier" }
```

- [ ] **Step 2: Añadir la dependencia a `:core:common`**

En `core/common/build.gradle.kts`, dentro de `commonMain.dependencies`:

```kotlin
implementation(libs.napier)
```

Y añadir el bloque de tests que hoy no existe (justo despues de `commonMain.dependencies { ... }`):

```kotlin
commonTest.dependencies {
    implementation(kotlin("test"))
}
```

- [ ] **Step 3: Escribir el test (en rojo) de `AgendaLogger`**

`core/common/src/commonTest/kotlin/com/daviddelgado/agenda/common/logging/AgendaLoggerTest.kt`:

```kotlin
package com.daviddelgado.agenda.common.logging

import kotlin.test.Test

class AgendaLoggerTest {
    @Test
    fun arrancarDosVecesNoFalla() {
        AgendaLogger.start()
        AgendaLogger.start()
    }

    @Test
    fun logueaSinLanzarExcepcion() {
        AgendaLogger.start()
        AgendaLogger.d("Test", "mensaje de depuracion")
        AgendaLogger.w("Test", "aviso", RuntimeException("motivo"))
        AgendaLogger.e("Test", "error", RuntimeException("causa"))
    }
}
```

Run: `./gradlew :core:common:testDebugUnitTest`
Expected: FAIL (no existe `AgendaLogger`, error de compilacion).

- [ ] **Step 4: Implementar `AgendaLogger`**

`core/common/src/commonMain/kotlin/com/daviddelgado/agenda/common/logging/AgendaLogger.kt`:

```kotlin
package com.daviddelgado.agenda.common.logging

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

/**
 * Punto unico de logging de la app cliente (Android/iOS). Envuelve Napier para no atar el
 * resto del codigo a una libreria concreta: si algun dia se añade un crash reporter real
 * (Sentry/Crashlytics), solo hay que tocar este fichero.
 */
object AgendaLogger {
    private var started = false

    fun start() {
        if (started) return
        Napier.base(DebugAntilog())
        started = true
    }

    fun d(
        tag: String,
        message: String,
    ) = Napier.d(message, tag = tag)

    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) = Napier.w(message, throwable, tag)

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) = Napier.e(message, throwable, tag)
}
```

Run: `./gradlew :core:common:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Arrancarlo al inicio de la app y usarlo en los `runCatching` silenciosos**

En `shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/KoinInitializer.kt`, añadir el arranque antes de `startKoin`:

```kotlin
package com.daviddelgado.agenda.shared

import com.daviddelgado.agenda.common.logging.AgendaLogger
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/** Punto de entrada comun; cada plataforma añade sus modulos especificos (androidContext, etc). */
fun initKoin(
    extraModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
) {
    AgendaLogger.start()
    startKoin {
        appDeclaration()
        modules(appModules + extraModules)
    }
}
```

En `core/data/src/commonMain/kotlin/com/daviddelgado/agenda/data/task/TaskRepositoryImpl.kt`, registrar los fallos silenciosos que hoy se tragan con `runCatching {}.onSuccess { ... }` sin dejar rastro. Añade el import `com.daviddelgado.agenda.common.logging.AgendaLogger` y cambia:

```kotlin
override suspend fun upsertTask(task: Task) {
    val isNew = dao.getById(task.id) == null
    dao.upsert(task.toEntity(pendingSync = true))
    runCatching {
        if (isNew) api.create(task.toDto()) else api.update(task.toDto())
    }.onSuccess { dao.markSynced(task.id) }
        .onFailure { AgendaLogger.w("TaskRepository", "No se pudo subir la tarea ${task.id}", it) }
}
```

y en `pushPendingDeletions`/`syncTasks` (bloque `runCatching { ... }` de `syncTasks`):

```kotlin
override suspend fun syncTasks(): Result<Unit> =
    runCatching {
        pushPendingDeletions()
        pushPending()
        pullRemote()
    }.onFailure { AgendaLogger.e("TaskRepository", "Fallo sincronizando tareas", it) }
```

Y en `deleteWithTombstone`:

```kotlin
private suspend fun deleteWithTombstone(
    ids: List<String>,
    remoteDelete: suspend () -> Unit,
) {
    if (ids.isEmpty()) return
    dao.deleteByIds(ids)
    pendingDeletionDao.upsertAll(ids.map { PendingDeletionEntity(it) })
    runCatching { remoteDelete() }
        .onSuccess { pendingDeletionDao.deleteByIds(ids) }
        .onFailure { AgendaLogger.w("TaskRepository", "Borrado remoto pendiente de reintentar: $ids", it) }
}
```

- [ ] **Step 6: Verificar y commitear**

Run: `./gradlew :core:common:testDebugUnitTest :core:data:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (los tests existentes de `TaskRepositoryImplTest` no deben romperse: solo se añadio logging, ningun cambio de comportamiento).

```bash
git add gradle/libs.versions.toml core/common/build.gradle.kts core/common/src/commonMain/kotlin/com/daviddelgado/agenda/common/logging/AgendaLogger.kt core/common/src/commonTest/kotlin/com/daviddelgado/agenda/common/logging/AgendaLoggerTest.kt shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/KoinInitializer.kt core/data/src/commonMain/kotlin/com/daviddelgado/agenda/data/task/TaskRepositoryImpl.kt
git commit -m "feat: logging centralizado con Napier en fallos silenciosos de sync"
```

---

### Task 2: Accesibilidad — navegacion del calendario legible por lector de pantalla

**Files:**
- Modify: `feature/calendar/src/commonMain/kotlin/com/daviddelgado/agenda/feature/calendar/CalendarScreen.kt:57,62`
- Modify: `feature/calendar/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/calendar/CalendarScreenTest.kt`

**Interfaces:**
- Consumes: nada nuevo (usa `Icons.Filled.ArrowBack`/`ArrowForward`, ya disponibles sin dependencia extra, igual que `Icons.Filled.Add/Refresh/Close/DoneAll/Delete` en `feature/tasks`).

- [ ] **Step 1: Escribir el test instrumentado (en rojo)**

Añadir a `feature/calendar/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/calendar/CalendarScreenTest.kt` (dentro de la clase `CalendarScreenTest`, junto a los demas `@Test`):

```kotlin
    @Test
    fun losBotonesDeNavegacionDelMesTienenDescripcionAccesible() {
        composeRule.setContent { CalendarScreen(onOpenDay = {}, viewModel = viewModelCon()) }

        composeRule.onNodeWithContentDescription("Mes anterior").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mes siguiente").assertIsDisplayed()
    }
```

Añadir el import que falta: `import androidx.compose.ui.test.onNodeWithContentDescription`.

Run: `./gradlew :feature:calendar:connectedDebugAndroidTest` (con el emulador Pixel_6a arrancado)
Expected: FAIL — no existe ningun nodo con esa `contentDescription` (hoy los botones solo tienen `Text("<")`/`Text(">")`).

- [ ] **Step 2: Sustituir los textos "<"/">" por iconos con descripcion**

En `feature/calendar/src/commonMain/kotlin/com/daviddelgado/agenda/feature/calendar/CalendarScreen.kt`, añadir los imports:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
```

Y sustituir las lineas 57 y 62:

```kotlin
IconButton(onClick = { viewModel.onIntent(CalendarIntent.PreviousMonth) }) {
    Icon(Icons.Filled.ArrowBack, contentDescription = "Mes anterior")
}
Text(
    text = "${state.visibleMonth.month.name} ${state.visibleMonth.year}",
    style = MaterialTheme.typography.titleLarge,
)
IconButton(onClick = { viewModel.onIntent(CalendarIntent.NextMonth) }) {
    Icon(Icons.Filled.ArrowForward, contentDescription = "Mes siguiente")
}
```

- [ ] **Step 3: Verificar y commitear**

Run: `./gradlew :feature:calendar:connectedDebugAndroidTest`
Expected: 4/4 tests en verde (los 3 existentes + el nuevo).

```bash
git add feature/calendar/src/commonMain/kotlin/com/daviddelgado/agenda/feature/calendar/CalendarScreen.kt feature/calendar/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/calendar/CalendarScreenTest.kt
git commit -m "fix: descripcion accesible en la navegacion de mes del calendario"
```

---

### Task 3: Integracion continua (GitHub Actions)

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Ninguna (workflow independiente del codigo).

- [ ] **Step 1: Crear el workflow**

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle.kts', 'gradle/libs.versions.toml') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: gradlew check
        run: ./gradlew check --no-daemon
```

No hay paso de "escribir test primero": este workflow no tiene logica propia que probar por unidad, su verificacion es que se ejecute correctamente en GitHub.

- [ ] **Step 2: Verificar sintacticamente**

Run: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"` (o cualquier validador YAML disponible) para confirmar que el fichero es YAML valido antes de subirlo.
Expected: sin errores.

- [ ] **Step 3: Commitear**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: ejecutar ./gradlew check en GitHub Actions en cada push/PR"
```

(Nota: los tests instrumentados de Compose no corren en este workflow porque necesitan un emulador Android, que no esta configurado aqui; el job cubre compilacion + ktlint + detekt + lint + los 160 tests unitarios.)

---

## Seccion 2 — Seguridad

### Task 4: Validacion de tareas en el servidor

**Files:**
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/routes/TaskRoutes.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/dto/Dtos.kt`
- Modify: `server/src/test/kotlin/com/daviddelgado/agenda/server/api/TaskRoutesTest.kt`

**Interfaces:**
- Produces: `ErrorResponse` con mensaje descriptivo y `400 Bad Request` cuando `TaskDto` no es valido.

- [ ] **Step 1: Escribir los tests (en rojo)**

Añadir a `server/src/test/kotlin/com/daviddelgado/agenda/server/api/TaskRoutesTest.kt`:

```kotlin
    @Test
    fun crearUnaTareaConTituloVacioResponde400() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken

            val response =
                client.post("/tasks") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(tareaDeEjemplo(id = "tarea-1", title = "   "))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun crearUnaTareaConTituloDemasiadoLargoResponde400() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken

            val response =
                client.post("/tasks") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(tareaDeEjemplo(id = "tarea-1", title = "a".repeat(201)))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun crearUnaTareaConFechaInvalidaResponde400() =
        withApi { client ->
            val token = client.registrarUsuario().accessToken

            val response =
                client.post("/tasks") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(tareaDeEjemplo(id = "tarea-1").copy(date = "no-es-una-fecha"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
```

Run: `./gradlew :server:test --tests "*.TaskRoutesTest"`
Expected: FAIL — hoy el servidor acepta cualquier `TaskDto` sin validar (título vacío o "no-es-una-fecha" provoca 500 o se guarda tal cual).

- [ ] **Step 2: Añadir la validacion**

Crear la validacion como funcion reutilizable. En `server/src/main/kotlin/com/daviddelgado/agenda/server/dto/Dtos.kt`, añadir al final del fichero:

```kotlin
private const val MAX_TITLE_LENGTH = 200
private const val MAX_DESCRIPTION_LENGTH = 2000

/** Devuelve el motivo por el que la tarea no es valida, o null si lo es. */
fun TaskDto.validationError(): String? {
    if (title.isBlank()) return "El titulo no puede estar vacio"
    if (title.length > MAX_TITLE_LENGTH) return "El titulo no puede superar $MAX_TITLE_LENGTH caracteres"
    if (description.length > MAX_DESCRIPTION_LENGTH) {
        return "La descripcion no puede superar $MAX_DESCRIPTION_LENGTH caracteres"
    }
    if (runCatching { java.time.LocalDate.parse(date) }.isFailure) return "Fecha invalida"
    if (time != null && runCatching { java.time.LocalTime.parse(time) }.isFailure) return "Hora invalida"
    return null
}
```

En `server/src/main/kotlin/com/daviddelgado/agenda/server/routes/TaskRoutes.kt`, importar `com.daviddelgado.agenda.server.dto.validationError` y validar en `post("/tasks")` y `put("/tasks/{id}")` antes de tocar el repositorio:

```kotlin
    post("/tasks") {
        val userId = call.requireUserId()
        val dto = call.receive<TaskDto>()
        dto.validationError()?.let { reason ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(reason))
            return@post
        }
        call.respond(HttpStatusCode.Created, taskRepository.create(userId, dto))
        TaskEventBroadcaster.notifyTasksChanged(userId)
    }

    put("/tasks/{id}") {
        val userId = call.requireUserId()
        val taskId = call.parameters.getOrFail("id")
        val dto = call.receive<TaskDto>()
        dto.validationError()?.let { reason ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(reason))
            return@put
        }

        if (!taskRepository.update(userId, taskId, dto)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Tarea no encontrada"))
            return@put
        }
        call.respond(dto.copy(id = taskId))
        TaskEventBroadcaster.notifyTasksChanged(userId)
    }
```

- [ ] **Step 3: Verificar y commitear**

Run: `./gradlew :server:test`
Expected: BUILD SUCCESSFUL, todos los tests existentes + los 3 nuevos en verde.

```bash
git add server/src/main/kotlin/com/daviddelgado/agenda/server/dto/Dtos.kt server/src/main/kotlin/com/daviddelgado/agenda/server/routes/TaskRoutes.kt server/src/test/kotlin/com/daviddelgado/agenda/server/api/TaskRoutesTest.kt
git commit -m "fix: el servidor valida titulo/fecha/hora de una tarea antes de guardarla"
```

---

### Task 5: Validar formato de email en el registro

**Files:**
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt`
- Modify: `server/src/test/kotlin/com/daviddelgado/agenda/server/api/AuthRoutesTest.kt`

**Interfaces:**
- Ninguna nueva; refuerza la validacion ya existente en `POST /auth/register`.

- [ ] **Step 1: Escribir el test (en rojo)**

Añadir a `server/src/test/kotlin/com/daviddelgado/agenda/server/api/AuthRoutesTest.kt`:

```kotlin
    @Test
    fun registrarseConEmailSinArrobaResponde400() =
        withApi { client ->
            val response =
                client.post("/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(RegisterRequest(name = "David", email = "no-es-un-email", password = "secreta123"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
```

(Añadir los imports que falten: `com.daviddelgado.agenda.server.dto.RegisterRequest`, `io.ktor.client.request.post`, `io.ktor.client.request.setBody`, `io.ktor.http.ContentType`, `io.ktor.http.contentType`, `io.ktor.http.HttpStatusCode`, `kotlin.test.assertEquals` — solo los que no esten ya importados en el fichero.)

Run: `./gradlew :server:test --tests "*.AuthRoutesTest"`
Expected: FAIL — hoy `AuthRoutes.kt` solo comprueba `isBlank()`, un email sin "@" pasa la validacion.

- [ ] **Step 2: Reforzar la validacion**

En `server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt`, cambiar la condicion de `post("/auth/register")`:

```kotlin
    post("/auth/register") {
        val request = call.receive<RegisterRequest>()

        val emailValido = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(request.email)
        if (request.name.isBlank() || !emailValido || request.password.length < 6) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Datos invalidos: email valido y contrasena minima 6 caracteres"))
            return@post
        }
```

- [ ] **Step 3: Verificar y commitear**

Run: `./gradlew :server:test --tests "*.AuthRoutesTest"`
Expected: BUILD SUCCESSFUL.

```bash
git add server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt server/src/test/kotlin/com/daviddelgado/agenda/server/api/AuthRoutesTest.kt
git commit -m "fix: valida formato de email al registrarse, no solo que no este vacio"
```

---

### Task 6: Rate limiting en login/registro

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `server/build.gradle.kts`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt`
- Modify: `server/src/test/kotlin/com/daviddelgado/agenda/server/api/AuthRoutesTest.kt`

**Interfaces:**
- Produces: rutas `/auth/login` y `/auth/register` dentro de un `RateLimit` de Ktor con nombre `"auth"`.

- [ ] **Step 1: Añadir el plugin de rate limiting**

En `gradle/libs.versions.toml`, `[libraries]` (junto a los demas `ktor-server-*`):

```toml
ktor-server-rateLimit = { group = "io.ktor", name = "ktor-server-rate-limit", version.ref = "ktor" }
```

En `server/build.gradle.kts`, junto a `implementation(libs.ktor.server.statusPages)`:

```kotlin
implementation(libs.ktor.server.rateLimit)
```

- [ ] **Step 2: Escribir el test (en rojo)**

Añadir a `server/src/test/kotlin/com/daviddelgado/agenda/server/api/AuthRoutesTest.kt`:

```kotlin
    @Test
    fun trasVariosLoginsFallidosSeguidosElServidorResponde429() =
        withApi { client ->
            client.registrarUsuario(email = "limite@test.com", password = "secreta123")

            val intentoQueDeberiaBloquear =
                (1..15).map {
                    client.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(LoginRequest(email = "limite@test.com", password = "incorrecta"))
                    }
                }.last()

            assertEquals(HttpStatusCode.TooManyRequests, intentoQueDeberiaBloquear.status)
        }
```

(Import que falta: `com.daviddelgado.agenda.server.dto.LoginRequest` si no esta ya.)

Run: `./gradlew :server:test --tests "*.AuthRoutesTest"`
Expected: FAIL — hoy no hay ningun limite, los 15 intentos responden `401 Unauthorized`, ninguno `429`.

- [ ] **Step 3: Instalar el plugin y aplicarlo a las rutas de auth**

En `server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt`, añadir el import `io.ktor.server.plugins.ratelimit.RateLimit` y `io.ktor.server.plugins.ratelimit.RateLimitName` y, junto a `install(CORS) { ... }`:

```kotlin
    install(RateLimit) {
        register(RateLimitName("auth")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
        }
    }
```

(Añadir `import kotlin.time.Duration.Companion.seconds`.)

En `server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt`, envolver `login` y `register` (no `refresh`, que ya requiere conocer un refresh token valido) en el rate limiter. Importar `io.ktor.server.plugins.ratelimit.RateLimitName` y `io.ktor.server.plugins.ratelimit.rateLimit`, y cambiar la firma de la funcion:

```kotlin
fun Route.authRoutes(
    userRepository: UserRepository,
    jwtConfig: JwtConfig,
) {
    rateLimit(RateLimitName("auth")) {
        post("/auth/register") {
            // ... (sin cambios en el cuerpo)
        }

        post("/auth/login") {
            // ... (sin cambios en el cuerpo)
        }
    }

    post("/auth/refresh") {
        // ... (sin cambios, queda fuera del rate limit)
    }
}
```

- [ ] **Step 4: Verificar y commitear**

Run: `./gradlew :server:test --tests "*.AuthRoutesTest"`
Expected: BUILD SUCCESSFUL. Revisa tambien que el resto de tests de `:server` sigan en verde (`./gradlew :server:test`), ya que otros tests tambien llaman a `/auth/register`/`/auth/login` repetidas veces dentro del mismo `withApi` y podrian toparse con el limite de 10/60s si el test hace mas de 10 llamadas seguidas — si algun test existente falla por eso, sube el `limit` a 30 en vez de bajarlo (no quites el rate limit).

```bash
git add gradle/libs.versions.toml server/build.gradle.kts server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt server/src/main/kotlin/com/daviddelgado/agenda/server/routes/AuthRoutes.kt server/src/test/kotlin/com/daviddelgado/agenda/server/api/AuthRoutesTest.kt
git commit -m "feat: rate limiting en /auth/login y /auth/register contra fuerza bruta"
```

---

## Seccion 1 — Funcional

### Task 7: Logica pura de "cuando toca un recordatorio" (`ReminderScheduler`)

**Files:**
- Create: `server/src/main/kotlin/com/daviddelgado/agenda/server/reminder/ReminderScheduler.kt`
- Create: `server/src/test/kotlin/com/daviddelgado/agenda/server/reminder/ReminderSchedulerTest.kt`

**Interfaces:**
- Produces: `ReminderScheduler.isDue(reminderFrequency: String, taskDate: LocalDate, taskTime: LocalTime?, isCompleted: Boolean, lastSentAt: Instant?, now: Instant): Boolean` — lo consume `Task 9`.
- No depende de la base de datos ni de red: funcion pura, JVM (`java.time`), vive en `:server` porque este modulo no depende de `:core:domain`.

- [ ] **Step 1: Escribir los tests (en rojo)**

`server/src/test/kotlin/com/daviddelgado/agenda/server/reminder/ReminderSchedulerTest.kt`:

```kotlin
package com.daviddelgado.agenda.server.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderSchedulerTest {
    private val fecha = LocalDate.of(2026, 9, 20)
    private val hora = LocalTime.of(9, 0)
    private val momentoDeLaTarea = fecha.atTime(hora).toInstant(ZoneOffset.UTC)

    @Test
    fun sinRecordatorioNuncaEstaListo() {
        assertFalse(
            ReminderScheduler.isDue("NINGUNO", fecha, hora, isCompleted = false, lastSentAt = null, now = momentoDeLaTarea),
        )
    }

    @Test
    fun unaTareaCompletadaNuncaEstaLista() {
        assertFalse(
            ReminderScheduler.isDue("DIARIO", fecha, hora, isCompleted = true, lastSentAt = null, now = momentoDeLaTarea),
        )
    }

    @Test
    fun antesDeLaHoraDeLaTareaNoEstaListo() {
        assertFalse(
            ReminderScheduler.isDue(
                "UNA_VEZ",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = null,
                now = momentoDeLaTarea.minusSeconds(60),
            ),
        )
    }

    @Test
    fun enSuHoraLaPrimeraVezEstaListo() {
        assertTrue(
            ReminderScheduler.isDue("UNA_VEZ", fecha, hora, isCompleted = false, lastSentAt = null, now = momentoDeLaTarea),
        )
    }

    @Test
    fun unaVezNoSeRepiteTrasEnviarse() {
        assertFalse(
            ReminderScheduler.isDue(
                "UNA_VEZ",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = momentoDeLaTarea.plusSeconds(86_400),
            ),
        )
    }

    @Test
    fun personalizadoSeComportaComoUnaVezPorAhora() {
        assertFalse(
            ReminderScheduler.isDue(
                "PERSONALIZADO",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = momentoDeLaTarea.plusSeconds(86_400),
            ),
        )
    }

    @Test
    fun diarioSeRepiteExactamenteCadaDia() {
        val unDiaDespues = momentoDeLaTarea.plusSeconds(86_400)
        val docHorasDespues = momentoDeLaTarea.plusSeconds(43_200)

        assertTrue(
            ReminderScheduler.isDue("DIARIO", fecha, hora, isCompleted = false, lastSentAt = momentoDeLaTarea, now = unDiaDespues),
        )
        assertFalse(
            ReminderScheduler.isDue(
                "DIARIO",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = docHorasDespues,
            ),
        )
    }

    @Test
    fun semanalSeRepiteCadaSieteDias() {
        val seisDiasDespues = momentoDeLaTarea.plusSeconds(6 * 86_400L)
        val sieteDiasDespues = momentoDeLaTarea.plusSeconds(7 * 86_400L)

        assertFalse(
            ReminderScheduler.isDue(
                "SEMANAL",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = seisDiasDespues,
            ),
        )
        assertTrue(
            ReminderScheduler.isDue(
                "SEMANAL",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = sieteDiasDespues,
            ),
        )
    }

    @Test
    fun mensualSeRepiteUnMesDespues() {
        val unMesDespues = fecha.plusMonths(1).atTime(hora).toInstant(ZoneOffset.UTC)

        assertTrue(
            ReminderScheduler.isDue(
                "MENSUAL",
                fecha,
                hora,
                isCompleted = false,
                lastSentAt = momentoDeLaTarea,
                now = unMesDespues,
            ),
        )
    }

    @Test
    fun sinHoraUsaMedianocheComoMomentoDeAviso() {
        val medianoche = fecha.atTime(LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC)

        assertTrue(
            ReminderScheduler.isDue("UNA_VEZ", fecha, taskTime = null, isCompleted = false, lastSentAt = null, now = medianoche),
        )
    }
}
```

Run: `./gradlew :server:test --tests "*.ReminderSchedulerTest"`
Expected: FAIL — no existe `ReminderScheduler`, error de compilacion.

- [ ] **Step 2: Implementar `ReminderScheduler`**

`server/src/main/kotlin/com/daviddelgado/agenda/server/reminder/ReminderScheduler.kt`:

```kotlin
package com.daviddelgado.agenda.server.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Decide si una tarea necesita un recordatorio push ahora mismo. Logica pura (sin red ni
 * base de datos) para poder probarla con TDD normal; [runReminderLoop] (Task 9) es quien la
 * usa de verdad contra la base de datos y Firebase.
 *
 * El servidor no depende de `:core:domain` (modulo solo cliente), asi que aqui se repite el
 * catalogo cerrado de `ReminderFrequency` como `String` ("NINGUNO", "UNA_VEZ", "DIARIO",
 * "SEMANAL", "MENSUAL", "PERSONALIZADO"), tal cual se guarda en `Tasks.reminderFrequency`. Un
 * valor desconocido no dispara nada.
 */
object ReminderScheduler {
    fun isDue(
        reminderFrequency: String,
        taskDate: LocalDate,
        taskTime: LocalTime?,
        isCompleted: Boolean,
        lastSentAt: Instant?,
        now: Instant,
    ): Boolean {
        if (isCompleted || reminderFrequency == "NINGUNO") return false
        val proximoAviso = nextDueInstant(reminderFrequency, taskDate, taskTime, lastSentAt) ?: return false
        return !now.isBefore(proximoAviso)
    }

    private fun nextDueInstant(
        reminderFrequency: String,
        taskDate: LocalDate,
        taskTime: LocalTime?,
        lastSentAt: Instant?,
    ): Instant? {
        val horaAviso = taskTime ?: LocalTime.MIDNIGHT
        if (lastSentAt == null) return LocalDateTime.of(taskDate, horaAviso).toInstant(ZoneOffset.UTC)

        val ultimaFecha = lastSentAt.atZone(ZoneOffset.UTC).toLocalDate()
        val siguienteFecha =
            when (reminderFrequency) {
                "DIARIO" -> ultimaFecha.plusDays(1)
                "SEMANAL" -> ultimaFecha.plusWeeks(1)
                "MENSUAL" -> ultimaFecha.plusMonths(1)
                // UNA_VEZ / PERSONALIZADO: un unico aviso; sin un campo propio de intervalo
                // (ver ESTADO_PROYECTO.md), de momento no se repiten tras el primer envio.
                else -> return null
            }
        return LocalDateTime.of(siguienteFecha, horaAviso).toInstant(ZoneOffset.UTC)
    }
}
```

- [ ] **Step 3: Verificar y commitear**

Run: `./gradlew :server:test --tests "*.ReminderSchedulerTest"`
Expected: BUILD SUCCESSFUL, 11/11 tests en verde.

```bash
git add server/src/main/kotlin/com/daviddelgado/agenda/server/reminder/ReminderScheduler.kt server/src/test/kotlin/com/daviddelgado/agenda/server/reminder/ReminderSchedulerTest.kt
git commit -m "feat: logica pura de cuando toca enviar un recordatorio de tarea"
```

---

### Task 8: Registro de tokens FCM por usuario

**Files:**
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/db/DatabaseFactory.kt`
- Create: `server/src/main/kotlin/com/daviddelgado/agenda/server/repository/FcmTokenRepository.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/dto/Dtos.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/routes/UserRoutes.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt`
- Create: `server/src/test/kotlin/com/daviddelgado/agenda/server/api/FcmTokenRoutesTest.kt`

**Interfaces:**
- Consumes: `FcmTokens` (tabla nueva), `Users.id` (existente).
- Produces: `POST /users/me/fcm-token` (autenticado) y `FcmTokenRepository.tokensForUser(userId): List<String>` — lo consume `Task 9`.

- [ ] **Step 1: Escribir el test de la ruta (en rojo)**

`server/src/test/kotlin/com/daviddelgado/agenda/server/api/FcmTokenRoutesTest.kt`:

```kotlin
package com.daviddelgado.agenda.server.api

import com.daviddelgado.agenda.server.dto.FcmTokenRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals

class FcmTokenRoutesTest {
    @Test
    fun sinTokenDeSesionResponde401() =
        withApi { client ->
            val response =
                client.post("/users/me/fcm-token") {
                    contentType(ContentType.Application.Json)
                    setBody(FcmTokenRequest("token-de-prueba"))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun registrarUnTokenFcmResponde204() =
        withApi { client ->
            val accessToken = client.registrarUsuario().accessToken

            val response =
                client.post("/users/me/fcm-token") {
                    bearerAuth(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(FcmTokenRequest("token-de-prueba"))
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
        }

    @Test
    fun registrarElMismoTokenDosVecesNoFalla() =
        withApi { client ->
            val accessToken = client.registrarUsuario().accessToken

            client.post("/users/me/fcm-token") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(FcmTokenRequest("token-repetido"))
            }
            val segunda =
                client.post("/users/me/fcm-token") {
                    bearerAuth(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(FcmTokenRequest("token-repetido"))
                }

            assertEquals(HttpStatusCode.NoContent, segunda.status)
        }
}
```

Run: `./gradlew :server:test --tests "*.FcmTokenRoutesTest"`
Expected: FAIL — no existe `FcmTokenRequest` ni la ruta.

- [ ] **Step 2: Tabla, DTO y repositorio**

En `server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt`, añadir al final:

```kotlin
/** Un token de dispositivo FCM por el que se le puede mandar un push a este usuario. */
object FcmTokens : Table("fcm_tokens") {
    val userId =
        varchar("user_id", 36)
            .references(Users.id, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val token = varchar("token", 255)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId, token)
}
```

En `server/src/main/kotlin/com/daviddelgado/agenda/server/db/DatabaseFactory.kt`, cambiar:

```kotlin
SchemaUtils.createMissingTablesAndColumns(Users, Tasks, FcmTokens)
```

En `server/src/main/kotlin/com/daviddelgado/agenda/server/dto/Dtos.kt`, añadir:

```kotlin
@Serializable
data class FcmTokenRequest(val token: String)
```

`server/src/main/kotlin/com/daviddelgado/agenda/server/repository/FcmTokenRepository.kt`:

```kotlin
package com.daviddelgado.agenda.server.repository

import com.daviddelgado.agenda.server.db.FcmTokens
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class FcmTokenRepository {
    fun upsert(
        userId: String,
        token: String,
    ) {
        transaction {
            val yaExiste =
                FcmTokens.selectAll().where { (FcmTokens.userId eq userId) and (FcmTokens.token eq token) }
                    .empty().not()
            if (yaExiste) {
                FcmTokens.update({ (FcmTokens.userId eq userId) and (FcmTokens.token eq token) }) {
                    it[updatedAt] = Instant.now()
                }
            } else {
                FcmTokens.insert {
                    it[FcmTokens.userId] = userId
                    it[FcmTokens.token] = token
                    it[updatedAt] = Instant.now()
                }
            }
        }
    }

    fun tokensForUser(userId: String): List<String> =
        transaction {
            FcmTokens.selectAll().where { FcmTokens.userId eq userId }.map { it[FcmTokens.token] }
        }
}
```

- [ ] **Step 3: La ruta**

En `server/src/main/kotlin/com/daviddelgado/agenda/server/routes/UserRoutes.kt`, cambiar la firma y añadir la ruta:

```kotlin
package com.daviddelgado.agenda.server.routes

import com.daviddelgado.agenda.server.dto.ErrorResponse
import com.daviddelgado.agenda.server.dto.FcmTokenRequest
import com.daviddelgado.agenda.server.dto.UserResponse
import com.daviddelgado.agenda.server.repository.FcmTokenRepository
import com.daviddelgado.agenda.server.repository.UserRepository
import com.daviddelgado.agenda.server.security.requireUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.userRoutes(
    userRepository: UserRepository,
    fcmTokenRepository: FcmTokenRepository,
) {
    authenticate("auth-jwt") {
        get("/users/me") {
            val user = userRepository.findById(call.requireUserId())

            if (user == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Usuario no encontrado"))
                return@get
            }
            call.respond(UserResponse(user.id, user.name, user.email))
        }

        // Borrado conjunto: elimina la cuenta y, en cascada (ON DELETE CASCADE), todas sus tareas.
        delete("/users/me") {
            userRepository.delete(call.requireUserId())
            call.respond(HttpStatusCode.NoContent)
        }

        // Recordatorios push (Task 9): el cliente Android manda aqui su token FCM tras el
        // login y cada vez que Firebase se lo renueva (ver AgendaFirebaseMessagingService).
        post("/users/me/fcm-token") {
            val request = call.receive<FcmTokenRequest>()
            fcmTokenRepository.upsert(call.requireUserId(), request.token)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
```

En `server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt`, instanciar el repositorio y pasarlo:

```kotlin
    val userRepository = UserRepository()
    val taskRepository = TaskRepository()
    val fcmTokenRepository = FcmTokenRepository()
```

```kotlin
    routing {
        authRoutes(userRepository, jwtConfig)
        userRoutes(userRepository, fcmTokenRepository)
        taskRoutes(taskRepository)
    }
```

(Import que falta: `com.daviddelgado.agenda.server.repository.FcmTokenRepository`.)

- [ ] **Step 4: Verificar y commitear**

Run: `./gradlew :server:test`
Expected: BUILD SUCCESSFUL (todos los tests existentes + los 3 nuevos).

```bash
git add server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt server/src/main/kotlin/com/daviddelgado/agenda/server/db/DatabaseFactory.kt server/src/main/kotlin/com/daviddelgado/agenda/server/repository/FcmTokenRepository.kt server/src/main/kotlin/com/daviddelgado/agenda/server/dto/Dtos.kt server/src/main/kotlin/com/daviddelgado/agenda/server/routes/UserRoutes.kt server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt server/src/test/kotlin/com/daviddelgado/agenda/server/api/FcmTokenRoutesTest.kt
git commit -m "feat: endpoint para registrar el token FCM de un dispositivo"
```

---

### Task 9: Envio real de push (Firebase Admin SDK) + bucle de recordatorios

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `server/build.gradle.kts`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/repository/TaskRepository.kt`
- Create: `server/src/main/kotlin/com/daviddelgado/agenda/server/push/PushSender.kt`
- Create: `server/src/main/kotlin/com/daviddelgado/agenda/server/reminder/ReminderJob.kt`
- Modify: `server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt`
- Create: `server/src/test/kotlin/com/daviddelgado/agenda/server/reminder/ReminderJobTest.kt`
- Create: `server/src/test/kotlin/com/daviddelgado/agenda/server/push/PushSenderTest.kt`

**Interfaces:**
- Consumes: `ReminderScheduler.isDue(...)` (Task 7), `FcmTokenRepository.tokensForUser(...)` (Task 8).
- Produces: `PushSender` (interfaz), `providePushSender(): PushSender`, `checkAndSendReminders(taskRepository, fcmTokenRepository, pushSender, now)`, `runReminderLoop(...)` — arrancado solo desde `main()`.

- [ ] **Step 1: Añadir el Firebase Admin SDK**

En `gradle/libs.versions.toml`, `[versions]`:

```toml
firebaseAdmin = "9.4.1"
```

`[libraries]`:

```toml
firebase-admin = { group = "com.google.firebase", name = "firebase-admin", version.ref = "firebaseAdmin" }
```

En `server/build.gradle.kts`:

```kotlin
implementation(libs.firebase.admin)
```

- [ ] **Step 2: Columna `last_reminder_sent_at` + consulta de candidatos**

En `server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt`, dentro de `object Tasks`, añadir:

```kotlin
    val lastReminderSentAt = timestamp("last_reminder_sent_at").nullable()
```

En `server/src/main/kotlin/com/daviddelgado/agenda/server/repository/TaskRepository.kt`, añadir el modelo y los dos metodos nuevos (junto al resto de la clase, con los imports `java.time.LocalDate` y `java.time.LocalTime` ya presentes):

```kotlin
/** Lo minimo que necesita [com.daviddelgado.agenda.server.reminder.ReminderJob] de una tarea. */
data class ReminderCandidate(
    val taskId: String,
    val userId: String,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val reminderFrequency: String,
    val isCompleted: Boolean,
    val lastReminderSentAt: Instant?,
)
```

```kotlin
    /** Tareas no completadas con un recordatorio configurado; [ReminderScheduler] decide cuales tocan ya. */
    fun tasksPendingReminderCheck(): List<ReminderCandidate> =
        transaction {
            Tasks.selectAll()
                .where { (Tasks.isCompleted eq false) and (Tasks.reminderFrequency neq "NINGUNO") }
                .map {
                    ReminderCandidate(
                        taskId = it[Tasks.id],
                        userId = it[Tasks.userId],
                        title = it[Tasks.title],
                        date = it[Tasks.date],
                        time = it[Tasks.time],
                        reminderFrequency = it[Tasks.reminderFrequency],
                        isCompleted = it[Tasks.isCompleted],
                        lastReminderSentAt = it[Tasks.lastReminderSentAt],
                    )
                }
        }

    fun markReminderSent(
        taskId: String,
        at: Instant,
    ) {
        transaction {
            Tasks.update({ Tasks.id eq taskId }) { it[lastReminderSentAt] = at }
        }
    }
```

(Import que falta en `TaskRepository.kt`: `org.jetbrains.exposed.sql.SqlExpressionBuilder.neq`.)

- [ ] **Step 3: `PushSender` — escribir el test (en rojo) y luego implementar**

`server/src/test/kotlin/com/daviddelgado/agenda/server/push/PushSenderTest.kt`:

```kotlin
package com.daviddelgado.agenda.server.push

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame

class PushSenderTest {
    @Test
    fun sinVariableDeEntornoUsaElEnvioSinEfecto() {
        assertSame(NoOpPushSender, providePushSender(serviceAccountJsonPath = null))
    }

    @Test
    fun elEnvioSinEfectoSiempreDevuelveFalse() {
        assertFalse(NoOpPushSender.send(token = "cualquiera", title = "t", body = "b"))
    }
}
```

Run: `./gradlew :server:test --tests "*.PushSenderTest"`
Expected: FAIL — no existe el paquete `push`.

`server/src/main/kotlin/com/daviddelgado/agenda/server/push/PushSender.kt`:

```kotlin
package com.daviddelgado.agenda.server.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import java.io.FileInputStream

fun interface PushSender {
    /** @return true si Firebase acepto el envio. */
    fun send(
        token: String,
        title: String,
        body: String,
    ): Boolean
}

/**
 * Envio real via Firebase Cloud Messaging. Necesita un proyecto Firebase real y su fichero de
 * credenciales de cuenta de servicio (Firebase Console > Configuracion del proyecto > Cuentas
 * de servicio > Generar nueva clave privada), cuya ruta se pasa en la variable de entorno
 * AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON. Sin ella, [providePushSender] usa [NoOpPushSender]:
 * la app entera sigue funcionando, solo que sin recordatorios push de verdad, igual que
 * `ProductionConfig` con los pines de certificado.
 */
class FirebasePushSender(serviceAccountJsonPath: String) : PushSender {
    private val messaging: FirebaseMessaging

    init {
        val credentials = FileInputStream(serviceAccountJsonPath).use { GoogleCredentials.fromStream(it) }
        val app =
            FirebaseApp.getApps().firstOrNull()
                ?: FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build())
        messaging = FirebaseMessaging.getInstance(app)
    }

    override fun send(
        token: String,
        title: String,
        body: String,
    ): Boolean =
        runCatching {
            messaging.send(
                Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .build(),
            )
        }.onFailure { logger.error("Fallo enviando push a $token", it) }.isSuccess

    private companion object {
        val logger = LoggerFactory.getLogger(FirebasePushSender::class.java)
    }
}

object NoOpPushSender : PushSender {
    private val logger = LoggerFactory.getLogger(NoOpPushSender::class.java)
    private var avisado = false

    override fun send(
        token: String,
        title: String,
        body: String,
    ): Boolean {
        if (!avisado) {
            logger.warn(
                "AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON no configurado: los recordatorios push " +
                    "estan deshabilitados (ver FirebasePushSender).",
            )
            avisado = true
        }
        return false
    }
}

/** @param serviceAccountJsonPath normalmente `System.getenv("AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON")`. */
fun providePushSender(serviceAccountJsonPath: String?): PushSender =
    serviceAccountJsonPath
        ?.let { runCatching { FirebasePushSender(it) }.getOrNull() }
        ?: NoOpPushSender
```

Run: `./gradlew :server:test --tests "*.PushSenderTest"`
Expected: PASS.

- [ ] **Step 4: `ReminderJob` — escribir el test (en rojo) y luego implementar**

`server/src/test/kotlin/com/daviddelgado/agenda/server/reminder/ReminderJobTest.kt`:

```kotlin
package com.daviddelgado.agenda.server.reminder

import com.daviddelgado.agenda.server.db.DatabaseFactory
import com.daviddelgado.agenda.server.dto.TaskDto
import com.daviddelgado.agenda.server.push.PushSender
import com.daviddelgado.agenda.server.repository.FcmTokenRepository
import com.daviddelgado.agenda.server.repository.TaskRepository
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderJobTest {
    private val taskRepository = TaskRepository()
    private val fcmTokenRepository = FcmTokenRepository()

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("jdbc:h2:mem:reminder-job-test-${Random.nextLong()};DB_CLOSE_DELAY=-1")
    }

    @Test
    fun mandaUnPushACadaTokenDeUnaTareaListaYMarcaElEnvio() {
        val userId = crearUsuarioConTarea(reminderFrequency = "UNA_VEZ", date = "2026-09-20", time = "09:00")
        fcmTokenRepository.upsert(userId, "token-1")
        fcmTokenRepository.upsert(userId, "token-2")
        val enviosRecibidos = mutableListOf<String>()
        val pushSender = PushSender { token, _, _ -> enviosRecibidos.add(token); true }
        val ahora = Instant.parse("2026-09-20T09:00:00Z")

        checkAndSendReminders(taskRepository, fcmTokenRepository, pushSender, now = ahora)

        assertEquals(setOf("token-1", "token-2"), enviosRecibidos.toSet())
        val actualizada = taskRepository.tasksPendingReminderCheck().single()
        assertEquals(ahora, actualizada.lastReminderSentAt)
    }

    @Test
    fun noMandaNadaSiAunNoEsLaHora() {
        crearUsuarioConTarea(reminderFrequency = "UNA_VEZ", date = "2026-09-20", time = "09:00")
        var seLlamo = false
        val pushSender = PushSender { _, _, _ -> seLlamo = true; true }

        checkAndSendReminders(
            taskRepository,
            fcmTokenRepository,
            pushSender,
            now = Instant.parse("2026-09-20T08:00:00Z"),
        )

        assertTrue(!seLlamo)
    }

    private fun crearUsuarioConTarea(
        reminderFrequency: String,
        date: String,
        time: String,
    ): String {
        val userRepository = com.daviddelgado.agenda.server.repository.UserRepository()
        val user = userRepository.create("David", "user-${Random.nextLong()}@test.com", "hash")
        taskRepository.create(
            user.id,
            TaskDto(
                id = "",
                title = "Tarea con recordatorio",
                date = date,
                time = time,
                category = "OTRO",
                priority = "MEDIA",
                reminderFrequency = reminderFrequency,
            ),
        )
        return user.id
    }
}
```

Run: `./gradlew :server:test --tests "*.ReminderJobTest"`
Expected: FAIL — no existe `checkAndSendReminders`.

`server/src/main/kotlin/com/daviddelgado/agenda/server/reminder/ReminderJob.kt`:

```kotlin
package com.daviddelgado.agenda.server.reminder

import com.daviddelgado.agenda.server.push.PushSender
import com.daviddelgado.agenda.server.repository.FcmTokenRepository
import com.daviddelgado.agenda.server.repository.TaskRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.coroutines.coroutineContext

private val logger = LoggerFactory.getLogger("ReminderJob")

/** Cada cuanto se revisa si hay recordatorios pendientes de enviar. */
const val REMINDER_CHECK_INTERVAL_MILLIS = 60_000L

/**
 * Bucle en segundo plano (arrancado solo desde `main()`, nunca en los tests que llaman a
 * `agendaModule` directamente) que revisa periodicamente que tareas necesitan un recordatorio
 * push ahora mismo (ver [ReminderScheduler]) y lo manda por [PushSender] a cada token FCM
 * registrado del usuario dueño de la tarea.
 */
suspend fun runReminderLoop(
    taskRepository: TaskRepository,
    fcmTokenRepository: FcmTokenRepository,
    pushSender: PushSender,
) {
    while (coroutineContext.isActive) {
        runCatching { checkAndSendReminders(taskRepository, fcmTokenRepository, pushSender) }
            .onFailure { logger.error("Fallo revisando recordatorios", it) }
        delay(REMINDER_CHECK_INTERVAL_MILLIS)
    }
}

/** Extraido de [runReminderLoop] para poder probarlo sin esperar al bucle real. */
fun checkAndSendReminders(
    taskRepository: TaskRepository,
    fcmTokenRepository: FcmTokenRepository,
    pushSender: PushSender,
    now: Instant = Instant.now(),
) {
    taskRepository.tasksPendingReminderCheck()
        .filter {
            ReminderScheduler.isDue(
                reminderFrequency = it.reminderFrequency,
                taskDate = it.date,
                taskTime = it.time,
                isCompleted = it.isCompleted,
                lastSentAt = it.lastReminderSentAt,
                now = now,
            )
        }
        .forEach { candidate ->
            fcmTokenRepository.tokensForUser(candidate.userId).forEach { token ->
                pushSender.send(token = token, title = candidate.title, body = "Recordatorio de tarea")
            }
            taskRepository.markReminderSent(candidate.taskId, now)
        }
}
```

- [ ] **Step 5: Arrancar el bucle solo en `main()` (nunca en los tests)**

En `server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt`, cambiar `main()`:

```kotlin
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = { agendaModule() })
        .start(wait = false)
    startReminderLoop()
    Thread.currentThread().join()
}

private fun startReminderLoop() {
    val pushSender = providePushSender(System.getenv("AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON"))
    kotlinx.coroutines.GlobalScope.launch {
        runReminderLoop(TaskRepository(), FcmTokenRepository(), pushSender)
    }
}
```

(Imports que faltan: `com.daviddelgado.agenda.server.push.providePushSender`, `com.daviddelgado.agenda.server.reminder.runReminderLoop`, `com.daviddelgado.agenda.server.repository.FcmTokenRepository`, `kotlinx.coroutines.GlobalScope`, `kotlinx.coroutines.launch`. `@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)` en `startReminderLoop` para silenciar el aviso de `GlobalScope`, justificado porque el bucle debe vivir mientras viva el proceso, igual que el propio servidor Netty.)

Este cambio de `start(wait = true)` a `start(wait = false)` + `Thread.currentThread().join()` es necesario para poder lanzar el bucle de recordatorios despues de arrancar el servidor sin bloquear; el comportamiento observable (el proceso sigue vivo indefinidamente) no cambia. Los tests siguen usando `agendaModule()` directamente via `testApplication` (`withApi`), que nunca llama a `main()`, asi que nunca arrancan el bucle real.

- [ ] **Step 6: Verificar y commitear**

Run: `./gradlew :server:test`
Expected: BUILD SUCCESSFUL (todos los tests de `:server`, incluidos los 4 nuevos).

```bash
git add gradle/libs.versions.toml server/build.gradle.kts server/src/main/kotlin/com/daviddelgado/agenda/server/db/Tables.kt server/src/main/kotlin/com/daviddelgado/agenda/server/repository/TaskRepository.kt server/src/main/kotlin/com/daviddelgado/agenda/server/push/PushSender.kt server/src/main/kotlin/com/daviddelgado/agenda/server/reminder/ReminderJob.kt server/src/main/kotlin/com/daviddelgado/agenda/server/Application.kt server/src/test/kotlin/com/daviddelgado/agenda/server/reminder/ReminderJobTest.kt server/src/test/kotlin/com/daviddelgado/agenda/server/push/PushSenderTest.kt
git commit -m "feat: envio real de recordatorios por push (Firebase) desde un bucle en segundo plano"
```

---

### Task 10: Cliente Android — recibir y registrar el token FCM

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (raiz)
- Modify: `androidApp/build.gradle.kts`
- Create: `androidApp/google-services.json`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Modify: `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/repository/Repositories.kt`
- Modify: `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/usecase/UseCases.kt`
- Modify: `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/di/DomainModule.kt`
- Modify: `core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/FakeAuthRepository.kt`
- Modify: `core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network/api/AuthApi.kt`
- Modify: `core/data/src/commonMain/kotlin/com/daviddelgado/agenda/data/auth/AuthRepositoryImpl.kt`
- Create: `androidApp/src/main/kotlin/com/daviddelgado/agenda/android/AgendaFirebaseMessagingService.kt`

**Interfaces:**
- Consumes: `POST /users/me/fcm-token` (Task 8).
- Produces: `RegisterFcmTokenUseCase(token: String)`, expuesto en `domainModule`, inyectado en `AgendaFirebaseMessagingService`.

- [ ] **Step 1: Dependencias de Firebase**

En `gradle/libs.versions.toml`, `[versions]`:

```toml
googleServices = "4.4.2"
firebaseBom = "33.5.1"
```

`[libraries]`:

```toml
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging-ktx" }
```

`[plugins]`:

```toml
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

En `build.gradle.kts` (raiz), añadir a la lista de plugins `apply false`:

```kotlin
alias(libs.plugins.google.services) apply false
```

En `androidApp/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
}
```

```kotlin
dependencies {
    implementation(projects.shared)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.network)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: `google-services.json` placeholder**

El plugin `google-services` necesita este fichero para generar recursos en tiempo de build, o la build falla. Como `ProductionConfig.kt`, se deja un placeholder con formato valido y **sin credenciales reales**; hay que sustituirlo por el que descargue Firebase Console (Configuracion del proyecto > Tus apps > Android, `applicationId` = `com.daviddelgado.agenda`) antes de que los push funcionen de verdad.

`androidApp/google-services.json`:

```json
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "agenda-placeholder",
    "storage_bucket": "agenda-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": {
          "package_name": "com.daviddelgado.agenda"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "PLACEHOLDER_API_KEY_SUSTITUIR_CON_EL_REAL_DE_FIREBASE"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
```

- [ ] **Step 3: Permiso y servicio en el manifest**

En `androidApp/src/main/AndroidManifest.xml`, añadir el permiso (Android 13+ exige pedirlo en tiempo de ejecucion; el manifest lo declara) y el servicio:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Agenda">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".AgendaFirebaseMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
```

- [ ] **Step 4: `RegisterFcmTokenUseCase` — TDD desde el repositorio de dominio**

Añadir a `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/repository/Repositories.kt`, dentro de `interface AuthRepository`:

```kotlin
    /** Manda al servidor el token FCM de este dispositivo para poder recibir recordatorios push. */
    suspend fun registerFcmToken(token: String): Result<Unit>
```

Añadir a `core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/FakeAuthRepository.kt` la implementacion fake (mismo patron que `deleteAccount`):

```kotlin
    var tokenRegistrado: String? = null

    override suspend fun registerFcmToken(token: String): Result<Unit> {
        tokenRegistrado = token
        return Result.success(Unit)
    }
```

Test (en rojo) en `core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/` — crear `RegisterFcmTokenUseCaseTest.kt`:

```kotlin
package com.daviddelgado.agenda.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterFcmTokenUseCaseTest {
    @Test
    fun delegaEnElRepositorio() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = RegisterFcmTokenUseCase(repository)

            useCase("token-de-prueba")

            assertEquals("token-de-prueba", repository.tokenRegistrado)
        }
}
```

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.RegisterFcmTokenUseCaseTest"`
Expected: FAIL — no existe `RegisterFcmTokenUseCase` ni `registerFcmToken` en el fake.

En `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/usecase/UseCases.kt`, añadir junto a `DeleteAccountUseCase`:

```kotlin
class RegisterFcmTokenUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(token: String): Result<Unit> = repository.registerFcmToken(token)
}
```

En `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/di/DomainModule.kt`, añadir el import y la linea:

```kotlin
import com.daviddelgado.agenda.domain.usecase.RegisterFcmTokenUseCase
```

```kotlin
        factory { RegisterFcmTokenUseCase(get()) }
```

- [ ] **Step 5: Implementacion real (API + repositorio)**

En `core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network/api/AuthApi.kt`, añadir el DTO de peticion (crear `FcmTokenRequest` en `core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network/dto/` junto a los demas DTOs de auth, con el mismo `@Serializable data class FcmTokenRequest(val token: String)` que en el servidor) y el metodo:

```kotlin
    suspend fun registerFcmToken(token: String) {
        apiCall {
            client.post("users/me/fcm-token") {
                contentType(ContentType.Application.Json)
                setBody(FcmTokenRequest(token))
            }
        }
    }
```

En `core/data/src/commonMain/kotlin/com/daviddelgado/agenda/data/auth/AuthRepositoryImpl.kt`, añadir:

```kotlin
    override suspend fun registerFcmToken(token: String): Result<Unit> =
        runCatching { authApi.registerFcmToken(token) }
```

Run: `./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: `AgendaFirebaseMessagingService`**

`androidApp/src/main/kotlin/com/daviddelgado/agenda/android/AgendaFirebaseMessagingService.kt`:

```kotlin
package com.daviddelgado.agenda.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.daviddelgado.agenda.domain.usecase.RegisterFcmTokenUseCase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private const val REMINDER_CHANNEL_ID = "agenda_reminders"
private const val REMINDER_NOTIFICATION_ID = 1001

/**
 * Recibe los recordatorios push que manda el servidor (ver ReminderJob en :server). Cada
 * mensaje trae ya el titulo/cuerpo listos (notificacion "display", no data-only), asi que
 * basta con mostrarla; el registro del token nuevo se hace en [onNewToken].
 */
class AgendaFirebaseMessagingService : FirebaseMessagingService() {
    private val registerFcmToken: RegisterFcmTokenUseCase by inject()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch { registerFcmToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: return
        val body = message.notification?.body ?: ""
        ensureChannel()
        val notification =
            NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(this).notify(REMINDER_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL_ID, "Recordatorios de tareas", NotificationManager.IMPORTANCE_HIGH),
        )
    }
}
```

No lleva test unitario propio: `FirebaseMessagingService` solo se puede instanciar en un contexto Android real, igual que `MainActivity` (que tampoco lo tiene). Su logica de negocio (que hacer con el token) ya esta cubierta por `RegisterFcmTokenUseCaseTest`.

- [ ] **Step 7: Compilar y commitear**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL (confirma que el `google-services.json` placeholder es valido para el plugin y que todo el grafo de dependencias nuevo compila).

```bash
git add gradle/libs.versions.toml build.gradle.kts androidApp/build.gradle.kts androidApp/google-services.json androidApp/src/main/AndroidManifest.xml core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/repository/Repositories.kt core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/usecase/UseCases.kt core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain/di/DomainModule.kt core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/FakeAuthRepository.kt core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain/usecase/RegisterFcmTokenUseCaseTest.kt core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network/api/AuthApi.kt core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network/dto/ core/data/src/commonMain/kotlin/com/daviddelgado/agenda/data/auth/AuthRepositoryImpl.kt androidApp/src/main/kotlin/com/daviddelgado/agenda/android/AgendaFirebaseMessagingService.kt
git commit -m "feat: cliente Android recibe recordatorios push y registra su token FCM"
```

- [ ] **Step 8: Registrar el token tras el login (engancharlo al flujo existente)**

Busca en `feature/login/src/commonMain/kotlin/com/daviddelgado/agenda/feature/login/LoginViewModel.kt` donde se llama a `LoginUseCase` con exito (tras un login OK) y en `shared` donde se llama a `RestoreSessionUseCase` al arrancar (splash). En ambos puntos, tras confirmar sesion valida, llamar a Firebase para pedir el token actual y registrarlo:

```kotlin
com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
    viewModelScope.launch { registerFcmTokenUseCase(token) }
}
```

Esto requiere inyectar `RegisterFcmTokenUseCase` en `LoginViewModel` (constructor + registro en `feature/login`'s modulo Koin) y en el punto de arranque que ya llama a `RestoreSessionUseCase`. Como el fichero exacto y el cableado de Koin de `feature/login` no se han inspeccionado en este plan, el ejecutor de esta tarea debe:
1. Leer `feature/login/src/commonMain/kotlin/com/daviddelgado/agenda/feature/login/LoginViewModel.kt` y su modulo Koin (`feature/login/.../LoginModule.kt` o equivalente) para seguir el mismo patron de inyeccion que los demas casos de uso ya usados alli.
2. Añadir un test de `LoginViewModelTest` (siguiendo el patron TDD ya usado en ese modulo) que compruebe que tras un login exitoso se llama a `registerFcmTokenUseCase` con el token que devuelva un fake de Firebase — como Firebase no es inyectable facilmente en comun, aceptar en su lugar probar solo que `LoginViewModel` expone/llama un punto de extension inyectado (p.ej. un `FcmTokenProvider` con `actual`/`expect` que en Android envuelve `FirebaseMessaging.getInstance().token`), y dejar el `expect`/`actual` de iOS como `TODO` documentado igual que el resto de la app.

Run: `./gradlew :feature:login:testDebugUnitTest`
Expected: BUILD SUCCESSFUL con el nuevo test en verde.

```bash
git add feature/login/
git commit -m "feat: registra el token FCM tras iniciar sesion"
```

---

### Task 11: Buscador y filtro por categoria en la lista de tareas

**Files:**
- Modify: `feature/tasks/src/commonMain/kotlin/com/daviddelgado/agenda/feature/tasks/TasksContract.kt`
- Modify: `feature/tasks/src/commonMain/kotlin/com/daviddelgado/agenda/feature/tasks/TasksViewModel.kt`
- Modify: `feature/tasks/src/commonMain/kotlin/com/daviddelgado/agenda/feature/tasks/TasksScreen.kt`
- Modify: `feature/tasks/src/commonTest/kotlin/com/daviddelgado/agenda/feature/tasks/TasksViewModelTest.kt`

**Interfaces:**
- Produces: `TasksState.visibleTasks: List<Task>` (computado), `TasksIntent.SearchQueryChanged(value: String)`, `TasksIntent.FilterCategoryChanged(value: TaskCategory?)`.

- [ ] **Step 1: Escribir los tests del reductor (en rojo)**

Añadir a `feature/tasks/src/commonTest/kotlin/com/daviddelgado/agenda/feature/tasks/TasksViewModelTest.kt` (siguiendo el patron ya usado en ese fichero: `viewModelCon(...)` construye el ViewModel con fakes):

```kotlin
    @Test
    fun elBuscadorFiltraPorTituloSinDistinguirMayusculas() =
        runTest {
            val tareas =
                listOf(
                    Task(id = "1", title = "Comprar pan", date = hoy),
                    Task(id = "2", title = "Gimnasio", date = hoy),
                )
            val viewModel = viewModelCon(FakeTaskRepository(tareas))

            viewModel.onIntent(TasksIntent.SearchQueryChanged("gimna"))

            assertEquals(listOf("2"), viewModel.currentState.visibleTasks.map { it.id })
        }

    @Test
    fun elFiltroDeCategoriaSoloMuestraEsaCategoria() =
        runTest {
            val tareas =
                listOf(
                    Task(id = "1", title = "Comprar pan", date = hoy, category = TaskCategory.HOGAR),
                    Task(id = "2", title = "Gimnasio", date = hoy, category = TaskCategory.SALUD),
                )
            val viewModel = viewModelCon(FakeTaskRepository(tareas))

            viewModel.onIntent(TasksIntent.FilterCategoryChanged(TaskCategory.SALUD))

            assertEquals(listOf("2"), viewModel.currentState.visibleTasks.map { it.id })
        }

    @Test
    fun volverAPulsarLaMismaCategoriaQuitaElFiltro() =
        runTest {
            val tareas = listOf(Task(id = "1", title = "Comprar pan", date = hoy, category = TaskCategory.HOGAR))
            val viewModel = viewModelCon(FakeTaskRepository(tareas))

            viewModel.onIntent(TasksIntent.FilterCategoryChanged(TaskCategory.HOGAR))
            viewModel.onIntent(TasksIntent.FilterCategoryChanged(TaskCategory.HOGAR))

            assertEquals(listOf("1"), viewModel.currentState.visibleTasks.map { it.id })
        }
```

(Import que falta si no esta ya: `com.daviddelgado.agenda.domain.model.TaskCategory`. Ajusta el nombre exacto de `viewModelCon`/`FakeTaskRepository` al que ya use el fichero — por convencion del repo son esos.)

Run: `./gradlew :feature:tasks:testDebugUnitTest --tests "*.TasksViewModelTest"`
Expected: FAIL — no existen `visibleTasks`, `SearchQueryChanged` ni `FilterCategoryChanged`.

- [ ] **Step 2: Estado e intents**

En `feature/tasks/src/commonMain/kotlin/com/daviddelgado/agenda/feature/tasks/TasksContract.kt`, añadir al `data class TasksState`:

```kotlin
    val searchQuery: String = "",
    val filterCategory: TaskCategory? = null,
```

y, dentro del `TasksState`, junto a `selectedCount`:

```kotlin
    val visibleTasks: List<Task>
        get() =
            tasks.filter { task ->
                (filterCategory == null || task.category == filterCategory) &&
                    (searchQuery.isBlank() || task.title.contains(searchQuery, ignoreCase = true))
            }
```

Añadir a `sealed interface TasksIntent`, junto a `FormTitleChanged`:

```kotlin
    data class SearchQueryChanged(val value: String) : TasksIntent

    data class FilterCategoryChanged(val value: TaskCategory) : TasksIntent
```

- [ ] **Step 3: Reductor**

En `feature/tasks/src/commonMain/kotlin/com/daviddelgado/agenda/feature/tasks/TasksViewModel.kt`, dentro del `when (intent)` de `onIntent`, añadir:

```kotlin
            is TasksIntent.SearchQueryChanged -> setState { copy(searchQuery = intent.value) }
            is TasksIntent.FilterCategoryChanged ->
                setState {
                    copy(filterCategory = if (filterCategory == intent.value) null else intent.value)
                }
```

- [ ] **Step 4: Verificar el reductor**

Run: `./gradlew :feature:tasks:testDebugUnitTest --tests "*.TasksViewModelTest"`
Expected: BUILD SUCCESSFUL, los 3 tests nuevos + todos los existentes en verde.

- [ ] **Step 5: UI — buscador + chips de categoria**

En `feature/tasks/src/commonMain/kotlin/com/daviddelgado/agenda/feature/tasks/TasksScreen.kt`, añadir los imports:

```kotlin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import com.daviddelgado.agenda.designsystem.component.AgendaTextField
import com.daviddelgado.agenda.domain.model.TaskCategory
```

(quita `ExposedDropdownMenuBox` de la lista si no llega a usarse; solo hace falta `FilterChip`, `horizontalScroll`, `AgendaTextField` y `TaskCategory` — revisa que no esten ya importados antes de duplicarlos).

Modificar `TasksList` para mostrar el buscador+chips antes de la lista y usar `state.visibleTasks` en vez de `state.tasks`:

```kotlin
@Composable
private fun TasksList(
    state: TasksState,
    onIntent: (TasksIntent) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp)) {
        if (!state.isSelectionMode) {
            TasksHeader(isSyncing = state.isSyncing, onRefresh = { onIntent(TasksIntent.Refresh) })
            TasksSearchAndFilters(state = state, onIntent = onIntent)
        }

        if (state.visibleTasks.isEmpty()) {
            Text(text = "No hay tareas para este dia", modifier = Modifier.padding(top = 24.dp))
        } else {
            LazyColumn {
                items(state.visibleTasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        isSelectionMode = state.isSelectionMode,
                        isSelected = task.id in state.selectedTaskIds,
                        onToggle = { onIntent(TasksIntent.ToggleCompleted(task.id)) },
                        onDelete = { onIntent(TasksIntent.RequestDelete(task)) },
                        onClick = {
                            if (state.isSelectionMode) {
                                onIntent(TasksIntent.ToggleTaskSelection(task.id))
                            } else {
                                onIntent(TasksIntent.OpenEditTaskForm(task))
                            }
                        },
                        onLongClick = { onIntent(TasksIntent.EnterSelectionMode(task.id)) },
                    )
                }
            }
        }
    }
}

/** Buscador de texto libre por titulo + chips de categoria (toggle: pulsar dos veces la quita). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksSearchAndFilters(
    state: TasksState,
    onIntent: (TasksIntent) -> Unit,
) {
    Column {
        AgendaTextField(
            value = state.searchQuery,
            onValueChange = { onIntent(TasksIntent.SearchQueryChanged(it)) },
            label = "Buscar por titulo",
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TaskCategory.entries.forEach { category ->
                FilterChip(
                    selected = state.filterCategory == category,
                    onClick = { onIntent(TasksIntent.FilterCategoryChanged(category)) },
                    label = { Text(category.name) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 6: Test instrumentado (en rojo, luego verde)**

Añadir a `feature/tasks/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/tasks/TasksScreenTest.kt` (siguiendo el patron de fakes ya usado en ese fichero):

```kotlin
    @Test
    fun escribirEnElBuscadorOcultaLasTareasQueNoCoinciden() {
        val tareas =
            listOf(
                Task(id = "1", title = "Comprar pan", date = hoy),
                Task(id = "2", title = "Gimnasio", date = hoy),
            )
        composeRule.setContent { TasksScreen(viewModel = viewModelCon(tareas)) }

        composeRule.onNodeWithText("Buscar por titulo").performTextInput("gimna")

        composeRule.onNodeWithText("Comprar pan").assertDoesNotExist()
        composeRule.onNodeWithText("Gimnasio").assertIsDisplayed()
    }
```

(Ajusta `viewModelCon`/`Task`/imports al patron exacto ya presente en ese fichero de test; añade `import androidx.compose.ui.test.performTextInput` y `import androidx.compose.ui.test.assertDoesNotExist` si faltan.)

Run: `./gradlew :feature:tasks:connectedDebugAndroidTest` (con el emulador Pixel_6a arrancado)
Expected: FAIL primero (sin el cambio de UI), PASS despues de aplicar el Step 5.

- [ ] **Step 7: Verificar todo y commitear**

Run: `./gradlew :feature:tasks:testDebugUnitTest :feature:tasks:connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL, todos los tests (existentes + nuevos) en verde.

```bash
git add feature/tasks/src/commonMain/kotlin/com/daviddelgado/agenda/feature/tasks/TasksContract.kt feature/tasks/src/commonMain/kotlin/com/daviddelgado/agenda/feature/tasks/TasksViewModel.kt feature/tasks/src/commonMain/kotlin/com/daviddelgado/agenda/feature/tasks/TasksScreen.kt feature/tasks/src/commonTest/kotlin/com/daviddelgado/agenda/feature/tasks/TasksViewModelTest.kt feature/tasks/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/tasks/TasksScreenTest.kt
git commit -m "feat: buscador por titulo y filtro por categoria en la lista de tareas"
```

---

## Cierre

- [ ] **Task 12: Verificacion final completa + actualizar ESTADO_PROYECTO.md**

Run: `./gradlew check` — Expected: BUILD SUCCESSFUL (compilacion + ktlint + detekt + lint + todos los tests unitarios de todos los modulos, incluidos los nuevos).

Run: `./gradlew :feature:login:connectedDebugAndroidTest :feature:tasks:connectedDebugAndroidTest :feature:calendar:connectedDebugAndroidTest` (con el emulador Pixel_6a arrancado) — Expected: todos los tests instrumentados en verde, incluidos los nuevos de accesibilidad (Task 2) y buscador (Task 11).

Añadir una seccion nueva a `ESTADO_PROYECTO.md` (siguiendo el estilo de las secciones "Verificado en caliente" ya existentes) describiendo: logging con Napier, fix de accesibilidad del calendario, CI en GitHub Actions, validacion+rate limiting del servidor, recordatorios push reales (con la nota de que `AGENDA_FIREBASE_SERVICE_ACCOUNT_JSON` y `androidApp/google-services.json` son placeholders pendientes de credenciales reales, igual que `ProductionConfig`), y buscador/filtro de tareas. Actualizar tambien el recuento total de tests en la seccion 6 y quitar de la seccion 10 ("Lo que NO esta hecho") los puntos ya resueltos si alguno lo estaba.

```bash
git add ESTADO_PROYECTO.md
git commit -m "docs: actualiza ESTADO_PROYECTO.md tras logging, seguridad, recordatorios push y buscador"
```
