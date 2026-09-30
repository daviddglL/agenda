package com.daviddelgado.agenda.feature.streaks.data.repository

import com.daviddelgado.agenda.database.TaskDao
import com.daviddelgado.agenda.feature.streaks.domain.model.StreakSummary
import com.daviddelgado.agenda.feature.streaks.domain.repository.StreakRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

class StreakRepositoryImpl(private val dao: TaskDao) : StreakRepository {
    override fun observeStreak(): Flow<StreakSummary> =
        dao.observeCompletedDateEpochDays().map { epochDays ->
            val dates = epochDays.map { LocalDate.fromEpochDays(it.toInt()) }.sortedDescending()
            StreakSummary(
                currentStreak = currentStreak(dates),
                bestStreak = bestStreak(dates),
                completedDates = dates,
            )
        }

    /**
     * La racha actual cuenta dias seguidos con al menos una tarea completada, anclada en
     * hoy... o en ayer si hoy todavia no se ha completado nada: el usuario tiene hasta el
     * final del dia para no perderla, no se rompe a las 00:00 en punto solo porque "hoy"
     * todavia no tiene ninguna tarea marcada.
     */
    private fun currentStreak(datesDesc: List<LocalDate>): Int {
        if (datesDesc.isEmpty()) return 0
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val mostRecentCompleted = datesDesc.first()

        // Si ni hoy ni ayer hay nada completado, la racha esta rota: ya paso mas de un dia
        // entero sin marcar nada, no importa cuantos dias seguidos hubiera antes.
        if (mostRecentCompleted != today && mostRecentCompleted != yesterday) return 0

        var expected = mostRecentCompleted
        var streak = 0
        for (date in datesDesc) {
            if (date != expected) break
            streak++
            expected = expected.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }

    private fun bestStreak(datesDesc: List<LocalDate>): Int {
        if (datesDesc.isEmpty()) return 0
        val datesAsc = datesDesc.sorted()
        var best = 1
        var running = 1
        for (i in 1 until datesAsc.size) {
            val expectedNext = datesAsc[i - 1].plus(1, DateTimeUnit.DAY)
            running = if (datesAsc[i] == expectedNext) running + 1 else 1
            if (running > best) best = running
        }
        return best
    }
}
