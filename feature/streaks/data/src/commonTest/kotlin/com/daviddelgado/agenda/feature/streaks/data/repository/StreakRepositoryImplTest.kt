package com.daviddelgado.agenda.feature.streaks.data.repository

import com.daviddelgado.agenda.feature.streaks.data.fake.FakeTaskDao
import com.daviddelgado.agenda.feature.tasks.database.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreakRepositoryImplTest {
    private val hoy = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun diasAntes(dias: Int): LocalDate = hoy.minus(dias, DateTimeUnit.DAY)

    private fun completada(
        id: String,
        date: LocalDate,
    ) = TaskEntity(
        id = id,
        title = "Tarea $id",
        description = "",
        dateEpochDay = date.toEpochDays().toLong(),
        timeMinuteOfDay = null,
        durationMinutes = null,
        category = "OTRO",
        priority = "MEDIA",
        reminderFrequency = "NINGUNO",
        incrementAmount = null,
        incrementEveryValue = null,
        incrementEveryUnit = null,
        isCompleted = true,
    )

    @Test
    fun sinTareasCompletadasNoHayRacha() =
        runTest {
            val resumen = StreakRepositoryImpl(FakeTaskDao()).observeStreak().first()

            assertEquals(0, resumen.currentStreak)
            assertEquals(0, resumen.bestStreak)
            assertTrue(resumen.completedDates.isEmpty())
        }

    @Test
    fun tresDiasSeguidosTerminandoHoySonRachaDeTres() =
        runTest {
            val dao =
                FakeTaskDao(
                    listOf(
                        completada("1", hoy),
                        completada("2", diasAntes(1)),
                        completada("3", diasAntes(2)),
                    ),
                )

            val resumen = StreakRepositoryImpl(dao).observeStreak().first()

            assertEquals(3, resumen.currentStreak)
            assertEquals(3, resumen.bestStreak)
        }

    @Test
    fun unHuecoCortaLaRachaActual() =
        runTest {
            val dao =
                FakeTaskDao(
                    listOf(
                        completada("1", hoy),
                        // falta ayer
                        completada("2", diasAntes(2)),
                        completada("3", diasAntes(3)),
                    ),
                )

            val resumen = StreakRepositoryImpl(dao).observeStreak().first()

            assertEquals(1, resumen.currentStreak)
            // La mejor racha si cuenta los dos dias seguidos de antes del hueco.
            assertEquals(2, resumen.bestStreak)
        }

    @Test
    fun siHoyNoHayNadaCompletadoPeroAyerSiLaRachaSigueViva() =
        runTest {
            // Periodo de gracia: el usuario tiene hasta el final del dia de hoy para marcar
            // algo sin perder la racha que trae de ayer.
            val dao = FakeTaskDao(listOf(completada("1", diasAntes(1)), completada("2", diasAntes(2))))

            val resumen = StreakRepositoryImpl(dao).observeStreak().first()

            assertEquals(2, resumen.currentStreak)
            assertEquals(2, resumen.bestStreak)
        }

    @Test
    fun siNiHoyNiAyerHayNadaCompletadoLaRachaActualEsCero() =
        runTest {
            // Ya paso mas de un dia entero sin marcar nada: la racha esta rota de verdad,
            // no es solo el periodo de gracia de "todavia no ha acabado el dia de hoy".
            val dao = FakeTaskDao(listOf(completada("1", diasAntes(2)), completada("2", diasAntes(3))))

            val resumen = StreakRepositoryImpl(dao).observeStreak().first()

            assertEquals(0, resumen.currentStreak)
            assertEquals(2, resumen.bestStreak)
        }

    @Test
    fun elPeriodoDeGraciaTambienCuentaLosDiasSeguidosAnterioresAAyer() =
        runTest {
            val dao =
                FakeTaskDao(
                    listOf(
                        completada("1", diasAntes(1)),
                        completada("2", diasAntes(2)),
                        completada("3", diasAntes(3)),
                    ),
                )

            val resumen = StreakRepositoryImpl(dao).observeStreak().first()

            assertEquals(3, resumen.currentStreak)
        }

    @Test
    fun variasTareasElMismoDiaCuentanComoUnSoloDiaDeRacha() =
        runTest {
            val dao =
                FakeTaskDao(
                    listOf(
                        completada("1", hoy),
                        completada("2", hoy),
                        completada("3", diasAntes(1)),
                    ),
                )

            val resumen = StreakRepositoryImpl(dao).observeStreak().first()

            assertEquals(2, resumen.currentStreak)
            assertEquals(listOf(hoy, diasAntes(1)), resumen.completedDates)
        }

    @Test
    fun laMejorRachaRecuerdaUnaSerieAntiguaMasLargaQueLaActual() =
        runTest {
            val dao =
                FakeTaskDao(
                    listOf(
                        completada("1", hoy),
                        completada("2", diasAntes(10)),
                        completada("3", diasAntes(11)),
                        completada("4", diasAntes(12)),
                        completada("5", diasAntes(13)),
                    ),
                )

            val resumen = StreakRepositoryImpl(dao).observeStreak().first()

            assertEquals(1, resumen.currentStreak)
            assertEquals(4, resumen.bestStreak)
        }
}
