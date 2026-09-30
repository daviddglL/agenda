package com.daviddelgado.agenda.shared.di

import com.daviddelgado.agenda.feature.tasks.data.remote.TaskApi
import com.daviddelgado.agenda.feature.tasks.database.dao.TaskDao
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureAvailabilityTest {
    private val all: Set<KClass<*>> = FeatureContract.requiredTypes.values.flatten().toSet()

    @AfterTest
    fun reset() = FeatureAvailability.update(emptySet())

    @Test
    fun `con todos los tipos no hay features no disponibles ni logs`() {
        val logs = mutableListOf<String>()
        assertEquals(emptySet(), checkFeatures({ all }, { logs += it }))
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `sin TaskApi solo TASKS no esta disponible y se registra el tipo`() {
        val logs = mutableListOf<String>()
        val result = checkFeatures({ all - TaskApi::class }, { logs += it })
        assertEquals(setOf(AppFeature.TASKS), result)
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("TaskApi"))
    }

    @Test
    fun `sin un tipo compartido caen las tres features`() {
        val result = checkFeatures({ all - TaskDao::class }, { })
        assertEquals(setOf(AppFeature.AUTH, AppFeature.TASKS, AppFeature.STREAKS), result)
    }

    @Test
    fun `si declaredTypes lanza se registra y se asume disponible`() {
        val logs = mutableListOf<String>()
        val result = checkFeatures({ throw IllegalStateException("boom") }, { logs += it })
        assertEquals(emptySet(), result)
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("boom"))
    }

    @Test
    fun `update marca solo las features indicadas como no disponibles`() {
        FeatureAvailability.update(setOf(AppFeature.STREAKS))
        assertFalse(FeatureAvailability.isAvailable(AppFeature.STREAKS))
        assertTrue(FeatureAvailability.isAvailable(AppFeature.TASKS))
    }
}
