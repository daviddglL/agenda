# Migración a Clean Architecture modular por feature — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganizar los módulos del cliente KMP de la agenda al modelo de Squadfy_KMM: `core/` solo con lo transversal, `feature/{auth,tasks,streaks}/{domain,data,[database],presentation}` y `build-logic/` con convention plugins, sin cambiar el comportamiento de la app.

**Architecture:** Refactor puro por fases. Primero `build-logic`, después se sacan las features de core una a una (auth → streaks → tasks) y al final se consolida core. Así `core` nunca depende de una feature, ni siquiera temporalmente. Los ficheros se mueven con `git mv`, y los `package`/`import` se reescriben con dos scripts pequeños y deterministas. Los tests actuales son la red de seguridad: no se escriben tests de comportamiento nuevos y los existentes no cambian sus aserciones.

**Tech Stack:** Kotlin 2.0.20, Kotlin Multiplatform, Compose Multiplatform 1.6.11, AGP 8.5.2, Gradle 8.13, Koin 4.0.0, Ktor 2.3.12, Room KMP 2.7.0-alpha11, ktlint 12.1.1, detekt 1.23.6.

**Spec:** `docs/superpowers/specs/2026-09-30-clean-architecture-modular-design.md` (léela entera antes de empezar).

## Global Constraints

- El comportamiento de la app no cambia. `server/` e `iosApp/` no se tocan.
- Se mantiene MVI (`MviViewModel`, `UiState`, `UiIntent`, `UiEffect`).
- Raíz de paquetes: `com.daviddelgado.agenda`. Formato: `com.daviddelgado.agenda.<core|feature>.[<feature>.]<capa>.<subpaquete>`.
- Los paquetes **no llevan guion bajo** (`forgotpassword`, no `forgot_password`), por las reglas `PackageNaming` de detekt y `package-name` de ktlint.
- El `namespace` Android de cada módulo = `"com.daviddelgado.agenda" + path.replace(':', '.')` (`:core:data` → `com.daviddelgado.agenda.core.data`).
- `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34` (solo app), Java/JVM 17, targets `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`.
- Excepciones de dependencia entre features permitidas (solo estas dos): `feature:streaks:data → feature:tasks:database` y `feature:auth:data → feature:tasks:database`.
- `presentation` nunca depende de `data` ni de `database`. `domain` no importa `io.ktor`, `androidx.room` ni `androidx.compose`.
- Las dependencias entre proyectos se declaran en el `build.gradle.kts` de cada módulo, nunca en los convention plugins.
- Los ficheros se mueven con `git mv`, nunca con copiar y borrar (excepto los fakes de test, que se **copian** a propósito cuando los necesitan varios módulos).
- Room: `schemas/.../4.json` no cambia ni un byte. La carpeta de esquemas se renombra al FQN nuevo de `AgendaDatabase`.
- Cada tarea termina con `./gradlew check --no-daemon` en verde y un commit convencional en español, con el trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- En Windows no se compilan los targets iOS (`kotlin.native.ignoreDisabledTargets=true`). Por eso los ficheros de `iosMain` se revisan a mano y con el grep de referencias obsoletas de cada tarea.
- Todos los comandos se ejecutan desde la raíz del repo (`C:\Users\ragna\OneDrive\Escritorio\agenda`) en Git Bash.

## Review Focus

1. **Koin en tiempo de ejecución:** si una definición se pierde al dividir los módulos, falla al abrir la pantalla, no al compilar. Un test en `shared` (Tarea 6, `AppModulesTest`) comprueba con `verify()` de Koin que el grafo completo se resuelve.
2. **Base de datos instalada:** después de la Tarea 4, un usuario con la BD v4 debe seguir viendo sus tareas. Se fija comparando `4.json` byte a byte y con el test de Room existente (si lo hay) en la Tarea 4.
3. **Ficheros `iosMain` que no se compilan en Windows:** un `import` obsoleto en `*.ios.kt` no lo detecta el compilador. Lo detecta el grep de "referencias obsoletas" al final de cada tarea.
4. **Referencias implícitas del mismo paquete:** clases que antes compartían paquete (y por eso no tenían `import`) y ahora están en paquetes distintos. Cada tarea lista las que se conocen; el resto las señala `compileDebugKotlinAndroid`.
5. **Login + token FCM:** tras mover `FcmTokenProvider` al dominio y su implementación a `auth:data`, el login tiene que seguir registrando el token. Lo cubre `LoginViewModelTest`, que se mueve sin cambios, y la prueba manual de la Tarea 6.

---

## Herramientas de la migración (se crean en la Tarea 1 y se borran en la Tarea 6)

### `tools/refactor/fix-packages.sh`

Pone en la línea `package` de cada `.kt` el paquete que corresponde a su ruta: lo que va después de `/kotlin/`, con `/` → `.`.

```bash
#!/usr/bin/env bash
# Uso: tools/refactor/fix-packages.sh <directorio-o-fichero>...
# Reescribe la linea `package` de cada .kt para que coincida con su ruta
# (lo que va despues de /kotlin/, con / -> .). Idempotente.
set -euo pipefail
for target in "$@"; do
  find "$target" -type f -name '*.kt' -not -path '*/build/*' | while read -r file; do
    rel="${file#*/kotlin/}"
    pkg="$(dirname "$rel" | tr '/' '.')"
    PKG="$pkg" perl -pi -e 's/^package [\w.]+/package $ENV{PKG}/' "$file"
  done
done
```

### `tools/refactor/rename-fqn.sh`

Aplica un fichero de mapeo con líneas `<viejo> <nuevo>` (nombres completos de clase o prefijos de paquete) a todos los `.kt` y `.kts` del cliente. Los aplica del más largo al más corto, para que un prefijo de paquete no se coma a un nombre más específico, y exige que después del nombre no venga una letra, un dígito ni `_`.

```bash
#!/usr/bin/env bash
# Uso: tools/refactor/rename-fqn.sh <fichero-de-mapeo>
# Cada linea: "<fqn-o-paquete-viejo> <fqn-o-paquete-nuevo>". Se ignoran lineas vacias y las
# que empiezan por #. Se aplican de la mas larga a la mas corta.
set -euo pipefail
map="$1"
grep -v '^[[:space:]]*#' "$map" | awk 'NF == 2 { print length($1) "\t" $1 "\t" $2 }' \
  | sort -rn | cut -f2- | while IFS=$'\t' read -r old new; do
    find androidApp shared core feature -type f \( -name '*.kt' -o -name '*.kts' \) \
      -not -path '*/build/*' -print0 \
      | OLD="$old" NEW="$new" xargs -0 perl -pi -e 's/\Q$ENV{OLD}\E(?![A-Za-z0-9_])/$ENV{NEW}/g'
  done
```

Los ficheros de mapeo de cada tarea van en `tools/refactor/maps/` y se borran junto con los scripts en la Tarea 6.

### Comprobación de referencias obsoletas (se usa al final de cada tarea)

```bash
git grep -n -E "<regex de la tarea>" -- androidApp shared core feature ':!**/build/**'
```
Resultado esperado: **nada**. Esto cubre también `iosMain`, que no se compila en Windows.

---

### Task 1: build-logic con convention plugins (sin mover código)

**Files:**
- Create: `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/com/daviddelgado/agenda/convention/ProjectExtensions.kt`
- Create: `build-logic/convention/src/main/kotlin/{KmpLibraryConventionPlugin,CmpLibraryConventionPlugin,CmpFeatureConventionPlugin,RoomConventionPlugin,AndroidApplicationConventionPlugin}.kt`
- Create: `tools/refactor/fix-packages.sh`, `tools/refactor/rename-fqn.sh` (contenido arriba), `tools/refactor/maps/` (vacío, con un `.gitkeep`)
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml`, y el `build.gradle.kts` de: `core/{common,designsystem,network,database,domain,data}`, `feature/{login,register,passwordreset,calendar,tasks,streaks,settings}`, `shared`, `androidApp`

**Interfaces:**
- Produces: plugin ids `agenda.kmp.library`, `agenda.cmp.library`, `agenda.cmp.feature`, `agenda.room`, `agenda.android.application`. Los usan todas las tareas siguientes.

- [ ] **Step 1: Línea base.** Ejecuta `./gradlew check --no-daemon` y guarda el número de tests que pasan:

```bash
./gradlew check --no-daemon 2>&1 | tail -5
find . -path '*/build/test-results/*' -name 'TEST-*.xml' -not -path './server/*' | xargs grep -h -o 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print "tests cliente:", s}'
```
Expected: `BUILD SUCCESSFUL` y un número N. Apunta N en el ledger/progreso: todas las tareas deben terminar con el mismo número de tests (los de `server/` se excluyen porque no se tocan).

- [ ] **Step 2: Catálogo.** Añade al final de `[libraries]` en `gradle/libs.versions.toml`:

```toml
# Compose Multiplatform (mismas coordenadas que los accesores compose.* del plugin 1.6.11),
# usadas desde los convention plugins de build-logic
jetbrains-compose-runtime = { group = "org.jetbrains.compose.runtime", name = "runtime", version.ref = "compose-multiplatform" }
jetbrains-compose-foundation = { group = "org.jetbrains.compose.foundation", name = "foundation", version.ref = "compose-multiplatform" }
jetbrains-compose-material3 = { group = "org.jetbrains.compose.material3", name = "material3", version.ref = "compose-multiplatform" }
jetbrains-compose-ui = { group = "org.jetbrains.compose.ui", name = "ui", version.ref = "compose-multiplatform" }
jetbrains-compose-materialIconsExtended = { group = "org.jetbrains.compose.material", name = "material-icons-extended", version.ref = "compose-multiplatform" }
jetbrains-compose-components-resources = { group = "org.jetbrains.compose.components", name = "components-resources", version.ref = "compose-multiplatform" }

# Plugins de Gradle como dependencias de compilacion de build-logic (compileOnly)
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "agp" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
androidx-room-gradlePlugin = { group = "androidx.room", name = "room-gradle-plugin", version.ref = "room" }
```

- [ ] **Step 3: `build-logic/settings.gradle.kts`**

```kotlin
rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

include(":convention")
```

- [ ] **Step 4: `build-logic/convention/build.gradle.kts`**

```kotlin
plugins {
    `kotlin-dsl`
}

group = "com.daviddelgado.agenda.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.androidx.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "agenda.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("cmpLibrary") {
            id = "agenda.cmp.library"
            implementationClass = "CmpLibraryConventionPlugin"
        }
        register("cmpFeature") {
            id = "agenda.cmp.feature"
            implementationClass = "CmpFeatureConventionPlugin"
        }
        register("room") {
            id = "agenda.room"
            implementationClass = "RoomConventionPlugin"
        }
        register("androidApplication") {
            id = "agenda.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
```

- [ ] **Step 5: `ProjectExtensions.kt`**

```kotlin
package com.daviddelgado.agenda.convention

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal const val COMPILE_SDK = 34
internal const val MIN_SDK = 26
internal const val TARGET_SDK = 34
internal const val JVM_TARGET = 17

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()

/** `:feature:tasks:data` -> `com.daviddelgado.agenda.feature.tasks.data` (igual que los namespaces actuales). */
internal fun Project.pathToNamespace(): String = "com.daviddelgado.agenda" + path.replace(':', '.').lowercase()
```

- [ ] **Step 6: `KmpLibraryConventionPlugin.kt`** (en el paquete raíz, como en Squadfy)

```kotlin
import com.android.build.api.dsl.LibraryExtension
import com.daviddelgado.agenda.convention.COMPILE_SDK
import com.daviddelgado.agenda.convention.JVM_TARGET
import com.daviddelgado.agenda.convention.MIN_SDK
import com.daviddelgado.agenda.convention.lib
import com.daviddelgado.agenda.convention.libs
import com.daviddelgado.agenda.convention.pathToNamespace
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Modulo KMP sin UI: Android + los 3 targets iOS, Java 17, SDKs comunes y namespace desde el path. */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.library")

            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain(JVM_TARGET)
                androidTarget()
                iosX64()
                iosArm64()
                iosSimulatorArm64()
            }

            extensions.configure<LibraryExtension> {
                namespace = pathToNamespace()
                compileSdk = COMPILE_SDK
                defaultConfig {
                    minSdk = MIN_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                // Los tests unitarios de Android que pasan por android.util.Log (Napier, via
                // AgendaLogger) reciben valores por defecto en vez de "Method not mocked".
                testOptions.unitTests.isReturnDefaultValues = true
            }

            dependencies {
                "commonMainImplementation"(libs.lib("kotlinx-coroutines-core"))
                "commonMainImplementation"(libs.lib("kotlinx-datetime"))
                "commonMainImplementation"(libs.lib("koin-core"))
                "commonTestImplementation"(kotlin("test"))
                "commonTestImplementation"(libs.lib("kotlinx-coroutines-test"))
            }
        }
    }
}
```

- [ ] **Step 7: `CmpLibraryConventionPlugin.kt`**

```kotlin
import com.daviddelgado.agenda.convention.lib
import com.daviddelgado.agenda.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Modulo KMP con Compose Multiplatform (UI compartida). */
class CmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("agenda.kmp.library")
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            dependencies {
                "commonMainImplementation"(libs.lib("jetbrains-compose-runtime"))
                "commonMainImplementation"(libs.lib("jetbrains-compose-foundation"))
                "commonMainImplementation"(libs.lib("jetbrains-compose-material3"))
                "commonMainImplementation"(libs.lib("jetbrains-compose-ui"))
            }
        }
    }
}
```

- [ ] **Step 8: `CmpFeatureConventionPlugin.kt`**

```kotlin
import com.daviddelgado.agenda.convention.lib
import com.daviddelgado.agenda.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Capa presentation de una feature: Compose + Koin para ViewModels + tests de UI instrumentados
 * (punto 5 de markdown.md). Las dependencias de proyecto (core:*, feature:X:domain) las declara
 * cada modulo en su build.gradle.kts.
 */
class CmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("agenda.cmp.library")

            dependencies {
                "commonMainImplementation"(libs.lib("jetbrains-compose-materialIconsExtended"))
                "commonMainImplementation"(libs.lib("koin-compose"))
                "commonMainImplementation"(libs.lib("koin-compose-viewmodel"))

                "androidInstrumentedTestImplementation"(libs.lib("androidx-compose-ui-test-junit4"))
                "androidInstrumentedTestImplementation"(libs.lib("androidx-test-runner"))
                "androidInstrumentedTestImplementation"(libs.lib("androidx-test-ext-junit"))
                "androidInstrumentedTestImplementation"(libs.lib("androidx-compose-ui-test-manifest"))
                "androidInstrumentedTestImplementation"(libs.lib("androidx-activity-compose"))
                "androidInstrumentedTestImplementation"(libs.lib("kotlinx-coroutines-test"))
            }
        }
    }
}
```

- [ ] **Step 9: `RoomConventionPlugin.kt`**

```kotlin
import androidx.room.gradle.RoomExtension
import com.daviddelgado.agenda.convention.lib
import com.daviddelgado.agenda.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room KMP: KSP por target y esquemas exportados en <modulo>/schemas (se versionan en git). */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("androidx.room")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                "commonMainApi"(libs.lib("androidx-room-runtime"))
                "kspAndroid"(libs.lib("androidx-room-compiler"))
                "kspIosX64"(libs.lib("androidx-room-compiler"))
                "kspIosArm64"(libs.lib("androidx-room-compiler"))
                "kspIosSimulatorArm64"(libs.lib("androidx-room-compiler"))
            }
        }
    }
}
```

- [ ] **Step 10: `AndroidApplicationConventionPlugin.kt`**

```kotlin
import com.android.build.api.dsl.ApplicationExtension
import com.daviddelgado.agenda.convention.COMPILE_SDK
import com.daviddelgado.agenda.convention.JVM_TARGET
import com.daviddelgado.agenda.convention.MIN_SDK
import com.daviddelgado.agenda.convention.TARGET_SDK
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** App Android con Compose. applicationId, versiones, buildTypes y google-services van en el modulo. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<ApplicationExtension> {
                compileSdk = COMPILE_SDK
                defaultConfig {
                    minSdk = MIN_SDK
                    targetSdk = TARGET_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                buildFeatures.compose = true
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                jvmToolchain(JVM_TARGET)
            }
        }
    }
}
```

- [ ] **Step 11: `settings.gradle.kts`.** Dentro de `pluginManagement {`, como primera línea, añade:

```kotlin
    includeBuild("build-logic")
```

- [ ] **Step 12: Pasa los módulos actuales a los plugins.** Sustituye el bloque `plugins {}`, los `jvmToolchain`/targets y el bloque `android {}` común de cada módulo. **Conserva** todas las dependencias que no ponga ya el plugin y todos los comentarios. Contenido final de cada uno:

`core/common/build.gradle.kts`
```kotlin
plugins {
    id("agenda.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.napier)
        }
    }
}
```

`core/domain/build.gradle.kts`
```kotlin
plugins {
    id("agenda.kmp.library")
}
```

`core/designsystem/build.gradle.kts`
```kotlin
plugins {
    id("agenda.cmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.components.resources)
        }
    }
}
```

`core/network/build.gradle.kts`
```kotlin
plugins {
    id("agenda.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
```

`core/database/build.gradle.kts`
```kotlin
plugins {
    id("agenda.kmp.library")
    id("agenda.room")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}
```

`core/data/build.gradle.kts`
```kotlin
plugins {
    id("agenda.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.database)
            implementation(projects.core.network)
            implementation(projects.core.common)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
        }
        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
            implementation(libs.koin.android)
        }
    }
}

android {
    // Expone BuildConfig.DEBUG para elegir servidor de desarrollo vs ProductionConfig
    // (punto 4 de markdown.md) sin tocar codigo entre un build debug y uno release.
    buildFeatures {
        buildConfig = true
    }
}
```

`feature/tasks/build.gradle.kts` (y **el mismo contenido** en `feature/calendar`, `feature/register`, `feature/passwordreset`, `feature/settings`, `feature/streaks`; sus diferencias actuales solo eran dependencias que ahora pone el plugin)
```kotlin
plugins {
    id("agenda.cmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.domain)
        }
    }
}
```

`feature/login/build.gradle.kts`
```kotlin
plugins {
    id("agenda.cmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.domain)
        }

        androidMain.dependencies {
            implementation(libs.firebase.messaging)
        }
    }
}

// El DSL de dependencias de Kotlin Multiplatform (arriba, "androidMain.dependencies") no
// resuelve bien un BOM (platform(...)); se añade aqui, con el DSL clasico de Gradle, igual
// que hace androidApp/build.gradle.kts.
dependencies {
    add("androidMainImplementation", platform(libs.firebase.bom))
}
```

`shared/build.gradle.kts`
```kotlin
plugins {
    id("agenda.cmp.library")
}

kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.feature.login)
            implementation(projects.feature.register)
            implementation(projects.feature.passwordreset)
            implementation(projects.feature.calendar)
            implementation(projects.feature.tasks)
            implementation(projects.feature.streaks)
            implementation(projects.feature.settings)
            implementation(libs.jetbrains.compose.materialIconsExtended)
            implementation(libs.koin.compose)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
```

`androidApp/build.gradle.kts`
```kotlin
plugins {
    id("agenda.android.application")
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.daviddelgado.agenda.android"

    defaultConfig {
        applicationId = "com.daviddelgado.agenda"
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(projects.shared)
    implementation(projects.core.common)
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

- [ ] **Step 13: Crea los scripts** `tools/refactor/fix-packages.sh` y `tools/refactor/rename-fqn.sh` con el contenido de la sección "Herramientas", `chmod +x`, y `tools/refactor/maps/.gitkeep`. Prueba el script de renombrado sin tocar el repo:

```bash
R="$PWD"; mkdir -p .fqn-test/core/x/src/commonMain/kotlin/a/b && cd .fqn-test && mkdir -p androidApp shared feature
printf 'package a.b\nimport com.x.foo.Bar\nimport com.x.foo.BarBaz\nimport com.x.foo\n' > core/x/src/commonMain/kotlin/a/b/T.kt
printf 'com.x.foo.Bar com.y.Bar\ncom.x.foo com.z.foo\n' > m.map
bash "$R/tools/refactor/rename-fqn.sh" m.map && cat core/x/src/commonMain/kotlin/a/b/T.kt; cd "$R" && rm -rf .fqn-test
```
Expected:
```
package a.b
import com.y.Bar
import com.z.foo.BarBaz
import com.z.foo
```
(`Bar` se mapea por su entrada específica; `BarBaz` no se confunde con `Bar` y cae en el prefijo de paquete.)

- [ ] **Step 14: Verifica los namespaces** (no deben cambiar, porque `BuildConfig` de `core:data` y los `R` dependen de ellos):

```bash
./gradlew :core:data:generateDebugBuildConfig --no-daemon -q && find core/data/build -name BuildConfig.java | xargs grep '^package'
```
Expected: `package com.daviddelgado.agenda.core.data;`

- [ ] **Step 15: Verificación completa**

```bash
./gradlew check :androidApp:assembleDebug --no-daemon
```
Expected: `BUILD SUCCESSFUL` y el mismo número de tests N del Step 1.

- [ ] **Step 16: Commit**

```bash
git add -A build-logic tools settings.gradle.kts gradle/libs.versions.toml core feature shared androidApp
git commit -m "build: añade build-logic con convention plugins

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: feature/auth (domain, data, presentation)

Abreviaturas de ruta usadas en esta tarea (todas bajo `src/<sourceSet>/kotlin/`):
- `ADOM` = `feature/auth/domain/src/commonMain/kotlin/com/daviddelgado/agenda/feature/auth/domain`
- `ADOMT` = `feature/auth/domain/src/commonTest/kotlin/com/daviddelgado/agenda/feature/auth/domain`
- `ADATA` = `feature/auth/data/src/commonMain/kotlin/com/daviddelgado/agenda/feature/auth/data`
- `ADATAT` = `feature/auth/data/src/commonTest/kotlin/com/daviddelgado/agenda/feature/auth/data`
- `APRES` = `feature/auth/presentation/src/commonMain/kotlin/com/daviddelgado/agenda/feature/auth/presentation`
- `APREST` = `feature/auth/presentation/src/commonTest/kotlin/com/daviddelgado/agenda/feature/auth/presentation`
- `APRESI` = `feature/auth/presentation/src/androidInstrumentedTest/kotlin/com/daviddelgado/agenda/feature/auth/presentation`
- Origen: `CD` = `core/domain/src/commonMain/kotlin/com/daviddelgado/agenda/domain`, `CDT` = `core/domain/src/commonTest/kotlin/com/daviddelgado/agenda/domain`, `CDATA` = `core/data/src/commonMain/kotlin/com/daviddelgado/agenda/data`, `CDATAT` = `core/data/src/commonTest/kotlin/com/daviddelgado/agenda/data`, `CNET` = `core/network/src/commonMain/kotlin/com/daviddelgado/agenda/network`, `FL` = `feature/login/src`, `FR` = `feature/register/src`, `FP` = `feature/passwordreset/src`, `FS` = `feature/settings/src`, y el sufijo de paquete de cada feature vieja es `kotlin/com/daviddelgado/agenda/feature/<nombre>`.

**Files:**
- Create: `feature/auth/{domain,data,presentation}/build.gradle.kts`
- Create: `ADOM/model/User.kt`, `ADOM/repository/AuthRepository.kt`, `ADOM/usecase/*.kt` (9), `ADOM/di/AuthDomainModule.kt`, `ADATA/dto/AuthRequests.kt`, `ADATA/di/AuthDataModule.kt`, `APRES/di/AuthPresentationModule.kt`, `tools/refactor/maps/auth.map`
- Move: ver Steps 4–8
- Modify: `settings.gradle.kts`, `CD/model/Models.kt`, `CD/repository/Repositories.kt`, `CD/usecase/UseCases.kt`, `CD/di/DomainModule.kt`, `CDATA/di/DataModule.kt`, `CNET/dto/AuthDtos.kt`, `shared/build.gradle.kts`, `shared/.../AppModules.kt`, `androidApp/build.gradle.kts`
- Delete: `feature/{login,register,passwordreset,settings}` (quedan vacíos tras los `git mv`, más sus `build.gradle.kts` y los 4 `*Module.kt`)

**Interfaces:**
- Consumes: plugins de la Tarea 1; módulos `core:common`, `core:network`, `core:database`, `core:designsystem` (todavía con sus paquetes viejos).
- Produces (los usan `shared`, `androidApp` y las Tareas 5 y 6):
  - `com.daviddelgado.agenda.feature.auth.domain.model.User`
  - `com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository`
  - `com.daviddelgado.agenda.feature.auth.domain.usecase.{LoginUseCase, RegisterUseCase, RestoreSessionUseCase, ObserveCurrentUserUseCase, LogoutUseCase, DeleteAccountUseCase, RegisterFcmTokenUseCase, RequestPasswordResetUseCase, ResetPasswordUseCase}`
  - `com.daviddelgado.agenda.feature.auth.domain.fcm.FcmTokenProvider`
  - `com.daviddelgado.agenda.feature.auth.domain.di.authDomainModule: Module`
  - `com.daviddelgado.agenda.feature.auth.data.di.authDataModule: Module` (incluye `expect val platformAuthDataModule`)
  - `com.daviddelgado.agenda.feature.auth.presentation.di.authPresentationModule: Module`
  - Pantallas: `feature.auth.presentation.login.LoginScreen`, `.register.RegisterScreen`, `.forgotpassword.ForgotPasswordScreen`, `.resetpassword.ResetPasswordScreen`, `.settings.SettingsScreen`

- [ ] **Step 1: `settings.gradle.kts`.** Sustituye las 4 líneas `include(":feature:login")`, `include(":feature:register")`, `include(":feature:passwordreset")` e `include(":feature:settings")` por:

```kotlin
include(":feature:auth:domain")
include(":feature:auth:data")
include(":feature:auth:presentation")
```

- [ ] **Step 2: build files.**

`feature/auth/domain/build.gradle.kts`
```kotlin
plugins {
    id("agenda.kmp.library")
}
```

`feature/auth/data/build.gradle.kts`
```kotlin
plugins {
    id("agenda.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.auth.domain)
            implementation(projects.core.common)
            implementation(projects.core.network)
            // Temporal: pasa a projects.feature.tasks.database en la Tarea 4. AuthRepositoryImpl
            // vacia las tareas locales al hacer logout / borrar la cuenta (excepcion documentada en la spec).
            implementation(projects.core.database)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
        }

        androidMain.dependencies {
            implementation(libs.firebase.messaging)
        }
    }
}

// El DSL de dependencias de Kotlin Multiplatform no resuelve bien un BOM (platform(...)); se
// añade con el DSL clasico de Gradle, igual que hace androidApp/build.gradle.kts.
dependencies {
    add("androidMainImplementation", platform(libs.firebase.bom))
}
```

`feature/auth/presentation/build.gradle.kts`
```kotlin
plugins {
    id("agenda.cmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.auth.domain)
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
        }
    }
}
```

- [ ] **Step 3: Dominio — sacar `User`, `AuthRepository` y los casos de uso de auth.**
  - Crea `ADOM/model/User.kt` y **mueve** a él la declaración `data class User(...)` de `CD/model/Models.kt`, con su KDoc si lo tiene, idéntica. Bórrala de `Models.kt`.
  - Crea `ADOM/repository/AuthRepository.kt` y **mueve** `interface AuthRepository { ... }` de `CD/repository/Repositories.kt`, idéntica y con su KDoc. Bórrala del original.
  - Para cada una de estas clases de `CD/usecase/UseCases.kt`, crea `ADOM/usecase/<Clase>.kt` con la clase idéntica (KDoc incluido) y bórrala del original: `LoginUseCase`, `RegisterUseCase`, `RestoreSessionUseCase`, `ObserveCurrentUserUseCase`, `LogoutUseCase`, `DeleteAccountUseCase`, `RegisterFcmTokenUseCase`, `RequestPasswordResetUseCase`, `ResetPasswordUseCase`.
  - Cada fichero nuevo empieza con `package com.daviddelgado.agenda.feature.auth.domain.<subpaquete>` y lleva los `import` que use (copiados del original y con los FQN nuevos: `...feature.auth.domain.model.User`, `...feature.auth.domain.repository.AuthRepository`). Deja los `import` de más: el Step 10 los limpia.
  - Mueve la interfaz FCM: `git mv FL/commonMain/kotlin/com/daviddelgado/agenda/feature/login/FcmTokenProvider.kt ADOM/fcm/FcmTokenProvider.kt` (con `mkdir -p` antes). En su KDoc, sustituye `[platformLoginModule]` por `` `platformAuthDataModule` (feature:auth:data) ``.
  - Crea `ADOM/di/AuthDomainModule.kt`:

```kotlin
package com.daviddelgado.agenda.feature.auth.domain.di

import com.daviddelgado.agenda.feature.auth.domain.usecase.DeleteAccountUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.LoginUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.LogoutUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterFcmTokenUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RequestPasswordResetUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.ResetPasswordUseCase
import com.daviddelgado.agenda.feature.auth.domain.usecase.RestoreSessionUseCase
import org.koin.dsl.module

val authDomainModule =
    module {
        factory { LoginUseCase(get()) }
        factory { RegisterUseCase(get()) }
        factory { DeleteAccountUseCase(get()) }
        factory { RegisterFcmTokenUseCase(get()) }
        factory { RequestPasswordResetUseCase(get()) }
        factory { ResetPasswordUseCase(get()) }
        factory { RestoreSessionUseCase(get()) }
        factory { LogoutUseCase(get()) }
        factory { ObserveCurrentUserUseCase(get()) }
    }
```
  - En `CD/di/DomainModule.kt` borra esas 9 líneas `factory { ... }` y sus 9 `import`.
  - Tests de dominio: `mkdir -p ADOMT/usecase` y `git mv` a esa carpeta, desde `CDT/usecase/`: `AuthUseCasesTest.kt`, `RegisterFcmTokenUseCaseTest.kt`, `RequestPasswordResetUseCaseTest.kt`, `ResetPasswordUseCaseTest.kt`, `FakeAuthRepository.kt`.

- [ ] **Step 4: Data.**

```bash
mkdir -p ADATA/repository ADATA/remote ADATA/dto ADATA/di ADATAT/repository ADATAT/fake \
  feature/auth/data/src/androidMain/kotlin/com/daviddelgado/agenda/feature/auth/data/di \
  feature/auth/data/src/iosMain/kotlin/com/daviddelgado/agenda/feature/auth/data/di
git mv CDATA/auth/AuthRepositoryImpl.kt ADATA/repository/AuthRepositoryImpl.kt
git mv CNET/api/AuthApi.kt ADATA/remote/AuthApi.kt
git mv CDATAT/auth/AuthRepositoryImplTest.kt ADATAT/repository/AuthRepositoryImplTest.kt
cp CDATAT/fake/FakeTaskDao.kt CDATAT/fake/FakePendingDeletionDao.kt CDATAT/fake/FakeTokenProvider.kt CDATAT/fake/MockHttpClient.kt ADATAT/fake/
git mv FL/androidMain/kotlin/com/daviddelgado/agenda/feature/login/LoginModule.android.kt \
  feature/auth/data/src/androidMain/kotlin/com/daviddelgado/agenda/feature/auth/data/di/AuthDataModule.android.kt
git mv FL/iosMain/kotlin/com/daviddelgado/agenda/feature/login/LoginModule.ios.kt \
  feature/auth/data/src/iosMain/kotlin/com/daviddelgado/agenda/feature/auth/data/di/AuthDataModule.ios.kt
```
(Sustituye las abreviaturas por sus rutas reales.)

  - En los dos `AuthDataModule.{android,ios}.kt`: renombra `actual val platformLoginModule` → `actual val platformAuthDataModule` y añade `import com.daviddelgado.agenda.feature.auth.domain.fcm.FcmTokenProvider`.
  - Crea `ADATA/dto/AuthRequests.kt` y **quita** estas 5 clases de `CNET/dto/AuthDtos.kt` (allí se quedan `RefreshRequest`, `AuthResponse`, `UserResponse` y `ErrorResponse`):

```kotlin
package com.daviddelgado.agenda.feature.auth.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

/** Token FCM de este dispositivo, para `POST /users/me/fcm-token` (ver ReminderJob en :server). */
@Serializable
data class FcmTokenRequest(val token: String)

/** Cuerpo de `POST /auth/forgot-password` (ver `ForgotPasswordRequest` del modulo :server). */
@Serializable
data class ForgotPasswordRequest(val email: String)

/** Cuerpo de `POST /auth/reset-password` (ver `ResetPasswordRequest` del modulo :server). */
@Serializable
data class ResetPasswordRequest(val email: String, val code: String, val newPassword: String)
```

  - Crea `ADATA/di/AuthDataModule.kt`:

```kotlin
package com.daviddelgado.agenda.feature.auth.data.di

import com.daviddelgado.agenda.feature.auth.data.remote.AuthApi
import com.daviddelgado.agenda.feature.auth.data.repository.AuthRepositoryImpl
import com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/** Cada plataforma provee su FcmTokenProvider real (Firebase en Android; iOS pendiente). */
expect val platformAuthDataModule: Module

val authDataModule =
    module {
        includes(platformAuthDataModule)
        single { AuthApi(get()) }
        single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get()) }
    }
```
  - En `CDATA/di/DataModule.kt` borra `single { AuthApi(get()) }`, `single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get()) }` y sus `import`.

- [ ] **Step 5: Presentation — mover pantallas.**

```bash
P=feature/auth/presentation/src
O=kotlin/com/daviddelgado/agenda/feature
N=kotlin/com/daviddelgado/agenda/feature/auth/presentation
mkdir -p $P/commonMain/$N/{login,register,forgotpassword,resetpassword,settings,di} \
         $P/commonTest/$N/{login,register,forgotpassword,resetpassword,settings} \
         $P/androidInstrumentedTest/$N/login
for f in LoginContract LoginScreen LoginViewModel; do git mv feature/login/src/commonMain/$O/login/$f.kt $P/commonMain/$N/login/; done
git mv feature/login/src/commonTest/$O/login/LoginViewModelTest.kt $P/commonTest/$N/login/
git mv feature/login/src/androidInstrumentedTest/$O/login/LoginScreenTest.kt $P/androidInstrumentedTest/$N/login/
for f in RegisterContract RegisterScreen RegisterViewModel; do git mv feature/register/src/commonMain/$O/register/$f.kt $P/commonMain/$N/register/; done
git mv feature/register/src/commonTest/$O/register/RegisterViewModelTest.kt $P/commonTest/$N/register/
for f in ForgotPasswordContract ForgotPasswordScreen ForgotPasswordViewModel; do git mv feature/passwordreset/src/commonMain/$O/passwordreset/$f.kt $P/commonMain/$N/forgotpassword/; done
for f in ResetPasswordContract ResetPasswordScreen ResetPasswordViewModel; do git mv feature/passwordreset/src/commonMain/$O/passwordreset/$f.kt $P/commonMain/$N/resetpassword/; done
git mv feature/passwordreset/src/commonTest/$O/passwordreset/ForgotPasswordViewModelTest.kt $P/commonTest/$N/forgotpassword/
git mv feature/passwordreset/src/commonTest/$O/passwordreset/ResetPasswordViewModelTest.kt $P/commonTest/$N/resetpassword/
git mv feature/passwordreset/src/commonTest/$O/passwordreset/FakeAuthRepository.kt $P/commonTest/$N/FakeAuthRepository.kt
for f in SettingsContract SettingsScreen SettingsViewModel; do git mv feature/settings/src/commonMain/$O/settings/$f.kt $P/commonMain/$N/settings/; done
git mv feature/settings/src/commonTest/$O/settings/SettingsViewModelTest.kt $P/commonTest/$N/settings/
git rm -q feature/login/src/commonMain/$O/login/LoginModule.kt feature/register/src/commonMain/$O/register/RegisterModule.kt \
  feature/passwordreset/src/commonMain/$O/passwordreset/PasswordResetModule.kt feature/settings/src/commonMain/$O/settings/SettingsModule.kt \
  feature/login/build.gradle.kts feature/register/build.gradle.kts feature/passwordreset/build.gradle.kts feature/settings/build.gradle.kts
git status --short feature/login feature/register feature/passwordreset feature/settings
```
Expected: el último `git status` no muestra nada (no queda ningún fichero versionado). Borra las carpetas vacías y los `build/` que queden: `rm -rf feature/login feature/register feature/passwordreset feature/settings`.

  - Crea `APRES/di/AuthPresentationModule.kt`:

```kotlin
package com.daviddelgado.agenda.feature.auth.presentation.di

import com.daviddelgado.agenda.feature.auth.presentation.forgotpassword.ForgotPasswordViewModel
import com.daviddelgado.agenda.feature.auth.presentation.login.LoginViewModel
import com.daviddelgado.agenda.feature.auth.presentation.register.RegisterViewModel
import com.daviddelgado.agenda.feature.auth.presentation.resetpassword.ResetPasswordViewModel
import com.daviddelgado.agenda.feature.auth.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authPresentationModule =
    module {
        viewModel { LoginViewModel(get(), get(), get()) }
        viewModel { RegisterViewModel(get()) }
        viewModel { ForgotPasswordViewModel(get()) }
        viewModel { ResetPasswordViewModel(get()) }
        viewModel { SettingsViewModel(get(), get(), get()) }
    }
```

- [ ] **Step 6: Paquetes.** Arregla la línea `package` de todo lo que se ha movido o creado:

```bash
tools/refactor/fix-packages.sh feature/auth
```

- [ ] **Step 7: Mapa de referencias** `tools/refactor/maps/auth.map`:

```
com.daviddelgado.agenda.domain.model.User com.daviddelgado.agenda.feature.auth.domain.model.User
com.daviddelgado.agenda.domain.repository.AuthRepository com.daviddelgado.agenda.feature.auth.domain.repository.AuthRepository
com.daviddelgado.agenda.domain.usecase.LoginUseCase com.daviddelgado.agenda.feature.auth.domain.usecase.LoginUseCase
com.daviddelgado.agenda.domain.usecase.RegisterUseCase com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterUseCase
com.daviddelgado.agenda.domain.usecase.RestoreSessionUseCase com.daviddelgado.agenda.feature.auth.domain.usecase.RestoreSessionUseCase
com.daviddelgado.agenda.domain.usecase.ObserveCurrentUserUseCase com.daviddelgado.agenda.feature.auth.domain.usecase.ObserveCurrentUserUseCase
com.daviddelgado.agenda.domain.usecase.LogoutUseCase com.daviddelgado.agenda.feature.auth.domain.usecase.LogoutUseCase
com.daviddelgado.agenda.domain.usecase.DeleteAccountUseCase com.daviddelgado.agenda.feature.auth.domain.usecase.DeleteAccountUseCase
com.daviddelgado.agenda.domain.usecase.RegisterFcmTokenUseCase com.daviddelgado.agenda.feature.auth.domain.usecase.RegisterFcmTokenUseCase
com.daviddelgado.agenda.domain.usecase.RequestPasswordResetUseCase com.daviddelgado.agenda.feature.auth.domain.usecase.RequestPasswordResetUseCase
com.daviddelgado.agenda.domain.usecase.ResetPasswordUseCase com.daviddelgado.agenda.feature.auth.domain.usecase.ResetPasswordUseCase
com.daviddelgado.agenda.feature.login.FcmTokenProvider com.daviddelgado.agenda.feature.auth.domain.fcm.FcmTokenProvider
com.daviddelgado.agenda.network.api.AuthApi com.daviddelgado.agenda.feature.auth.data.remote.AuthApi
com.daviddelgado.agenda.network.dto.LoginRequest com.daviddelgado.agenda.feature.auth.data.dto.LoginRequest
com.daviddelgado.agenda.network.dto.RegisterRequest com.daviddelgado.agenda.feature.auth.data.dto.RegisterRequest
com.daviddelgado.agenda.network.dto.FcmTokenRequest com.daviddelgado.agenda.feature.auth.data.dto.FcmTokenRequest
com.daviddelgado.agenda.network.dto.ForgotPasswordRequest com.daviddelgado.agenda.feature.auth.data.dto.ForgotPasswordRequest
com.daviddelgado.agenda.network.dto.ResetPasswordRequest com.daviddelgado.agenda.feature.auth.data.dto.ResetPasswordRequest
com.daviddelgado.agenda.data.auth.AuthRepositoryImpl com.daviddelgado.agenda.feature.auth.data.repository.AuthRepositoryImpl
com.daviddelgado.agenda.feature.login com.daviddelgado.agenda.feature.auth.presentation.login
com.daviddelgado.agenda.feature.register com.daviddelgado.agenda.feature.auth.presentation.register
com.daviddelgado.agenda.feature.settings com.daviddelgado.agenda.feature.auth.presentation.settings
com.daviddelgado.agenda.feature.passwordreset.ForgotPasswordScreen com.daviddelgado.agenda.feature.auth.presentation.forgotpassword.ForgotPasswordScreen
com.daviddelgado.agenda.feature.passwordreset.ResetPasswordScreen com.daviddelgado.agenda.feature.auth.presentation.resetpassword.ResetPasswordScreen
```

Ejecuta `tools/refactor/rename-fqn.sh tools/refactor/maps/auth.map`.

- [ ] **Step 8: Referencias del mismo paquete que ahora necesitan `import`** (ya se sabe que faltan):
  - `APRES/login/LoginViewModel.kt`, `APREST/login/LoginViewModelTest.kt`, `APRESI/login/LoginScreenTest.kt`: añade `import com.daviddelgado.agenda.feature.auth.domain.fcm.FcmTokenProvider`.
  - `APREST/forgotpassword/ForgotPasswordViewModelTest.kt` y `APREST/resetpassword/ResetPasswordViewModelTest.kt`: añade `import com.daviddelgado.agenda.feature.auth.presentation.FakeAuthRepository`.
  - `ADATAT/repository/AuthRepositoryImplTest.kt`: añade los `import` de `com.daviddelgado.agenda.feature.auth.data.fake.{FakeTaskDao, FakePendingDeletionDao, FakeTokenProvider, mockHttpClient}` que use (antes los importaba de `com.daviddelgado.agenda.data.fake`; el script **no** los reescribe porque ese paquete sigue existiendo en `core:data`). Cambia esos `import com.daviddelgado.agenda.data.fake.X` por `import com.daviddelgado.agenda.feature.auth.data.fake.X`. Haz lo mismo dentro de `ADATAT/fake/MockHttpClient.kt` si importa `FakeTokenProvider`.
  - `ADATA/remote/AuthApi.kt`: comprueba que importa `com.daviddelgado.agenda.network.apiCall`, `...network.dto.AuthResponse` y `...network.dto.UserResponse`, y los DTOs de petición desde `com.daviddelgado.agenda.feature.auth.data.dto.*`.

- [ ] **Step 9: `shared` y `androidApp`.**
  - `shared/build.gradle.kts`: sustituye las 4 líneas `implementation(projects.feature.login)`, `.register`, `.passwordreset` y `.settings` por:

```kotlin
            implementation(projects.feature.auth.domain)
            implementation(projects.feature.auth.data)
            implementation(projects.feature.auth.presentation)
```
  - `shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared/AppModules.kt`: sustituye los imports `...feature.auth.presentation.login.loginModule`, `...register.registerModule`, `...settings.settingsModule` y `com.daviddelgado.agenda.feature.passwordreset.passwordResetModule` (resultado del script) por:

```kotlin
import com.daviddelgado.agenda.feature.auth.data.di.authDataModule
import com.daviddelgado.agenda.feature.auth.domain.di.authDomainModule
import com.daviddelgado.agenda.feature.auth.presentation.di.authPresentationModule
```
  y en la lista `appModules` sustituye `loginModule, registerModule, passwordResetModule, settingsModule` por `authDomainModule, authDataModule, authPresentationModule` (colócalos justo después de `domainModule`). Actualiza el KDoc de `sharedModule`: "FcmTokenProvider, que es un tipo de presentacion (feature/login)" → "FcmTokenProvider (feature:auth:domain), cuya implementacion real depende de la plataforma (feature:auth:data)".
  - `androidApp/build.gradle.kts`: añade `implementation(projects.feature.auth.domain)` debajo de `implementation(projects.shared)` (`AgendaFirebaseMessagingService` usa `RegisterFcmTokenUseCase`).

- [ ] **Step 10: Limpiar imports y compilar.**

```bash
./gradlew ktlintFormat --no-daemon -q ; ./gradlew compileDebugKotlinAndroid compileDebugUnitTestKotlinAndroid compileDebugAndroidTestKotlinAndroid :androidApp:compileDebugKotlin --no-daemon
```
Expected: `BUILD SUCCESSFUL`. Si falla por `Unresolved reference`, se trata de una referencia del mismo paquete que se ha partido. Añade el `import` con el FQN nuevo, **sin cambiar la lógica**, y apunta el fichero en el informe de la tarea.

- [ ] **Step 11: Referencias obsoletas** (cubre `iosMain`):

```bash
git grep -n -E "agenda\.feature\.(login|register|passwordreset|settings)\b|agenda\.domain\.(model\.User|repository\.AuthRepository)\b|agenda\.network\.api\.AuthApi|platformLoginModule|agenda\.data\.auth\.AuthRepositoryImpl|agenda\.domain\.usecase\.(Login|Register|RestoreSession|ObserveCurrentUser|Logout|DeleteAccount|RegisterFcmToken|RequestPasswordReset|ResetPassword)UseCase" -- androidApp shared core feature ':!**/build/**'
```
Expected: sin resultados.

- [ ] **Step 12: Reglas de capas de auth:**

```bash
git grep -n -E "import .*feature\.auth\.data" -- 'feature/auth/presentation' ; git grep -n -E "import (io\.ktor|androidx\.room|androidx\.compose)" -- 'feature/auth/domain'
```
Expected: sin resultados.

- [ ] **Step 13: Verificación completa**

```bash
./gradlew check :androidApp:assembleDebug --no-daemon
```
Expected: `BUILD SUCCESSFUL`, y el mismo número de tests N (recuento del Step 1 de la Tarea 1).

- [ ] **Step 14: Commit**

```bash
git add -A
git commit -m "refactor(auth): feature/auth en capas domain, data y presentation

Login, registro, recuperacion de contraseña y ajustes pasan a feature:auth:presentation.
User, AuthRepository, casos de uso y FcmTokenProvider a feature:auth:domain; AuthApi,
DTOs de peticion, AuthRepositoryImpl y el FCM de cada plataforma a feature:auth:data.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: feature/streaks (domain, data, presentation)

Abreviaturas: `SDOM` = `feature/streaks/domain/src/commonMain/kotlin/com/daviddelgado/agenda/feature/streaks/domain`, `SDATA` = `feature/streaks/data/src/commonMain/kotlin/com/daviddelgado/agenda/feature/streaks/data`, `SDATAT` = el mismo con `commonTest`, `SPRES` = `feature/streaks/presentation/src/commonMain/kotlin/com/daviddelgado/agenda/feature/streaks/presentation`, `SPREST` = el mismo con `commonTest`. `CD`, `CDATA`, `CDATAT` como en la Tarea 2. `OLDS` = `feature/streaks/src` (módulo viejo) con el paquete `kotlin/com/daviddelgado/agenda/feature/streaks`.

**Files:**
- Create: `feature/streaks/{domain,data,presentation}/build.gradle.kts`, `SDOM/model/StreakSummary.kt`, `SDOM/repository/StreakRepository.kt`, `SDOM/usecase/ObserveStreakUseCase.kt`, `SDOM/di/StreaksDomainModule.kt`, `SDATA/di/StreaksDataModule.kt`, `SPRES/di/StreaksPresentationModule.kt`, `tools/refactor/maps/streaks.map`
- Move: `StreakRepositoryImpl.kt` (+ test), `Streaks{Contract,Screen,ViewModel}.kt` (+ test)
- Modify: `settings.gradle.kts`, `CD/model/Models.kt`, `CD/repository/Repositories.kt`, `CD/usecase/UseCases.kt`, `CD/di/DomainModule.kt`, `CDATA/di/DataModule.kt`, `shared/build.gradle.kts`, `AppModules.kt`
- Delete: el módulo viejo `feature/streaks` (su `build.gradle.kts` y `StreaksModule.kt`)

**Interfaces:**
- Consumes: `core:database` (`TaskDao`, paquete `com.daviddelgado.agenda.database` hasta la Tarea 4).
- Produces: `com.daviddelgado.agenda.feature.streaks.domain.{model.StreakSummary, repository.StreakRepository, usecase.ObserveStreakUseCase, di.streaksDomainModule}`, `feature.streaks.data.{repository.StreakRepositoryImpl, di.streaksDataModule}`, `feature.streaks.presentation.{streaks.StreaksScreen, di.streaksPresentationModule}`.

> **Ojo con el orden:** el módulo nuevo `:feature:streaks:presentation` vive **dentro** de la carpeta del módulo viejo `:feature:streaks` (`feature/streaks/`). Primero se vacía el viejo (`git mv` de sus fuentes y `git rm` de su `build.gradle.kts`) y después se crean los submódulos.

- [ ] **Step 1: Vaciar el módulo viejo** a un directorio temporal dentro del repo, para no pisarlo:

```bash
O=kotlin/com/daviddelgado/agenda/feature/streaks
mkdir -p .streaks-tmp/main .streaks-tmp/test
for f in StreaksContract StreaksScreen StreaksViewModel; do git mv feature/streaks/src/commonMain/$O/$f.kt .streaks-tmp/main/; done
git mv feature/streaks/src/commonTest/$O/StreaksViewModelTest.kt .streaks-tmp/test/
git rm -q feature/streaks/src/commonMain/$O/StreaksModule.kt feature/streaks/build.gradle.kts
git status --short feature/streaks ; rm -rf feature/streaks
```
Expected: el `git status` no muestra nada dentro de `feature/streaks`.

- [ ] **Step 2: `settings.gradle.kts`.** Sustituye `include(":feature:streaks")` por:

```kotlin
include(":feature:streaks:domain")
include(":feature:streaks:data")
include(":feature:streaks:presentation")
```

- [ ] **Step 3: build files.**

`feature/streaks/domain/build.gradle.kts`
```kotlin
plugins {
    id("agenda.kmp.library")
}
```

`feature/streaks/data/build.gradle.kts`
```kotlin
plugins {
    id("agenda.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.streaks.domain)
            // Temporal: pasa a projects.feature.tasks.database en la Tarea 4. La racha se calcula
            // con las fechas de las tareas completadas (excepcion documentada en la spec).
            implementation(projects.core.database)
        }
    }
}
```

`feature/streaks/presentation/build.gradle.kts`
```kotlin
plugins {
    id("agenda.cmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.streaks.domain)
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
        }
    }
}
```

- [ ] **Step 4: Dominio.** Mueve, idénticas y con su KDoc, borrándolas del fichero original:
  - `data class StreakSummary` de `CD/model/Models.kt` → `SDOM/model/StreakSummary.kt`
  - `interface StreakRepository` de `CD/repository/Repositories.kt` → `SDOM/repository/StreakRepository.kt`
  - `class ObserveStreakUseCase` de `CD/usecase/UseCases.kt` → `SDOM/usecase/ObserveStreakUseCase.kt`
  - Crea `SDOM/di/StreaksDomainModule.kt`:

```kotlin
package com.daviddelgado.agenda.feature.streaks.domain.di

import com.daviddelgado.agenda.feature.streaks.domain.usecase.ObserveStreakUseCase
import org.koin.dsl.module

val streaksDomainModule =
    module {
        factory { ObserveStreakUseCase(get()) }
    }
```
  - En `CD/di/DomainModule.kt` borra `factory { ObserveStreakUseCase(get()) }` y su `import`.

- [ ] **Step 5: Data.**

```bash
mkdir -p SDATA/repository SDATA/di SDATAT/repository SDATAT/fake
git mv CDATA/streak/StreakRepositoryImpl.kt SDATA/repository/StreakRepositoryImpl.kt
git mv CDATAT/streak/StreakRepositoryImplTest.kt SDATAT/repository/StreakRepositoryImplTest.kt
cp CDATAT/fake/FakeTaskDao.kt SDATAT/fake/FakeTaskDao.kt
```
  - Crea `SDATA/di/StreaksDataModule.kt`:

```kotlin
package com.daviddelgado.agenda.feature.streaks.data.di

import com.daviddelgado.agenda.feature.streaks.data.repository.StreakRepositoryImpl
import com.daviddelgado.agenda.feature.streaks.domain.repository.StreakRepository
import org.koin.dsl.module

val streaksDataModule =
    module {
        single<StreakRepository> { StreakRepositoryImpl(get()) }
    }
```
  - En `CDATA/di/DataModule.kt` borra `single<StreakRepository> { StreakRepositoryImpl(get()) }` y sus `import`.
  - En `SDATAT/repository/StreakRepositoryImplTest.kt`, cambia `import com.daviddelgado.agenda.data.fake.FakeTaskDao` → `import com.daviddelgado.agenda.feature.streaks.data.fake.FakeTaskDao`.

- [ ] **Step 6: Presentation.**

```bash
mkdir -p SPRES/streaks SPRES/di SPREST/streaks
git mv .streaks-tmp/main/*.kt SPRES/streaks/
git mv .streaks-tmp/test/StreaksViewModelTest.kt SPREST/streaks/
rm -rf .streaks-tmp
```
  - Crea `SPRES/di/StreaksPresentationModule.kt`:

```kotlin
package com.daviddelgado.agenda.feature.streaks.presentation.di

import com.daviddelgado.agenda.feature.streaks.presentation.streaks.StreaksViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val streaksPresentationModule =
    module {
        viewModel { StreaksViewModel(get()) }
    }
```

- [ ] **Step 7: Paquetes y referencias.** `tools/refactor/fix-packages.sh feature/streaks` y crea `tools/refactor/maps/streaks.map`:

```
com.daviddelgado.agenda.domain.model.StreakSummary com.daviddelgado.agenda.feature.streaks.domain.model.StreakSummary
com.daviddelgado.agenda.domain.repository.StreakRepository com.daviddelgado.agenda.feature.streaks.domain.repository.StreakRepository
com.daviddelgado.agenda.domain.usecase.ObserveStreakUseCase com.daviddelgado.agenda.feature.streaks.domain.usecase.ObserveStreakUseCase
com.daviddelgado.agenda.data.streak.StreakRepositoryImpl com.daviddelgado.agenda.feature.streaks.data.repository.StreakRepositoryImpl
com.daviddelgado.agenda.feature.streaks.StreaksScreen com.daviddelgado.agenda.feature.streaks.presentation.streaks.StreaksScreen
```
Ejecuta `tools/refactor/rename-fqn.sh tools/refactor/maps/streaks.map`.

- [ ] **Step 8: `shared`.** En `shared/build.gradle.kts` sustituye `implementation(projects.feature.streaks)` por las tres líneas `implementation(projects.feature.streaks.domain)`, `implementation(projects.feature.streaks.data)` e `implementation(projects.feature.streaks.presentation)`. En `AppModules.kt` sustituye el `import ...feature.streaks.streaksModule` por los imports de `streaksDomainModule`, `streaksDataModule` y `streaksPresentationModule`, y en la lista `streaksModule` por esos tres.

- [ ] **Step 9: Compilar.** El mismo comando del Step 10 de la Tarea 2. Referencias del mismo paquete que ya se sabe que faltan: ninguna (las pantallas de streaks siguen juntas en `presentation.streaks`).

- [ ] **Step 10: Referencias obsoletas:**

```bash
git grep -n -E "agenda\.feature\.streaks\.(StreaksScreen|streaksModule)|agenda\.domain\.(model\.StreakSummary|repository\.StreakRepository|usecase\.ObserveStreakUseCase)|agenda\.data\.streak\b" -- androidApp shared core feature ':!**/build/**'
```
Expected: sin resultados.

- [ ] **Step 11: Verificación completa**, igual que en la Tarea 2 (Step 13: `check` + `assembleDebug` y el mismo N).

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -m "refactor(streaks): feature/streaks en capas domain, data y presentation

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: feature/tasks (domain, data, database, presentation)

Abreviaturas: `TDOM`, `TDOMT`, `TDATA`, `TDATAT`, `TDB`, `TPRES`, `TPREST`, `TPRESI` = `feature/tasks/<domain|data|database|presentation>/src/<commonMain|commonTest|androidInstrumentedTest>/kotlin/com/daviddelgado/agenda/feature/tasks/<capa>`. `CD`, `CDT`, `CDATA`, `CDATAT`, `CNET` como antes. `CDB` = `core/database`.

**Files:**
- Create: `feature/tasks/{domain,data,database,presentation}/build.gradle.kts`, `TDOM/model/*.kt`, `TDOM/repository/TaskRepository.kt`, `TDOM/usecase/*.kt` (9), `TDOM/di/TasksDomainModule.kt`, `TDATA/di/TasksDataModule.kt`, `TDB/di/TasksDatabaseModule.kt` (+ `.android`/`.ios`), `TPRES/di/TasksPresentationModule.kt`, `tools/refactor/maps/tasks.map`
- Move: todo `core/database` (incluido `schemas/`), el resto de `core/domain` que no es transversal, `TaskApi`, `TaskDtos`, `WebSocketService`, mappers, `TaskRepositoryImpl` (+ tests y fakes), las pantallas de `feature/tasks` y `feature/calendar` (+ tests)
- Modify: `settings.gradle.kts`, `feature/auth/data/build.gradle.kts`, `feature/streaks/data/build.gradle.kts`, `CDATA/di/DataModule*.kt`, `core/data/build.gradle.kts`, `core/domain` (queda vacío de modelos), `shared/build.gradle.kts`, `AppModules.kt`
- Delete: `core/database`, `feature/calendar` y el `feature/tasks` viejo (sus `build.gradle.kts` y `*Module.kt`)

**Interfaces:**
- Produces: `com.daviddelgado.agenda.feature.tasks.domain.model.{Task, TaskCategory, TaskPriority, ReminderFrequency, IncrementUnit, IncrementConfig}`, `...domain.repository.TaskRepository`, `...domain.usecase.{ObserveTasksUseCase, UpsertTaskUseCase, GenerateTaskRepetitionsUseCase, DeleteTaskUseCase, DeleteTasksUseCase, DeleteAllTasksUseCase, ToggleTaskCompletionUseCase, SyncTasksUseCase, ObserveTaskChangesUseCase}`, `...domain.di.tasksDomainModule`; `...database.{AgendaDatabase, AgendaDatabaseConstructor, DatabaseFactory, buildAgendaDatabase}`, `...database.dao.{TaskDao, PendingDeletionDao}`, `...database.entity.{TaskEntity, PendingDeletionEntity}`, `...database.di.tasksDatabaseModule` (incluye `expect val platformTasksDatabaseModule`); `...data.remote.TaskApi`, `...data.dto.{TaskDto, BulkDeleteRequest, DeletedCountResponse}`, `...data.websocket.WebSocketService`, `...data.mapper.{toDomain, toDto, toEntity}`, `...data.repository.TaskRepositoryImpl`, `...data.di.tasksDataModule`; `...presentation.tasks.TasksScreen`, `...presentation.calendar.CalendarScreen`, `...presentation.di.tasksPresentationModule`.

- [ ] **Step 1: Guarda el esquema de referencia de Room**, para compararlo al final:

```bash
mkdir -p .room-ref && cp core/database/schemas/com.daviddelgado.agenda.database.AgendaDatabase/*.json .room-ref/ && sha256sum .room-ref/*.json
```
Apunta los hashes.

- [ ] **Step 2: Vaciar los módulos viejos `feature/tasks` y `feature/calendar`** a un temporal (como en la Tarea 3):

```bash
O=kotlin/com/daviddelgado/agenda/feature
mkdir -p .tasks-tmp/{tasks,calendar}/{main,test,itest}
for f in TasksContract TasksScreen TasksViewModel; do git mv feature/tasks/src/commonMain/$O/tasks/$f.kt .tasks-tmp/tasks/main/; done
git mv feature/tasks/src/commonTest/$O/tasks/TasksViewModelTest.kt feature/tasks/src/commonTest/$O/tasks/FakeTaskRepository.kt .tasks-tmp/tasks/test/
git mv feature/tasks/src/androidInstrumentedTest/$O/tasks/TasksScreenTest.kt .tasks-tmp/tasks/itest/
for f in CalendarContract CalendarLayout CalendarScreen CalendarViewModel; do git mv feature/calendar/src/commonMain/$O/calendar/$f.kt .tasks-tmp/calendar/main/; done
git mv feature/calendar/src/commonTest/$O/calendar/CalendarViewModelTest.kt feature/calendar/src/commonTest/$O/calendar/CalendarLayoutTest.kt .tasks-tmp/calendar/test/
git mv feature/calendar/src/androidInstrumentedTest/$O/calendar/CalendarScreenTest.kt .tasks-tmp/calendar/itest/
git rm -q feature/tasks/src/commonMain/$O/tasks/TasksModule.kt feature/calendar/src/commonMain/$O/calendar/CalendarModule.kt feature/tasks/build.gradle.kts feature/calendar/build.gradle.kts
git status --short feature/tasks feature/calendar ; rm -rf feature/tasks feature/calendar
```
Expected: el `git status` no muestra nada.

- [ ] **Step 3: `core/database` → `feature/tasks/database`**, entero (fuentes y `schemas/`):

```bash
mkdir -p feature/tasks && git mv core/database feature/tasks/database && rm -rf feature/tasks/database/build
D=feature/tasks/database/src
N=kotlin/com/daviddelgado/agenda/feature/tasks/database
for ss in commonMain androidMain iosMain; do mkdir -p $D/$ss/$N; git mv $D/$ss/kotlin/com/daviddelgado/agenda/database/* $D/$ss/$N/; done
mkdir -p $D/commonMain/$N/dao $D/commonMain/$N/entity $D/commonMain/$N/di $D/androidMain/$N/di $D/iosMain/$N/di
git mv $D/commonMain/$N/TaskDao.kt $D/commonMain/$N/PendingDeletionDao.kt $D/commonMain/$N/dao/
git mv $D/commonMain/$N/TaskEntity.kt $D/commonMain/$N/PendingDeletionEntity.kt $D/commonMain/$N/entity/
git mv feature/tasks/database/schemas/com.daviddelgado.agenda.database.AgendaDatabase \
       feature/tasks/database/schemas/com.daviddelgado.agenda.feature.tasks.database.AgendaDatabase
```
`feature/tasks/database/build.gradle.kts` pasa a ser:

```kotlin
plugins {
    id("agenda.kmp.library")
    id("agenda.room")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
```
(Deja de depender de `core:domain`: comprueba con `git grep -n "agenda.domain" feature/tasks/database` que ninguna clase de la BD lo importa. Si alguna lo importa, vuelve a añadir la dependencia a `projects.feature.tasks.domain` y apúntalo en el informe.)

  - Crea el DI de la BD. Sale de `platformDataModule` (`single { DatabaseFactory(...) }`) y de `dataModule` (`buildAgendaDatabase` y los dos DAOs).

`TDB/di/TasksDatabaseModule.kt` (en `commonMain`):
```kotlin
package com.daviddelgado.agenda.feature.tasks.database.di

import com.daviddelgado.agenda.feature.tasks.database.AgendaDatabase
import com.daviddelgado.agenda.feature.tasks.database.buildAgendaDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/** Cada plataforma provee su DatabaseFactory (en Android necesita el Context). */
expect val platformTasksDatabaseModule: Module

val tasksDatabaseModule =
    module {
        includes(platformTasksDatabaseModule)
        single { buildAgendaDatabase(get()) }
        single { get<AgendaDatabase>().taskDao() }
        single { get<AgendaDatabase>().pendingDeletionDao() }
    }
```

`feature/tasks/database/src/androidMain/kotlin/com/daviddelgado/agenda/feature/tasks/database/di/TasksDatabaseModule.android.kt`:
```kotlin
package com.daviddelgado.agenda.feature.tasks.database.di

import com.daviddelgado.agenda.feature.tasks.database.DatabaseFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformTasksDatabaseModule: Module =
    module {
        single { DatabaseFactory(androidContext()) }
    }
```

`feature/tasks/database/src/iosMain/kotlin/com/daviddelgado/agenda/feature/tasks/database/di/TasksDatabaseModule.ios.kt`:
```kotlin
package com.daviddelgado.agenda.feature.tasks.database.di

import com.daviddelgado.agenda.feature.tasks.database.DatabaseFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformTasksDatabaseModule: Module =
    module {
        single { DatabaseFactory() }
    }
```
  - En `CDATA/di/DataModule.kt` borra `single { buildAgendaDatabase(get()) }`, las dos líneas de los DAO y sus `import`. En `DataModule.android.kt` y `DataModule.ios.kt` borra `single { DatabaseFactory(...) }` y su `import`.
  - Referencias del mismo paquete que se parten: `AgendaDatabase.kt` necesita `import ...database.dao.{TaskDao, PendingDeletionDao}` e `import ...database.entity.{TaskEntity, PendingDeletionEntity}`; `TaskDao.kt` y `PendingDeletionDao.kt` necesitan importar su entidad de `...database.entity`.

- [ ] **Step 4: Dominio de tareas.**

`feature/tasks/domain/build.gradle.kts`:
```kotlin
plugins {
    id("agenda.kmp.library")
}
```
  - `mkdir -p TDOM/model TDOM/repository TDOM/usecase TDOM/di TDOMT/model TDOMT/usecase`
  - Lo que queda en `CD/model/Models.kt` (`TaskCategory`, `TaskPriority`, `ReminderFrequency`, `IncrementUnit`, `IncrementConfig`, `Task`) es todo de tareas: `git mv CD/model/Models.kt TDOM/model/TaskModels.kt` (conserva el historial; el fichero no se parte).
  - `git mv CD/repository/Repositories.kt TDOM/repository/TaskRepository.kt` (solo queda `TaskRepository`; comprueba que no queda nada más).
  - Para cada clase que queda en `CD/usecase/UseCases.kt` (`ObserveTasksUseCase`, `UpsertTaskUseCase`, `GenerateTaskRepetitionsUseCase`, `DeleteTaskUseCase`, `DeleteTasksUseCase`, `DeleteAllTasksUseCase`, `ToggleTaskCompletionUseCase`, `SyncTasksUseCase`, `ObserveTaskChangesUseCase`): crea `TDOM/usecase/<Clase>.kt` con la clase idéntica. Si queda algo a nivel de fichero (constantes privadas, funciones privadas), va al fichero de la clase que lo usa. Luego `git rm CD/usecase/UseCases.kt`.
  - Crea `TDOM/di/TasksDomainModule.kt` con los 9 `factory` que quedan en `DomainModule.kt` (copia las líneas tal cual) y el nombre `val tasksDomainModule`. Después `git rm CD/di/DomainModule.kt`.
  - Tests: `git mv CDT/model/TaskTest.kt TDOMT/model/`; `git mv CDT/usecase/{TaskUseCasesTest,GenerateTaskRepetitionsUseCaseTest,FakeTaskRepository}.kt TDOMT/usecase/`.
  - `core/domain` se queda sin fuentes de dominio de negocio. Compruébalo con `git ls-files core/domain`: solo debe quedar `build.gradle.kts`. El módulo se mantiene, porque la Tarea 5 lo llena con `logger/` y `util/`.

- [ ] **Step 5: Data de tareas.**

`feature/tasks/data/build.gradle.kts`:
```kotlin
plugins {
    id("agenda.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.tasks.domain)
            implementation(projects.feature.tasks.database)
            implementation(projects.core.common)
            implementation(projects.core.network)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
        }
    }
}
```

```bash
mkdir -p TDATA/{remote,dto,websocket,mapper,repository,di} TDATAT/{mapper,repository,fake}
git mv CNET/api/TaskApi.kt TDATA/remote/TaskApi.kt
git mv CNET/dto/TaskDtos.kt TDATA/dto/TaskDtos.kt
git mv CNET/WebSocketService.kt TDATA/websocket/WebSocketService.kt
git mv CDATA/task/TaskMapper.kt CDATA/task/TaskDtoMapper.kt TDATA/mapper/
git mv CDATA/task/TaskRepositoryImpl.kt TDATA/repository/
git mv CDATAT/task/TaskMapperTest.kt CDATAT/task/TaskDtoMapperTest.kt TDATAT/mapper/
git mv CDATAT/task/TaskRepositoryImplTest.kt TDATAT/repository/
git mv CDATAT/fake/FakeTaskDao.kt CDATAT/fake/FakePendingDeletionDao.kt TDATAT/fake/
cp CDATAT/fake/MockHttpClient.kt CDATAT/fake/FakeTokenProvider.kt TDATAT/fake/
```
  - Crea `TDATA/di/TasksDataModule.kt`:

```kotlin
package com.daviddelgado.agenda.feature.tasks.data.di

import com.daviddelgado.agenda.feature.tasks.data.remote.TaskApi
import com.daviddelgado.agenda.feature.tasks.data.repository.TaskRepositoryImpl
import com.daviddelgado.agenda.feature.tasks.domain.repository.TaskRepository
import org.koin.dsl.module

val tasksDataModule =
    module {
        single { TaskApi(get()) }
        single<TaskRepository> { TaskRepositoryImpl(get(), get(), get()) }
    }
```
  - En `CDATA/di/DataModule.kt` borra `single { TaskApi(get()) }`, `single<TaskRepository> { ... }` y sus `import`. Después de esto, a `dataModule` solo le quedan `includes(platformDataModule)`, el `TokenProvider` y el `HttpClient`.
  - `core/data/build.gradle.kts`: quita `implementation(projects.core.domain)` e `implementation(projects.core.database)` si ya no los usa ninguna clase de `core/data` (compruébalo con `git grep -n -E "agenda\.(domain|database)\." core/data`: sin resultados → se quitan).
  - Referencias del mismo paquete que se parten: `TaskRepositoryImpl.kt` usa `toDomain`/`toDto`/`toEntity`, que antes estaban en su mismo paquete `data.task` → añade `import com.daviddelgado.agenda.feature.tasks.data.mapper.toDomain`, `...mapper.toDto` y `...mapper.toEntity` (los que use). `TaskApi.kt` usaba `WebSocketService` por import (el mapa lo cubre). Los tests de `TDATAT/mapper` y `TDATAT/repository` que usen fakes: cambia `import com.daviddelgado.agenda.data.fake.X` → `import com.daviddelgado.agenda.feature.tasks.data.fake.X`.

- [ ] **Step 6: `auth:data` y `streaks:data` apuntan a la BD nueva.** En `feature/auth/data/build.gradle.kts` y en `feature/streaks/data/build.gradle.kts` sustituye `implementation(projects.core.database)` por `implementation(projects.feature.tasks.database)` y actualiza el comentario: "Excepcion documentada en la spec (§3): …", quitando la palabra "Temporal". Los fakes copiados en `feature/auth/data/.../fake/FakeTaskDao.kt` y `feature/streaks/data/.../fake/FakeTaskDao.kt` los actualiza el mapa (sus `import` de `TaskDao`/`TaskEntity`).

- [ ] **Step 7: Presentation.**

`feature/tasks/presentation/build.gradle.kts`:
```kotlin
plugins {
    id("agenda.cmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.tasks.domain)
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
        }
    }
}
```

```bash
P=feature/tasks/presentation/src; N=kotlin/com/daviddelgado/agenda/feature/tasks/presentation
mkdir -p $P/commonMain/$N/{tasks,calendar,di} $P/commonTest/$N/{tasks,calendar} $P/androidInstrumentedTest/$N/{tasks,calendar}
for s in tasks calendar; do
  git mv .tasks-tmp/$s/main/*.kt $P/commonMain/$N/$s/
  git mv .tasks-tmp/$s/test/*.kt $P/commonTest/$N/$s/
  git mv .tasks-tmp/$s/itest/*.kt $P/androidInstrumentedTest/$N/$s/
done
rm -rf .tasks-tmp
```
  - Crea `TPRES/di/TasksPresentationModule.kt`:

```kotlin
package com.daviddelgado.agenda.feature.tasks.presentation.di

import com.daviddelgado.agenda.feature.tasks.presentation.calendar.CalendarViewModel
import com.daviddelgado.agenda.feature.tasks.presentation.tasks.TasksViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tasksPresentationModule =
    module {
        viewModel { TasksViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel { CalendarViewModel(get()) }
    }
```
  - Referencia conocida: `TasksScreenTest.kt` (androidInstrumentedTest) usaba `FakeTaskRepository` de `commonTest` del mismo paquete **solo si** no declara uno propio. Si el compilador no lo encuentra, el `androidInstrumentedTest` no hereda `commonTest`, y así era también antes: no toques nada y revisa el error real.

- [ ] **Step 8: `settings.gradle.kts`.** Sustituye `include(":core:database")`, `include(":feature:calendar")` e `include(":feature:tasks")` por:

```kotlin
include(":feature:tasks:domain")
include(":feature:tasks:data")
include(":feature:tasks:database")
include(":feature:tasks:presentation")
```

- [ ] **Step 9: Paquetes y referencias.** `tools/refactor/fix-packages.sh feature/tasks` y crea `tools/refactor/maps/tasks.map`:

```
com.daviddelgado.agenda.domain.model com.daviddelgado.agenda.feature.tasks.domain.model
com.daviddelgado.agenda.domain.repository.TaskRepository com.daviddelgado.agenda.feature.tasks.domain.repository.TaskRepository
com.daviddelgado.agenda.domain.usecase com.daviddelgado.agenda.feature.tasks.domain.usecase
com.daviddelgado.agenda.domain.di.domainModule com.daviddelgado.agenda.feature.tasks.domain.di.tasksDomainModule
com.daviddelgado.agenda.database.TaskDao com.daviddelgado.agenda.feature.tasks.database.dao.TaskDao
com.daviddelgado.agenda.database.PendingDeletionDao com.daviddelgado.agenda.feature.tasks.database.dao.PendingDeletionDao
com.daviddelgado.agenda.database.TaskEntity com.daviddelgado.agenda.feature.tasks.database.entity.TaskEntity
com.daviddelgado.agenda.database.PendingDeletionEntity com.daviddelgado.agenda.feature.tasks.database.entity.PendingDeletionEntity
com.daviddelgado.agenda.database com.daviddelgado.agenda.feature.tasks.database
com.daviddelgado.agenda.network.api.TaskApi com.daviddelgado.agenda.feature.tasks.data.remote.TaskApi
com.daviddelgado.agenda.network.dto.TaskDto com.daviddelgado.agenda.feature.tasks.data.dto.TaskDto
com.daviddelgado.agenda.network.dto.BulkDeleteRequest com.daviddelgado.agenda.feature.tasks.data.dto.BulkDeleteRequest
com.daviddelgado.agenda.network.dto.DeletedCountResponse com.daviddelgado.agenda.feature.tasks.data.dto.DeletedCountResponse
com.daviddelgado.agenda.network.WebSocketService com.daviddelgado.agenda.feature.tasks.data.websocket.WebSocketService
com.daviddelgado.agenda.data.task.TaskRepositoryImpl com.daviddelgado.agenda.feature.tasks.data.repository.TaskRepositoryImpl
com.daviddelgado.agenda.data.task com.daviddelgado.agenda.feature.tasks.data.mapper
com.daviddelgado.agenda.feature.tasks.TasksScreen com.daviddelgado.agenda.feature.tasks.presentation.tasks.TasksScreen
com.daviddelgado.agenda.feature.calendar.CalendarScreen com.daviddelgado.agenda.feature.tasks.presentation.calendar.CalendarScreen
```
Ejecuta `tools/refactor/rename-fqn.sh tools/refactor/maps/tasks.map`. Después vuelve a ejecutar `tools/refactor/fix-packages.sh feature/tasks`: el mapa ha reescrito algunos `package` con un prefijo de paquete, y esta segunda pasada los deja igual que su ruta.

- [ ] **Step 10: `shared`.** En `shared/build.gradle.kts` sustituye `implementation(projects.feature.calendar)` e `implementation(projects.feature.tasks)` por `implementation(projects.feature.tasks.domain)`, `.data`, `.database` y `.presentation`, y quita `implementation(projects.core.domain)` si `shared` ya no importa nada de `com.daviddelgado.agenda.domain`. En `AppModules.kt`: los imports de `tasksDomainModule` (el mapa lo ha puesto en el sitio de `domainModule`), `tasksDataModule`, `tasksDatabaseModule` y `tasksPresentationModule`; quita `calendarModule` y `tasksModule`. La lista `appModules` queda:

```kotlin
val appModules: List<Module> =
    listOf(
        dataModule,
        tasksDatabaseModule,
        authDomainModule,
        authDataModule,
        authPresentationModule,
        tasksDomainModule,
        tasksDataModule,
        tasksPresentationModule,
        streaksDomainModule,
        streaksDataModule,
        streaksPresentationModule,
        sharedModule,
    )
```

- [ ] **Step 11: Compilar**, con el mismo comando que en la Tarea 2 (Step 10), y arreglar las referencias del mismo paquete que se hayan partido añadiendo `import`.

- [ ] **Step 12: Room — la identidad del esquema no cambia.**

```bash
./gradlew :feature:tasks:database:kspDebugKotlinAndroid --no-daemon -q
ls feature/tasks/database/schemas/
sha256sum feature/tasks/database/schemas/com.daviddelgado.agenda.feature.tasks.database.AgendaDatabase/*.json .room-ref/*.json
git status --short feature/tasks/database/schemas
```
Expected: solo existe la carpeta `com.daviddelgado.agenda.feature.tasks.database.AgendaDatabase`, los hashes de `1.json`–`4.json` coinciden con los del Step 1, y `git status` solo muestra los renombrados (`R`), sin modificaciones (`M`) ni ficheros nuevos (`??`). **Si `4.json` difiere, PARA y avisa: no hagas commit.** Si todo cuadra, borra `.room-ref`.

- [ ] **Step 13: Referencias obsoletas:**

```bash
git grep -n -E "agenda\.(domain\.(model|repository|usecase|di)|database\.|data\.(task|streak)\b|network\.(api|dto\.Task|dto\.BulkDelete|dto\.DeletedCount|WebSocketService)|feature\.(calendar|tasks)\.(Tasks|Calendar)(Screen|Module|ViewModel))|platformDataModule.*DatabaseFactory|calendarModule|\btasksModule\b" -- androidApp shared core feature ':!**/build/**'
```
Expected: sin resultados.

- [ ] **Step 14: Reglas de capas** (todas las features hasta ahora):

```bash
git grep -n -E "import .*feature\.[a-z]+\.(data|database)\." -- 'feature/*/presentation' ; git grep -n -E "import (io\.ktor|androidx\.room|androidx\.compose)" -- 'feature/*/domain'
```
Expected: sin resultados.

- [ ] **Step 15: Verificación completa**: `check` + `assembleDebug` y el mismo N. Además, con el emulador levantado (`ESTADO_PROYECTO.md` explica cómo arrancarlo), ejecuta los tests de UI de las pantallas movidas:

```bash
./gradlew :feature:tasks:presentation:connectedDebugAndroidTest :feature:auth:presentation:connectedDebugAndroidTest --no-daemon
```
Expected: `BUILD SUCCESSFUL`. Si no hay emulador disponible, apúntalo en el informe como **no verificado** (no lo des por bueno).

- [ ] **Step 16: Commit**

```bash
git add -A
git commit -m "refactor(tasks): feature/tasks en capas domain, data, database y presentation

core:database pasa entero a feature:tasks:database; la carpeta de esquemas de Room se
renombra al FQN nuevo de AgendaDatabase (4.json identico, misma identityHash).
auth:data y streaks:data dependen de tasks:database (excepciones documentadas en la spec).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: consolidar core (domain, data, presentation, designsystem)

**Files:**
- Create: módulo `core/presentation` (`build.gradle.kts` + fuentes movidas), `core/data/src/commonMain/kotlin/com/daviddelgado/agenda/core/data/session/TokenProvider.kt`, `tools/refactor/maps/core.map`
- Move: `core/common` → `core/domain` (logger, util) + `core/presentation` (mvi); `core/network` → `core/data` (networking); `core/data` `secure/` y `auth/TokenProviderImpl.kt` → `core/data/.../session/`; `core/data/.../di/`; `core/designsystem` → paquete `core.designsystem`
- Modify: `settings.gradle.kts`, `core/{domain,data,designsystem}/build.gradle.kts`, el `build.gradle.kts` de todos los `feature/*/*`, de `shared` y de `androidApp`
- Delete: `core/common`, `core/network`

**Interfaces:**
- Produces:
  - `com.daviddelgado.agenda.core.domain.logger.AgendaLogger`
  - `com.daviddelgado.agenda.core.domain.util.randomEntityId`
  - `com.daviddelgado.agenda.core.presentation.mvi.{UiState, UiIntent, UiEffect, MviViewModel}`
  - `com.daviddelgado.agenda.core.data.networking.{NetworkConfig, ProductionConfig, ApiException, apiCall, installAgendaPlugins, createHttpClient}`
  - `com.daviddelgado.agenda.core.data.networking.dto.{RefreshRequest, AuthResponse, UserResponse, ErrorResponse}`
  - `com.daviddelgado.agenda.core.data.session.{TokenProvider, TokenProviderImpl, SecureStorage}`
  - `com.daviddelgado.agenda.core.data.di.{coreDataModule, platformCoreDataModule}`
  - `com.daviddelgado.agenda.core.designsystem.{component, theme}.*`

- [ ] **Step 1: `core/presentation`.** `settings.gradle.kts`: sustituye `include(":core:common")` e `include(":core:network")` por `include(":core:presentation")`.

`core/presentation/build.gradle.kts`:
```kotlin
plugins {
    id("agenda.cmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.lifecycle.viewmodel)
            implementation(projects.core.domain)
        }
    }
}
```
(`api` porque los `ViewModel` de las features heredan de `MviViewModel`, que extiende `androidx.lifecycle.ViewModel`.)

- [ ] **Step 2: Mover `core/common`.**

```bash
C=core/common/src; K=kotlin/com/daviddelgado/agenda
mkdir -p core/domain/src/commonMain/$K/core/domain/{logger,util} core/domain/src/commonTest/$K/core/domain/logger core/presentation/src/commonMain/$K/core/presentation/mvi
git mv $C/commonMain/$K/common/logging/AgendaLogger.kt core/domain/src/commonMain/$K/core/domain/logger/
git mv $C/commonTest/$K/common/logging/AgendaLoggerTest.kt core/domain/src/commonTest/$K/core/domain/logger/
git mv $C/commonMain/$K/common/util/Ids.kt core/domain/src/commonMain/$K/core/domain/util/
git mv $C/commonMain/$K/common/mvi/Mvi.kt $C/commonMain/$K/common/mvi/MviViewModel.kt core/presentation/src/commonMain/$K/core/presentation/mvi/
git rm -q core/common/build.gradle.kts ; git status --short core/common ; rm -rf core/common
```
Expected: `git status` de `core/common` vacío.

`core/domain/build.gradle.kts`:
```kotlin
plugins {
    id("agenda.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.napier)
        }
    }
}
```

- [ ] **Step 3: Mover `core/network` a `core/data/networking`.**

```bash
N=core/network/src; K=kotlin/com/daviddelgado/agenda; DD=core/data/src
for ss in commonMain androidMain iosMain; do
  mkdir -p $DD/$ss/$K/core/data/networking
  git mv $N/$ss/$K/network/*.kt $DD/$ss/$K/core/data/networking/
done
mkdir -p $DD/commonMain/$K/core/data/networking/dto
git mv $N/commonMain/$K/network/dto/AuthDtos.kt $DD/commonMain/$K/core/data/networking/dto/NetworkDtos.kt
git rm -q core/network/build.gradle.kts ; git status --short core/network ; rm -rf core/network
```
Expected: `git status` de `core/network` vacío (en la Tarea 2 salió `api/AuthApi.kt` y en la 4 `api/TaskApi.kt`, `dto/TaskDtos.kt` y `WebSocketService.kt`; si queda algo más, PARA y avisa).
  - Crea `core/data/src/commonMain/kotlin/com/daviddelgado/agenda/core/data/session/TokenProvider.kt` y **mueve** a él `interface TokenProvider { ... }` (con su KDoc) desde `networking/NetworkConfig.kt`, borrándola de allí.

- [ ] **Step 4: Reorganizar `core/data`.**

```bash
K=kotlin/com/daviddelgado/agenda; DD=core/data/src
for ss in commonMain androidMain iosMain; do
  mkdir -p $DD/$ss/$K/core/data/session $DD/$ss/$K/core/data/di
  git mv $DD/$ss/$K/data/secure/*.kt $DD/$ss/$K/core/data/session/
  git mv $DD/$ss/$K/data/di/*.kt $DD/$ss/$K/core/data/di/
done
git mv $DD/commonMain/$K/data/auth/TokenProviderImpl.kt $DD/commonMain/$K/core/data/session/
mkdir -p $DD/commonTest/$K/core/data/{networking,fake}
git mv $DD/commonTest/$K/data/auth/TokenRefreshTest.kt $DD/commonTest/$K/core/data/networking/
git mv $DD/commonTest/$K/data/fake/MockHttpClient.kt $DD/commonTest/$K/data/fake/FakeTokenProvider.kt $DD/commonTest/$K/core/data/fake/
git ls-files core/data | grep -v "/core/data/" | grep -v build.gradle.kts
```
Expected: el último comando no imprime nada (no queda ningún fichero en los paquetes viejos `com/daviddelgado/agenda/data/...`).

  - Renombra los ficheros y los símbolos de DI: `git mv .../core/data/di/DataModule.kt .../core/data/di/CoreDataModule.kt`, y lo mismo con `DataModule.android.kt` → `CoreDataModule.android.kt` y `DataModule.ios.kt` → `CoreDataModule.ios.kt`. Dentro, renombra `val dataModule` → `val coreDataModule` y `platformDataModule` → `platformCoreDataModule` (en el `expect`, los dos `actual` y el `includes(...)`). En el KDoc del `expect`, quita la mención a `DatabaseFactory`, que ya no está aquí.

`core/data/build.gradle.kts`:
```kotlin
plugins {
    id("agenda.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.security.crypto)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    // Expone BuildConfig.DEBUG para elegir servidor de desarrollo vs ProductionConfig
    // (punto 4 de markdown.md) sin tocar codigo entre un build debug y uno release.
    buildFeatures {
        buildConfig = true
    }
}
```

- [ ] **Step 5: `core/designsystem`** cambia de paquete:

```bash
K=kotlin/com/daviddelgado/agenda
mkdir -p core/designsystem/src/commonMain/$K/core
git mv core/designsystem/src/commonMain/$K/designsystem core/designsystem/src/commonMain/$K/core/designsystem
```

- [ ] **Step 6: Paquetes y referencias.** `tools/refactor/fix-packages.sh core` y `tools/refactor/maps/core.map`:

```
com.daviddelgado.agenda.common.logging com.daviddelgado.agenda.core.domain.logger
com.daviddelgado.agenda.common.util com.daviddelgado.agenda.core.domain.util
com.daviddelgado.agenda.common.mvi com.daviddelgado.agenda.core.presentation.mvi
com.daviddelgado.agenda.network.TokenProvider com.daviddelgado.agenda.core.data.session.TokenProvider
com.daviddelgado.agenda.network.dto com.daviddelgado.agenda.core.data.networking.dto
com.daviddelgado.agenda.network com.daviddelgado.agenda.core.data.networking
com.daviddelgado.agenda.data.secure com.daviddelgado.agenda.core.data.session
com.daviddelgado.agenda.data.auth.TokenProviderImpl com.daviddelgado.agenda.core.data.session.TokenProviderImpl
com.daviddelgado.agenda.data.di.dataModule com.daviddelgado.agenda.core.data.di.coreDataModule
com.daviddelgado.agenda.data.di.platformDataModule com.daviddelgado.agenda.core.data.di.platformCoreDataModule
com.daviddelgado.agenda.data.fake com.daviddelgado.agenda.core.data.fake
com.daviddelgado.agenda.designsystem com.daviddelgado.agenda.core.designsystem
```
Ejecuta `tools/refactor/rename-fqn.sh tools/refactor/maps/core.map` y otra vez `tools/refactor/fix-packages.sh core feature`. Esta segunda pasada de `fix-packages` corrige los fakes copiados en `feature/*/data/.../fake`, a los que el mapa les habría puesto `core.data.fake` en su línea `package`.

**Cuidado:** la entrada `com.daviddelgado.agenda.data.fake` reescribe también los `import` de los fakes copiados dentro de `feature/auth/data` y `feature/tasks/data`... pero esos ya importan `...feature.<x>.data.fake.*` desde las Tareas 2 y 4, así que no coinciden con el patrón. Compruébalo con `git grep -n "core.data.fake" feature`: sin resultados.

- [ ] **Step 7: Referencias del mismo paquete que se parten:**
  - `core/data/.../networking/*.kt` y `session/*.kt`: `TokenProviderImpl` y `AgendaHttpClientConfig`/`HttpClientFactory` usaban `TokenProvider` del mismo paquete `network` → añade `import com.daviddelgado.agenda.core.data.session.TokenProvider` donde haga falta, **incluidos `HttpClientFactory.android.kt` y `HttpClientFactory.ios.kt`**. El de iOS no se compila en Windows: revísalo a mano.
  - `CoreDataModule.kt` / `.android` / `.ios`: necesitan `import` de `...networking.NetworkConfig`, `...networking.ProductionConfig`, `...networking.createHttpClient`, `...session.SecureStorage`, `...session.TokenProvider` y `...session.TokenProviderImpl` (según los usen). Revisa el `.ios.kt` a mano.
  - Los `TokenProviderImpl` y `SecureStorage` quedan en el mismo paquete `session` → sin cambios entre ellos.

- [ ] **Step 8: Dependencias de proyecto en los `build.gradle.kts`.**
  - En `feature/*/presentation/build.gradle.kts`: sustituye `implementation(projects.core.common)` por `implementation(projects.core.presentation)` y añade `implementation(projects.core.domain)` si el módulo importa `AgendaLogger` o `randomEntityId` (compruébalo con `git grep -l "core.domain.logger\|core.domain.util" feature/<x>/presentation`).
  - En `feature/*/data/build.gradle.kts`: `projects.core.common` → `projects.core.domain`, y `projects.core.network` → `projects.core.data`.
  - `shared/build.gradle.kts`: quita `projects.core.common` y pon `projects.core.presentation` (si lo usa) y `projects.core.domain` (usa `AgendaLogger`). `projects.core.data` se queda.
  - `androidApp/build.gradle.kts`: quita `projects.core.common` y `projects.core.network`, y deja `projects.core.domain` (usa `AgendaLogger`).
  - `AppModules.kt`: el mapa ya ha convertido `dataModule` en `coreDataModule`; comprueba que en la lista aparece `coreDataModule` como primer elemento.

- [ ] **Step 9: Compilar**, con el mismo comando que en la Tarea 2 (Step 10).

- [ ] **Step 10: Referencias obsoletas:**

```bash
git grep -n -E "com\.daviddelgado\.agenda\.(common|network|data|domain|database|designsystem)\b" -- androidApp shared core feature ':!**/build/**'
```
Expected: sin resultados. Después de esta tarea, todo paquete del cliente empieza por `com.daviddelgado.agenda.core.`, `com.daviddelgado.agenda.feature.`, `com.daviddelgado.agenda.shared` o `com.daviddelgado.agenda.android`.

- [ ] **Step 11: `core` no depende de ninguna feature:**

```bash
git grep -n "feature" -- 'core/*/build.gradle.kts' ; git grep -n "import com.daviddelgado.agenda.feature" -- core
```
Expected: sin resultados.

- [ ] **Step 12: Verificación completa**: `check` + `assembleDebug` y el mismo N.

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "refactor(core): core solo con lo transversal (domain, data, presentation, designsystem)

core:common se reparte entre core:domain (logger, util) y el nuevo core:presentation (MVI);
core:network pasa a core:data.networking y la sesion (TokenProvider, SecureStorage) a
core:data.session. Todos los paquetes de core pasan a com.daviddelgado.agenda.core.*.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: shared (di, navigation), test del grafo de Koin, documentación y cierre

**Files:**
- Move: `shared/.../shared/{AppModules,KoinInitializer}.kt` → `shared/.../shared/di/`; `shared/.../shared/{HomeNavigator,SplashSessionHandler}.kt` (+ sus tests) → `shared/.../shared/navigation/`
- Create: `shared/src/androidUnitTest/kotlin/com/daviddelgado/agenda/shared/di/AppModulesTest.kt`
- Modify: `shared/src/commonMain/.../App.kt`, `shared/src/iosMain/.../MainViewController.kt`, `androidApp/.../AgendaApplication.kt`, `markdown.md`, `ESTADO_PROYECTO.md`, `.github/workflows/ci.yml` (solo si hace falta)
- Delete: `tools/refactor/`

**Interfaces:**
- Consumes: todos los módulos Koin de las Tareas 2–5.
- Produces: `com.daviddelgado.agenda.shared.di.{appModules, initKoin}`, `com.daviddelgado.agenda.shared.navigation.{HomeNavigator, SplashSessionHandler}`.

- [ ] **Step 1: Test del grafo de Koin (falla primero).** Crea `shared/src/androidUnitTest/kotlin/com/daviddelgado/agenda/shared/di/AppModulesTest.kt`:

```kotlin
package com.daviddelgado.agenda.shared.di

import android.content.Context
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Comprueba que cada dependencia que piden los modulos de Koin de la app la declara algun
 * modulo (tras dividir DomainModule/DataModule por feature es facil perder una). Solo analiza
 * los constructores, no crea nada, asi que no necesita un Context real.
 */
@OptIn(KoinExperimentalAPI::class)
class AppModulesTest {
    @Test
    fun `el grafo de Koin de la app esta completo`() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(Context::class, HttpClientConfig::class, HttpClientEngine::class),
        )
    }
}
```
Añade a `shared/build.gradle.kts`, dentro de `sourceSets`:

```kotlin
        androidUnitTest.dependencies {
            implementation(libs.koin.test)
        }
```
y al catálogo, en `[libraries]`:

```toml
koin-test = { group = "io.insert-koin", name = "koin-test", version.ref = "koin" }
```
Ejecuta `./gradlew :shared:testDebugUnitTest --tests "*AppModulesTest*" --no-daemon`.
Expected: **FAIL** con `Unresolved reference: appModules`. Todavía no se ha movido `appModules` a `shared.di`, y eso confirma que el test apunta al sitio nuevo.

- [ ] **Step 2: Mover `shared`.**

```bash
S=shared/src/commonMain/kotlin/com/daviddelgado/agenda/shared; ST=shared/src/commonTest/kotlin/com/daviddelgado/agenda/shared
mkdir -p $S/di $S/navigation $ST/navigation
git mv $S/AppModules.kt $S/KoinInitializer.kt $S/di/
git mv $S/HomeNavigator.kt $S/SplashSessionHandler.kt $S/navigation/
git mv $ST/HomeNavigatorTest.kt $ST/SplashSessionHandlerTest.kt $ST/navigation/
tools/refactor/fix-packages.sh shared
```
`tools/refactor/maps/shared.map`:
```
com.daviddelgado.agenda.shared.initKoin com.daviddelgado.agenda.shared.di.initKoin
com.daviddelgado.agenda.shared.appModules com.daviddelgado.agenda.shared.di.appModules
com.daviddelgado.agenda.shared.HomeNavigator com.daviddelgado.agenda.shared.navigation.HomeNavigator
com.daviddelgado.agenda.shared.SplashSessionHandler com.daviddelgado.agenda.shared.navigation.SplashSessionHandler
```
Ejecuta `tools/refactor/rename-fqn.sh tools/refactor/maps/shared.map`.

Referencias del mismo paquete que se parten, y hay que añadirlas a mano:
  - `App.kt` (sigue en `shared`): `import com.daviddelgado.agenda.shared.navigation.HomeNavigator` y `...navigation.SplashSessionHandler` (los que use).
  - `di/AppModules.kt`: `import com.daviddelgado.agenda.shared.navigation.SplashSessionHandler`.
  - `shared/src/iosMain/.../MainViewController.kt` (**no se compila en Windows, revísalo a mano**): si llama a `initKoin`, añade `import com.daviddelgado.agenda.shared.di.initKoin`.
  - `androidApp/.../AgendaApplication.kt`: el mapa ya reescribe el `import ...shared.initKoin`.

- [ ] **Step 3: El test pasa.** `./gradlew :shared:testDebugUnitTest --no-daemon`.
Expected: PASS, incluido `AppModulesTest`. Si `verify` informa de un tipo sin definición, significa que al dividir se ha perdido un `single`/`factory`: arréglalo **en el módulo Koin que corresponda**, no en el test. Solo van a `extraTypes` los tipos que la app no declara en Koin a propósito: `Context` (lo da `androidContext()`) y los parámetros de constructores de librerías que `verify` inspecciona por reflexión (por ejemplo, los del constructor de `HttpClient`: si se queja de `HttpClientEngine`, `HttpClientConfig` o `Boolean`, se añaden). Cada tipo que añadas a `extraTypes` va en el informe con su motivo. Nunca añadas una clase de la propia app (`com.daviddelgado.agenda.*`).

- [ ] **Step 4: Reglas de capas finales:**

```bash
git grep -n -E "import .*feature\.[a-z]+\.(data|database)\." -- 'feature/*/presentation'
git grep -n -E "import (io\.ktor|androidx\.room|androidx\.compose)" -- 'feature/*/domain' 'core/domain'
git grep -n -E "projects\.feature\." -- 'core/*/build.gradle.kts' 'feature/*/domain/build.gradle.kts'
git grep -n -E "projects\.feature\.(auth|streaks)" -- 'feature/tasks'
git grep -n -E "projects\.feature\.tasks\.(domain|data|presentation)" -- 'feature/auth' 'feature/streaks'
```
Expected: sin resultados. Las únicas dependencias entre features que deben quedar son `projects.feature.tasks.database`, en `feature/auth/data` y `feature/streaks/data`. Compruébalo con `git grep -n "projects.feature" -- 'feature/*/*/build.gradle.kts'`: además de las propias de cada feature, solo aparecen esas dos.

- [ ] **Step 5: CI.** `.github/workflows/ci.yml` ejecuta `./gradlew check`, que ya incluye `build-logic`, así que normalmente no hay que tocar nada. Revisa que no haga referencia a rutas de módulos viejos (`git grep -n -E "core/(common|network|database)|feature/(login|register|passwordreset|calendar|settings)" .github`). Si las hay, actualízalas.

- [ ] **Step 6: Borrar las herramientas.** `git rm -r -q tools/refactor` (y `tools/` si queda vacía).

- [ ] **Step 7: Verificación completa**

```bash
./gradlew clean check :androidApp:assembleDebug --no-daemon
```
Expected: `BUILD SUCCESSFUL`, con N+1 tests (los de la línea base más `AppModulesTest`).

- [ ] **Step 8: Prueba manual en el emulador** (criterio 4 de la spec). Arranca el servidor y el emulador siguiendo `ESTADO_PROYECTO.md`, instala con `./gradlew :androidApp:installDebug` y comprueba, en este orden: splash → login → crear una tarea → completarla → la racha sube → calendario con la tarea → ajustes → logout (la lista queda vacía al volver a entrar con otro usuario) → login otra vez → llega un push de recordatorio. Apunta el resultado de cada paso. Si alguno no se puede probar (por ejemplo, el push), dilo de forma explícita en el informe; no lo des por bueno.

- [ ] **Step 9: Documentación.**
  - `markdown.md`, línea de **Arquitectura**: sustituye los ejemplos de módulos por `core:{domain,data,presentation,designsystem}` + `feature:<x>:{domain,data,[database],presentation}` + `build-logic` (convention plugins), siguiendo la estructura de Squadfy_KMM, y enlaza la spec.
  - `ESTADO_PROYECTO.md`: actualiza el árbol de módulos y añade una entrada de sesión con: qué se ha hecho, las dos excepciones de dependencia entre features y su motivo, el cambio de nombre de la carpeta de esquemas de Room, que iOS no se ha podido compilar (hay que compilarlo en un Mac) y el resultado de la prueba manual.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor(shared): di y navigation en paquetes propios, test del grafo de Koin y docs

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 11: Revisión con contexto nuevo** de toda la rama desde `ce44fac` (regla de PR de AGENTS.md), antes de cualquier push.
