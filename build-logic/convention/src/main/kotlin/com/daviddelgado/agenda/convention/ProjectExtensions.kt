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
