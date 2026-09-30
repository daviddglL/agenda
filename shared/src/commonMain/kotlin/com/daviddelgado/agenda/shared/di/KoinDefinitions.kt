package com.daviddelgado.agenda.shared.di

import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.BeanDefinition
import kotlin.reflect.KClass

/**
 * UNICO sitio del proyecto que usa API interna de Koin (`instanceRegistry`). Si una version
 * futura de Koin la cambia, solo hay que tocar este fichero.
 */
@OptIn(KoinInternalApi::class)
private fun Koin.beanDefinitions(): List<BeanDefinition<*>> =
    // Una definicion con varios tipos enlazados aparece con una clave por tipo en el registro:
    // se deduplica por identidad para contar cada definicion una sola vez.
    instanceRegistry.instances.values
        .map { it.beanDefinition }
        .distinctBy { ByReference(it) }

private class ByReference(private val value: Any) {
    override fun equals(other: Any?): Boolean = other is ByReference && other.value === value

    override fun hashCode(): Int = value.hashCode()
}

/** Tipos (primario + enlazados) de todas las definiciones cargadas en este Koin. */
fun Koin.declaredTypes(): Set<KClass<*>> =
    beanDefinitions().flatMapTo(mutableSetOf()) { listOf(it.primaryType) + it.secondaryTypes }

/** Numero de definiciones (no de claves del registro) que declaran [type] como primario o enlazado. */
fun Koin.definitionsDeclaring(type: KClass<*>): Int =
    beanDefinitions().count { type == it.primaryType || type in it.secondaryTypes }

/** Numero de definiciones que declaran TODOS los [types] a la vez (primario o enlazados). */
fun Koin.definitionsDeclaringAll(vararg types: KClass<*>): Int =
    beanDefinitions().count {
            definition ->
        types.all { it == definition.primaryType || it in definition.secondaryTypes }
    }
