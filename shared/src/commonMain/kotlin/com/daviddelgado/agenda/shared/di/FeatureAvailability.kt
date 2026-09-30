package com.daviddelgado.agenda.shared.di

import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

/**
 * Features cuyo modulo Koin esta incompleto. Se calcula una vez al arrancar (ver [initKoin]) y
 * la UI lo consulta para mostrar "no disponible" en vez de pedir una dependencia inexistente
 * (lo que cerraria la app). Es un conjunto inmutable que se sustituye entero, asi que leerlo
 * desde Compose es seguro.
 */
object FeatureAvailability {
    @Volatile
    var unavailable: Set<AppFeature> = emptySet()
        private set

    fun isAvailable(feature: AppFeature): Boolean = feature !in unavailable

    internal fun update(unavailable: Set<AppFeature>) {
        this.unavailable = unavailable.toSet()
    }
}

/**
 * Compara los tipos declarados en Koin con [FeatureContract] y devuelve las features con tipos
 * ausentes (una linea de log por feature). Nunca lanza: si [declaredTypes] falla se registra el
 * error y se asume todo disponible, porque la comprobacion no debe tumbar la app.
 */
@Suppress("TooGenericExceptionCaught") // intencionado: la comprobacion nunca debe tumbar la app
internal fun checkFeatures(
    declaredTypes: () -> Set<KClass<*>>,
    log: (String) -> Unit,
): Set<AppFeature> =
    try {
        val missing = FeatureContract.missingTypes(declaredTypes())
        missing.forEach { (feature, types) ->
            val names = types.joinToString { it.simpleName ?: it.toString() }
            log("Feature $feature no disponible, faltan definiciones de Koin: $names")
        }
        missing.keys
    } catch (e: Exception) {
        log("No se pudo comprobar las definiciones de Koin (se asume todo disponible): $e")
        emptySet()
    }
