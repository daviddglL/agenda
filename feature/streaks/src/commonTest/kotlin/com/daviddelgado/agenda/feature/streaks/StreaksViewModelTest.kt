package com.daviddelgado.agenda.feature.streaks

import com.daviddelgado.agenda.domain.model.StreakSummary
import com.daviddelgado.agenda.domain.repository.StreakRepository
import com.daviddelgado.agenda.domain.usecase.ObserveStreakUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeStreakRepository(initial: StreakSummary) : StreakRepository {
    val summary = MutableStateFlow(initial)

    override fun observeStreak(): Flow<StreakSummary> = summary
}

@OptIn(ExperimentalCoroutinesApi::class)
class StreaksViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun prepararDispatcherPrincipal() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun restaurarDispatcherPrincipal() {
        Dispatchers.resetMain()
    }

    @Test
    fun elEstadoArrancaVacio() =
        runTest(dispatcher) {
            val repository = FakeStreakRepository(StreakSummary(0, 0, emptyList()))

            val viewModel = StreaksViewModel(ObserveStreakUseCase(repository))

            assertEquals(0, viewModel.currentState.currentStreak)
            assertEquals(0, viewModel.currentState.bestStreak)
            assertTrue(viewModel.currentState.completedDates.isEmpty())
        }

    @Test
    fun laRachaDelRepositorioLlegaAlEstado() =
        runTest(dispatcher) {
            val dias = listOf(LocalDate(2026, 9, 13), LocalDate(2026, 9, 12))
            val repository = FakeStreakRepository(StreakSummary(2, 7, dias))

            val viewModel = StreaksViewModel(ObserveStreakUseCase(repository))

            assertEquals(2, viewModel.currentState.currentStreak)
            assertEquals(7, viewModel.currentState.bestStreak)
            assertEquals(dias, viewModel.currentState.completedDates)
        }

    @Test
    fun laObservacionEsReactivaYElEstadoSeActualizaSolo() =
        runTest(dispatcher) {
            val repository = FakeStreakRepository(StreakSummary(1, 1, listOf(LocalDate(2026, 9, 13))))
            val viewModel = StreaksViewModel(ObserveStreakUseCase(repository))

            repository.summary.value = StreakSummary(3, 9, emptyList())

            assertEquals(3, viewModel.currentState.currentStreak)
            assertEquals(9, viewModel.currentState.bestStreak)
        }
}
