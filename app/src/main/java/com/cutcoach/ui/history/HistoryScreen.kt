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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cutcoach.data.db.SessionOutcome
import com.cutcoach.data.db.SessionWithCount
import com.cutcoach.data.db.SetLogEntity
import com.cutcoach.data.db.TrainingRepository
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.CutIcon
import com.cutcoach.ui.components.CutIconView
import com.cutcoach.ui.week.sessionIcon
import com.cutcoach.ui.components.Pill
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.today.SessionPickerSheet
import com.cutcoach.ui.theme.Sky
import com.cutcoach.ui.theme.TextHigh
import com.cutcoach.ui.theme.TextLow
import com.cutcoach.ui.theme.TextMid
import com.cutcoach.ui.theme.SurfaceEdge
import com.cutcoach.ui.theme.Ember
import com.cutcoach.ui.theme.tabular
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
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
    val scope = rememberCoroutineScope()

    // Backfilling a past day: pick the day first, then the session. Day first
    // because that is the part you are sure of.
    var backfillDate by remember { mutableStateOf<LocalDate?>(null) }

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
            Text("History", style = MaterialTheme.typography.displaySmall, color = TextHigh)
        }

        item {
            BackfillCard(onPickDay = { backfillDate = it })
        }

        if (sessions.isEmpty()) {
            item {
                CutCard {
                    Text(
                        "No sessions logged yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextHigh,
                    )
                    Text(
                        "Finish a session and it will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid,
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
                            color = Sky,
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

    backfillDate?.let { date ->
        SessionPickerSheet(
            repo = repo,
            todaysSessionId = repo.scheduleFor(date.dayOfWeek)?.sessionId,
            onPick = { sessionId ->
                scope.launch {
                    val definition = repo.session(sessionId)
                    val week = repo.programWeek(date)
                    if (definition != null && !training.sessionAlreadyOn(date, sessionId)) {
                        training.markSessionDone(
                            date = date,
                            sessionId = sessionId,
                            sessionName = definition.name,
                            programWeek = week,
                            isDeload = week != null && repo.isDeloadWeek(week),
                            plannedMinutes = repo.scheduleFor(date.dayOfWeek)
                                ?.durationMin?.min?.toInt() ?: 0,
                            // Noon rather than now, so a session backfilled at
                            // midnight does not sort onto the following day.
                            atTime = LocalTime.NOON,
                        )
                    }
                }
                backfillDate = null
            },
            onDismiss = { backfillDate = null },
        )
    }
}

/**
 * Record a session that happened away from the phone, on any recent day.
 *
 * A week of chips rather than a calendar: the window that matters is the last
 * few days, and a date picker for "yesterday" is three taps where one will do.
 */
@Composable
private fun BackfillCard(onPickDay: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }

    CutCard {
        SectionLabel("Did one away from the phone?")
        Text(
            "Pick the day, then the session. It is recorded without set numbers, " +
                "so nothing is invented and no ladder moves.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMid,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (0..BACKFILL_DAYS).forEach { back ->
                val date = today.minusDays(back.toLong())
                Pill(
                    text = when (back) {
                        0 -> "Today"
                        1 -> "Yest"
                        else -> date.format(DateTimeFormatter.ofPattern("EEE"))
                    },
                    colour = if (back == 0) TextHigh else TextMid,
                    fill = SurfaceEdge,
                    modifier = Modifier.clickable { onPickDay(date) },
                )
            }
        }
    }
}

/** How far back the chips reach. A week covers "I forgot to log it". */
private const val BACKFILL_DAYS = 6

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
                tint = if (session.endedAt != null) Ember else TextMid,
                size = 20.dp,
            )
            Text(
                session.sessionName,
                style = MaterialTheme.typography.titleMedium,
                color = TextHigh,
                modifier = Modifier.weight(1f),
            )
            Text(
                date.format(DateTimeFormatter.ofPattern("d MMM")),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = TextMid,
            )
        }

        Text(
            buildString {
                session.programWeek?.let { append("Week $it") }
                if (session.isDeload) append("  ·  deload")
                if (row.outcome == SessionOutcome.DONE_NO_NUMBERS) {
                    append("  ·  no set numbers")
                } else {
                    append("  ·  ${row.setCount} sets")
                }
                durationMinutes(session.startedAt, session.endedAt)
                    ?.takeIf { it > 0 }
                    ?.let { append("  ·  $it min") }
            },
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = TextMid,
        )

        // The three outcomes read differently on purpose. "Done, nothing
        // written down" is a complete record of an incomplete kind, and must
        // not be confused with a session that was abandoned part way — the
        // first is normal, the second is not.
        when (row.outcome) {
            SessionOutcome.UNFINISHED -> Text(
                "Not finished",
                style = MaterialTheme.typography.bodySmall,
                color = Ember,
            )

            SessionOutcome.DONE_NO_NUMBERS -> Text(
                "Done away from the phone. Nothing was recorded, so the ladders " +
                    "did not move.",
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
            )

            SessionOutcome.LOGGED -> Unit
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
            Text("Back", style = MaterialTheme.typography.labelMedium, color = Sky)
        }
        Text(name, style = MaterialTheme.typography.displaySmall, color = TextHigh)

        if (grouped.isEmpty()) {
            Text(
                "Nothing logged for this exercise yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(grouped.entries.toList(), key = { it.key }) { (_, sets) ->
                CutCard {
                    Text(
                        summarise(sets),
                        style = MaterialTheme.typography.headlineSmall.tabular(),
                        color = Ember,
                    )
                    val sides = sets.mapNotNull { it.side.takeIf { s -> s != "NONE" } }.distinct()
                    if (sides.isNotEmpty()) {
                        Text(
                            "each side",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMid,
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
