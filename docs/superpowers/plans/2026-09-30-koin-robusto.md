# Koin robusto: que no se pierda ninguna definición y, si pasa, que no tumbe la app

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Blindar la inyección de dependencias de la agenda en tres capas: (1) estructura que impida olvidar una capa y DSL por constructor, (2) tests que detecten cualquier definición perdida, (3) tolerancia en tiempo de ejecución para que una feature incompleta muestre "sección no disponible" en vez de cerrar la app.

**Architecture:** Cada feature se compone en un único módulo Koin agregado (en `shared/di`) que incluye todas sus capas y lo que necesita. Las definiciones usan referencias a constructor (`singleOf`/`factoryOf`/`viewModelOf`), así `verify()` de Koin inspecciona los constructores reales. Un contrato explícito (`FeatureContract`) lista los tipos que cada feature debe declarar: los tests lo comparan en ambos sentidos con lo declarado, y `initKoin` lo comprueba al arrancar para marcar features no disponibles.

**Tech Stack:** Koin 4.0.0 (`koin-core`, `koin-compose-viewmodel`, `koin-test`), Kotlin 2.0.20, Compose Multiplatform 1.6.11.

**Diseño aprobado por el usuario en conversación (2026-09-30):** puntos 1 + 2 + 3. Sigue la misma rama `refactor/clean-architecture-modular`. Spec de referencia de la arquitectura: `docs/superpowers/specs/2026-09-30-clean-architecture-modular-design.md`.

## Global Constraints

- Mismo comportamiento de la app en el caso normal: mismos scopes (`single` sigue siendo `single`, `factory` sigue siendo `factory`, `viewModel` sigue siendo `viewModel`) y mismas instancias compartidas (p. ej. `TokenProviderImpl` y `TokenProvider` siguen siendo la MISMA instancia).
- Ningún `import` de API interna de Koin (`KoinInternalApi`) fuera de UN fichero: `shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/di/KoinDefinitions.kt`.
- Las reglas de capas de la spec siguen vigentes: `presentation` no depende de `data`/`database`; `core` no depende de features; los módulos agregados viven en `shared` (la app es la única que ve todas las capas).
- Commits en español, convencionales, **SIN trailer `Co-Authored-By` ni ninguna atribución** (preferencia explícita del usuario).
- Cada tarea termina con `./gradlew check :androidApp:assembleDebug --no-daemon` en verde. Los tests existentes no cambian su lógica ni sus aserciones.
- Windows: iOS no compila; los `iosMain` se revisan a mano.

## Review Focus

1. **Instancia compartida de `TokenProviderImpl`/`TokenProvider`:** hoy son una única instancia con dos tipos. Con `singleOf(::TokenProviderImpl) { bind<TokenProvider>() }` debe seguir siéndolo (test en Tarea 8).
2. **Includes duplicados:** varios módulos agregados incluyen `coreDataModule`/`tasksDatabaseModule`; Koin debe cargarlos una sola vez (sin definiciones sobrescritas → el test lo comprueba con `allowOverride(false)`).
3. **Contrato desincronizado:** añadir o quitar una definición sin tocar `FeatureContract` debe romper un test (igualdad en ambos sentidos).
4. **La comprobación de arranque nunca debe tumbar la app:** si la API interna de Koin fallase, se registra el error y se asume disponible.
5. **Feature `AUTH` no disponible:** sin auth no hay login; la app debe mostrar una pantalla de "no disponible" en vez de cerrarse.

---

### Task 7: módulos agregados por feature y DSL por constructor

**Files:**
- Modify (DSL): los 18 ficheros con `module {`: `core/data/.../di/CoreDataModule{,.android,.ios}.kt`, `feature/auth/{domain,data,presentation}/.../di/*.kt` (+ `AuthDataModule.{android,ios}.kt`), `feature/streaks/{domain,data,presentation}/.../di/*.kt`, `feature/tasks/{domain,data,database,presentation}/.../di/*.kt` (+ `TasksDatabaseModule.{android,ios}.kt`), `shared/.../di/AppModules.kt`
- Create: `shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/di/FeatureModules.kt`
- Test: `shared/src/androidUnitTest/.../shared/di/AppModulesTest.kt` (sigue pasando sin cambios de lógica)

**Interfaces:**
- Produces: `val authFeatureModule: Module`, `val tasksFeatureModule: Module`, `val streaksFeatureModule: Module` (paquete `com.daviddelgado.agenda.shared.di`); `appModules == listOf(authFeatureModule, tasksFeatureModule, streaksFeatureModule, sharedModule)`.

- [ ] **Step 1: DSL por constructor.** Sustituye cada definición cuyo cuerpo sea solo `Clase(get(), ..., get())` por la referencia al constructor, manteniendo el scope:
  - `factory { X(get()) }` / `factory { X() }` → `factoryOf(::X)` (`import org.koin.core.module.dsl.factoryOf`)
  - `viewModel { X(get(), ...) }` → `viewModelOf(::X)` (`import org.koin.core.module.dsl.viewModelOf`)
  - `single { X(get()) }` → `singleOf(::X)`; `single<I> { X(get()...) }` → `singleOf(::X) { bind<I>() }` (`import org.koin.core.module.dsl.singleOf`, `import org.koin.core.module.dsl.bind`)
  - `single { TokenProviderImpl(get()) }` + `single<TokenProvider> { get<TokenProviderImpl>() }` → una sola línea `singleOf(::TokenProviderImpl) { bind<TokenProvider>() }`.
  - `single { SplashSessionHandler(get(), get(), get()) }` → `singleOf(::SplashSessionHandler)`.
  - **Se quedan como lambda** (no son "constructor con get()"): `networkConfig()`, `NetworkConfig(baseUrl = ...)`, `SecureStorage(androidContext())`, `DatabaseFactory(androidContext())`, `createHttpClient(get(), get())`, `buildAgendaDatabase(get())`, `get<AgendaDatabase>().taskDao()`, `get<AgendaDatabase>().pendingDeletionDao()`, `AndroidFcmTokenProvider()`, `NoopFcmTokenProvider()`, `SecureStorage()` (iOS), `DatabaseFactory()` (iOS).
  - Si algún constructor tiene un parámetro con valor por defecto o un tipo no inyectado, NO lo conviertas: déjalo en lambda y anótalo en el informe.
- [ ] **Step 2: módulos agregados.** Crea `FeatureModules.kt`:

```kotlin
package com.daviddelgado.agenda.shared.di

import com.daviddelgado.agenda.core.data.di.coreDataModule
import com.daviddelgado.agenda.feature.auth.data.di.authDataModule
import com.daviddelgado.agenda.feature.auth.domain.di.authDomainModule
import com.daviddelgado.agenda.feature.auth.presentation.di.authPresentationModule
import com.daviddelgado.agenda.feature.streaks.data.di.streaksDataModule
import com.daviddelgado.agenda.feature.streaks.domain.di.streaksDomainModule
import com.daviddelgado.agenda.feature.streaks.presentation.di.streaksPresentationModule
import com.daviddelgado.agenda.feature.tasks.data.di.tasksDataModule
import com.daviddelgado.agenda.feature.tasks.database.di.tasksDatabaseModule
import com.daviddelgado.agenda.feature.tasks.domain.di.tasksDomainModule
import com.daviddelgado.agenda.feature.tasks.presentation.di.tasksPresentationModule
import org.koin.dsl.module

/**
 * Un modulo Koin por feature con TODAS sus capas y lo que necesitan de otras (core, BD de
 * tareas). La app solo lista features: es imposible olvidar una capa. Koin carga una sola vez
 * los modulos incluidos desde varias features (coreDataModule, tasksDatabaseModule).
 */
val authFeatureModule =
    module {
        // tasksDatabaseModule: AuthRepositoryImpl vacia las tareas locales en logout/borrar cuenta.
        includes(coreDataModule, tasksDatabaseModule, authDomainModule, authDataModule, authPresentationModule)
    }

val tasksFeatureModule =
    module {
        includes(coreDataModule, tasksDatabaseModule, tasksDomainModule, tasksDataModule, tasksPresentationModule)
    }

val streaksFeatureModule =
    module {
        // La racha se calcula con las tareas completadas (TaskDao).
        includes(tasksDatabaseModule, streaksDomainModule, streaksDataModule, streaksPresentationModule)
    }
```
- [ ] **Step 3: `AppModules.kt`.** `appModules = listOf(authFeatureModule, tasksFeatureModule, streaksFeatureModule, sharedModule)`; quita los imports que sobren. `sharedModule` sigue siendo `private` o pasa a `internal` si la Tarea 8 lo necesita (hazlo `internal` ya).
- [ ] **Step 4: verificación.** `./gradlew :shared:testDebugUnitTest --no-daemon` (AppModulesTest debe seguir en verde; si `extraTypes` necesita menos tipos ahora, NO los quites en esta tarea). Después `./gradlew check :androidApp:assembleDebug --no-daemon` con el mismo recuento de tests (322 sumando variantes debug+release).
- [ ] **Step 5: prueba rápida en emulador** (si hay dispositivo, `adb devices`): instalar `:androidApp:installDebug`, abrir la app y comprobar que llega al login/home sin crash (`adb logcat -d | grep -i -E "koin|FATAL"` sin errores de Koin). Si no hay dispositivo, anótalo como no verificado.
- [ ] **Step 6: commit** `refactor(di): modulos Koin agregados por feature y DSL por constructor` (sin trailer).

### Task 8: contrato de tipos por feature y tests que detectan cualquier pérdida

**Files:**
- Create: `shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/di/KoinDefinitions.kt` (único sitio con API interna de Koin)
- Create: `shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/di/FeatureContract.kt`
- Create: `shared/src/androidUnitTest/kotlin/com/daviddelgado/agenda/shared/di/FeatureModulesTest.kt`
- Modify: `shared/src/androidUnitTest/.../di/AppModulesTest.kt` (KDoc + tests nuevos)

**Interfaces:**
- Produces:
  - `enum class AppFeature { AUTH, TASKS, STREAKS }`
  - `object FeatureContract { val modules: Map<AppFeature, Module>; val requiredTypes: Map<AppFeature, Set<KClass<*>>>; fun missingTypes(declared: Set<KClass<*>>): Map<AppFeature, Set<KClass<*>>> }` — `missingTypes` devuelve solo las features con algún tipo ausente.
  - `fun Koin.declaredTypes(): Set<KClass<*>>` (en `KoinDefinitions.kt`): conjunto de `primaryType` + `secondaryTypes` de todas las definiciones cargadas (`instanceRegistry.instances.values.map { it.beanDefinition }`), con `@OptIn(KoinInternalApi::class)`.

- [ ] **Step 1 (RED): tests primero.** Crea `FeatureModulesTest.kt` con, para cada `AppFeature`:
  1. `verify()` del módulo de la feature **aislado** (`FeatureContract.modules[feature]!!.verify(extraTypes = ...)`), con los mismos `extraTypes` justificados que `AppModulesTest` (`Context`, `HttpClientEngine`, `HttpClientConfig`, `List`) — solo los que hagan falta.
  2. Igualdad exacta: `koinApplication { allowOverride(false); modules(FeatureContract.modules[feature]!!) }.koin.declaredTypes()` **==** `FeatureContract.requiredTypes[feature]`. Mensaje de fallo con las dos diferencias (sobran / faltan).
  3. `koinApplication { allowOverride(false); modules(appModules) }` no lanza (includes duplicados no redefinen nada).
  Y en `AppModulesTest` añade:
  4. `FeatureContract.missingTypes(koinApplication { modules(appModules) }.koin.declaredTypes())` está vacío.
  5. Instancia compartida de `TokenProviderImpl`/`TokenProvider` (sin crear instancias, porque `SecureStorage` necesita Android): añade a `KoinDefinitions.kt` `fun Koin.definitionsDeclaring(type: KClass<*>): Int` (número de definiciones cuyo `primaryType` o `secondaryTypes` contiene `type`) y comprueba que tanto `TokenProviderImpl::class` como `TokenProvider::class` dan exactamente 1 y que es la misma definición (`TokenProvider` está en `secondaryTypes` de la definición de `TokenProviderImpl`) → una sola instancia `single` con dos tipos.
  Ejecuta `./gradlew :shared:testDebugUnitTest --no-daemon`: debe **fallar por compilación** (`FeatureContract`/`declaredTypes` no existen). Guarda la salida (RED).
- [ ] **Step 2 (GREEN): implementación.** Crea `KoinDefinitions.kt` y `FeatureContract.kt`. `requiredTypes[feature]` = lista explícita y ordenada por capa de TODOS los tipos que declara el módulo agregado de esa feature (incluidos los de `coreDataModule`/`tasksDatabaseModule` que incluye), p. ej. para TASKS: `NetworkConfig`, `SecureStorage`, `TokenProviderImpl`, `TokenProvider`, `HttpClient`, `DatabaseFactory`, `AgendaDatabase`, `TaskDao`, `PendingDeletionDao`, los 9 casos de uso de tareas, `TaskApi`, `TaskRepositoryImpl`, `TaskRepository`, `TasksViewModel`, `CalendarViewModel`. Para AUTH añade `FcmTokenProvider` y la implementación de plataforma NO (es `private`; su tipo declarado es `FcmTokenProvider`). Rellena la lista leyendo los módulos; el test de igualdad te dirá si falta o sobra algo.
  - `shared` pasa a necesitar dependencias de compilación sobre los tipos que nombra el contrato (`core:data`, todas las capas `domain`/`data`/`database`/`presentation` de las features, `io.ktor:ktor-client-core` vía `core:data` `api`): ya las tiene casi todas; añade las que falten en `shared/build.gradle.kts`.
- [ ] **Step 3: tests en verde.** `./gradlew :shared:testDebugUnitTest --no-daemon` → PASS.
- [ ] **Step 4: demostrar que detectan pérdidas (y deshacer).** Para cada caso, haz el cambio, ejecuta `:shared:testDebugUnitTest`, copia la línea del fallo al informe y **revierte** con `git checkout -- <fichero>`:
  - quitar `singleOf(::TaskApi)` de `TasksDataModule.kt` → debe fallar `FeatureModulesTest` (TASKS);
  - quitar `includes(tasksDatabaseModule, ...)` → dejar solo las capas de streaks en `streaksFeatureModule` → debe fallar (STREAKS);
  - quitar `single<FcmTokenProvider> { AndroidFcmTokenProvider() }` de `AuthDataModule.android.kt` → debe fallar (AUTH);
  - añadir una definición nueva cualquiera a `tasksDomainModule` sin tocar el contrato → debe fallar la igualdad.
  Tras revertir, `git status` limpio salvo los ficheros de la tarea.
- [ ] **Step 5: KDoc y docs.** Actualiza el KDoc de `AppModulesTest` y la sección 7terdecies de `ESTADO_PROYECTO.md`: qué cubre ahora (constructores reales vía DSL, igualdad contrato↔módulo por feature, sin overrides) y que el pendiente de "reforzar el test" queda resuelto.
- [ ] **Step 6: verificación completa** `./gradlew check :androidApp:assembleDebug --no-daemon`.
- [ ] **Step 7: commit** `test(di): contrato de tipos por feature y tests que detectan definiciones perdidas` (sin trailer).

### Task 9: tolerancia en tiempo de ejecución (feature no disponible en vez de crash)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/di/FeatureAvailability.kt`
- Modify: `shared/.../di/KoinInitializer.kt`, `shared/.../App.kt`
- Test: `shared/src/commonTest/kotlin/com/daviddelgado/agenda/shared/di/FeatureAvailabilityTest.kt`

**Interfaces:**
- Consumes: `AppFeature`, `FeatureContract.missingTypes`, `Koin.declaredTypes()` (Tarea 8).
- Produces:
  - `object FeatureAvailability { val unavailable: Set<AppFeature>; fun isAvailable(feature: AppFeature): Boolean; internal fun update(unavailable: Set<AppFeature>) }`
  - `internal fun checkFeatures(declaredTypes: () -> Set<KClass<*>>, log: (String) -> Unit): Set<AppFeature>`: calcula las features con tipos ausentes; registra UNA línea por feature con los nombres de los tipos que faltan; si `declaredTypes()` lanza cualquier excepción, registra el error y devuelve `emptySet()` (asume disponible: nunca tumba la app).

- [ ] **Step 1 (RED): tests** en `FeatureAvailabilityTest.kt` (commonTest, sin Koin real):
  1. `checkFeatures({ todos los tipos de requiredTypes }, log)` → vacío y sin logs.
  2. Quitando `TaskApi::class` del conjunto → `setOf(AppFeature.TASKS)` y un log que contiene `"TaskApi"`.
  3. Quitando un tipo compartido (p. ej. `TaskDao::class`, lo usan las tres) → `setOf(AUTH, TASKS, STREAKS)`.
  4. `declaredTypes` que lanza `IllegalStateException` → vacío y un log con el error (no propaga).
  5. `FeatureAvailability.update(setOf(STREAKS))` → `isAvailable(STREAKS) == false`, `isAvailable(TASKS) == true`.
  Ejecuta `./gradlew :shared:testDebugUnitTest --no-daemon` → falla por compilación (RED). Guarda la salida.
- [ ] **Step 2 (GREEN):** implementa `FeatureAvailability.kt`. En `initKoin`, justo después de `startKoin { ... }`, guarda la `KoinApplication` y llama `FeatureAvailability.update(checkFeatures({ koinApp.koin.declaredTypes() }) { AgendaLogger.e("Koin", it) })` (usa la firma real de `AgendaLogger`; si `e` no existe, usa la que haya para errores).
- [ ] **Step 3: UI en `App.kt`.**
  - Añade un composable privado `FeatureUnavailable(modifier)` con un texto centrado "Esta sección no está disponible ahora mismo" (usa los componentes/tema de `core:designsystem` que ya use `App.kt`).
  - Si `!FeatureAvailability.isAvailable(AUTH)`: la app muestra `FeatureUnavailable` a pantalla completa en lugar de splash/login/home (sin auth no se puede entrar).
  - En la home con pestañas: las pestañas de tareas y calendario muestran `FeatureUnavailable` si `TASKS` no está disponible; la de rachas si `STREAKS` no lo está; ajustes depende de `AUTH`. Las demás pestañas funcionan normal.
  - No cambies nada más de la navegación.
- [ ] **Step 4: verde + completo.** `./gradlew :shared:testDebugUnitTest --no-daemon` y después `./gradlew check :androidApp:assembleDebug --no-daemon`.
- [ ] **Step 5: prueba en emulador del modo degradado** (si hay dispositivo): comenta temporalmente `streaksDataModule` dentro de `streaksFeatureModule`, instala, abre la app: debe arrancar, la pestaña Rachas debe mostrar el mensaje y el resto funcionar; `logcat` muestra la línea de Koin. **Revierte** el cambio, reinstala y confirma que todo vuelve a la normalidad. Si no hay dispositivo, anótalo como no verificado.
- [ ] **Step 6: docs.** Añade a `ESTADO_PROYECTO.md` (sección nueva tras 7terdecies) el mecanismo: contrato, tests, comprobación de arranque, qué ve el usuario, y que `KoinDefinitions.kt` es el único punto con API interna de Koin (revisar al actualizar Koin).
- [ ] **Step 7: commit** `feat(di): features no disponibles se degradan en vez de cerrar la app` (sin trailer).
