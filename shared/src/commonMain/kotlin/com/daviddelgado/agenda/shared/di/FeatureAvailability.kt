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
 * ausentes (una linea de log por feature). Nunca lanza nada, ni siquiera un `Error`: si la API
 * interna de Koin cambia, [declaredTypes] puede fallar con `NoSuchMethodError` y similares. Si el
 * calculo falla se registra y se asume todo disponible (`emptySet()`). Si el calculo termina pero
 * falla el logger, se devuelve igualmente el conjunto calculado (es la informacion valida y la
 * UI debe degradarse); el fallo del logger se ignora.
 */
@Suppress("TooGenericExceptionCaught") // intencionado: la comprobacion nunca debe tumbar la app
internal fun checkFeatures(
    declaredTypes: () -> Set<KClass<*>>,
    log: (String) -> Unit,
): Set<AppFeature> {
    val safeLog: (String) -> Unit = { message ->
        try {
            log(message)
        } catch (_: Throwable) {
            // un logger roto no puede afectar al arranque
        }
    }
    return try {
        val missing = FeatureContract.missingTypes(declaredTypes())
        missing.forEach { (feature, types) ->
            val names = types.joinToString { it.simpleName ?: it.toString() }
            safeLog("Feature $feature no disponible, faltan definiciones de Koin: $names")
        }
        missing.keys
    } catch (e: Throwable) {
        safeLog("No se pudo comprobar las definiciones de Koin (se asume todo disponible): $e")
        emptySet()
    }
}
