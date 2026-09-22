package com.cutcoach.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cutcoach.data.db.SessionWithCount
import com.cutcoach.data.db.SetLogEntity
import com.cutcoach.data.db.TrainingRepository
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.CutIcon
import com.cutcoach.ui.components.CutIconView
import com.cutcoach.ui.week.sessionIcon
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SunriseAmber
import com.cutcoach.ui.theme.tabular
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * What you have actually done. Sessions newest first, and a way into the
 * per-exercise history that progression will argue from in Phase 3.
 */
@Composable
fun HistoryScreen(
    repo: ProtocolRepository,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val training = remember { TrainingRepository.get(context) }
    val sessions by training.recentSessions().collectAsStateWithLifecycle(emptyList())
    val loggedIds by training.loggedExerciseIds().collectAsStateWithLifecycle(emptyList())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 16.dp,
            bottom = 32.dp,
        ),
    ) {
        item {
            Text("History", style = MaterialTheme.typography.displaySmall, color = Mist)
        }

        if (sessions.isEmpty()) {
            item {
                CutCard {
                    Text(
                        "No sessions logged yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Mist,
                    )
                    Text(
                        "Finish a session and it will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MistDim,
                    )
                }
            }
        }

        if (loggedIds.isNotEmpty()) {
            item {
                CutCard {
                    SectionLabel("By exercise")
                    loggedIds.sorted().forEach { id ->
                        Text(
                            repo.exercise(id)?.name ?: id,
                            style = MaterialTheme.typography.bodyLarge,
                            color = CoolSky,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenExercise(id) }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }

        items(sessions, key = { it.session.id }) { row ->
            SessionRow(row)
        }
    }
}

@Composable
private fun SessionRow(row: SessionWithCount) {
    val session = row.session
    val date = LocalDate.parse(session.date)

    CutCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Finished or not is the first thing worth knowing about a past
            // session, so it leads the row rather than trailing it in words.
            CutIconView(
                icon = if (session.endedAt != null) CutIcon.CHECK else sessionIcon(session.sessionId),
                tint = if (session.endedAt != null) SunriseAmber else MistDim,
                size = 20.dp,
            )
            Text(
                session.sessionName,
                style = MaterialTheme.typography.titleMedium,
                color = Mist,
                modifier = Modifier.weight(1f),
            )
            Text(
                date.format(DateTimeFormatter.ofPattern("d MMM")),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MistDim,
            )
        }

        Text(
            buildString {
                session.programWeek?.let { append("Week $it") }
                if (session.isDeload) append("  -  deload")
                append("  -  ${row.setCount} sets")
                durationMinutes(session.startedAt, session.endedAt)?.let { append("  -  $it min") }
            },
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MistDim,
        )

        if (session.endedAt == null) {
            Text(
                "Not finished",
                style = MaterialTheme.typography.bodySmall,
                color = SunriseAmber,
            )
        }
    }
}

private fun durationMinutes(startedAt: Long, endedAt: Long?): Long? {
    if (endedAt == null) return null
    return TimeUnit.MILLISECONDS.toMinutes(endedAt - startedAt)
}

/**
 * Every set of one exercise, grouped by the session it was done in, newest
 * first. This is the view that answers "what do I have to beat today".
 */
@Composable
fun ExerciseHistoryScreen(
    repo: ProtocolRepository,
    exerciseId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val training = remember { TrainingRepository.get(context) }
    val name = repo.exercise(exerciseId)?.name ?: exerciseId

    val grouped by remember(exerciseId) {
        training.historyFor(exerciseId).map { sets -> sets.groupBy { it.sessionLogId } }
    }.collectAsStateWithLifecycle(emptyMap())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("Back", style = MaterialTheme.typography.labelMedium, color = CoolSky)
        }
        Text(name, style = MaterialTheme.typography.displaySmall, color = Mist)

        if (grouped.isEmpty()) {
            Text(
                "Nothing logged for this exercise yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MistDim,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(grouped.entries.toList(), key = { it.key }) { (_, sets) ->
                CutCard {
                    Text(
                        summarise(sets),
                        style = MaterialTheme.typography.headlineSmall.tabular(),
                        color = SunriseAmber,
                    )
                    val sides = sets.mapNotNull { it.side.takeIf { s -> s != "NONE" } }.distinct()
                    if (sides.isNotEmpty()) {
                        Text(
                            "each side",
                            style = MaterialTheme.typography.bodySmall,
                            color = MistDim,
                        )
                    }
                }
            }
        }
        Box(Modifier.height(20.dp))
    }
}

/** "15  15  14  12" for reps, or "45s  40s  38s" for holds. */
private fun summarise(sets: List<SetLogEntity>): String =
    sets.joinToString("  ") { set ->
        set.reps?.toString() ?: set.seconds?.let { "${it}s" } ?: "-"
    }
