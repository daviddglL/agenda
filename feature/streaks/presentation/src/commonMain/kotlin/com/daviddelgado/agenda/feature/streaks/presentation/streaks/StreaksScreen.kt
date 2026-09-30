package com.daviddelgado.agenda.feature.streaks.presentation.streaks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.daviddelgado.agenda.core.designsystem.theme.AgendaGray
import com.daviddelgado.agenda.core.designsystem.theme.AgendaGreen
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StreaksScreen(viewModel: StreaksViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text(text = "Rachas", style = MaterialTheme.typography.headlineMedium)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StreakStat(label = "Racha actual", value = state.currentStreak)
                StreakStat(label = "Mejor racha", value = state.bestStreak)
            }

            LastDaysGrid(completedDates = state.completedDates.toSet())
        }
    }
}

@Composable
private fun StreakStat(
    label: String,
    value: Int,
) {
    Column {
        Text(text = value.toString(), style = MaterialTheme.typography.headlineMedium, color = AgendaGreen)
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = AgendaGray)
    }
}

@Composable
private fun LastDaysGrid(completedDates: Set<kotlinx.datetime.LocalDate>) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val last28 = (27 downTo 0).map { today.minus(it, DateTimeUnit.DAY) }

    last28.chunked(7).forEach { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { date ->
                androidx.compose.foundation.layout.Box(
                    // Sin weight(1f) cada Box reclama todo el ancho de la Row (aspectRatio
                    // por si solo no reparte el espacio entre hermanos) y los 7 dias de la
                    // semana quedan apilados unos encima de otros en vez de en fila.
                    modifier =
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (date in completedDates) AgendaGreen else Color(0x22000000)),
                )
            }
        }
    }
}
