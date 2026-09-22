package com.cutcoach.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cutcoach.data.db.TrainingRepository
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.CutIcon
import com.cutcoach.ui.components.CutIconView
import com.cutcoach.ui.components.LabelledValue
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SlateEdge
import com.cutcoach.ui.theme.SunriseAmber
import com.cutcoach.ui.theme.tabular
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The week at a glance: what is scheduled, what is done, and where the deload
 * weeks fall.
 *
 * The week runs Monday to Sunday because the schedule in protocol.json does.
 */
@Composable
fun WeekScreen(
    repo: ProtocolRepository,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val training = remember { TrainingRepository.get(context) }
    val sessions by training.recentSessions().collectAsStateWithLifecycle(emptyList())

    val monday = today.with(DayOfWeek.MONDAY)
    val week = repo.programWeek(today)
    val isDeload = week != null && repo.isDeloadWeek(week)

    // Dates that have at least one finished session.
    val completedDates = sessions
        .filter { it.session.endedAt != null }
        .map { it.session.date }
        .toSet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("This week", style = MaterialTheme.typography.displaySmall, color = Mist)
            Text(
                buildString {
                    val total = repo.protocol.meta.programLengthWeeks
                    when {
                        week == null -> append("Before the program starts")
                        total == null -> append("Program week $week")
                        else -> append("Program week $week of $total")
                    }
                    if (isDeload) append("  -  deload")
                },
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = if (isDeload) SunriseAmber else MistDim,
            )
        }

        CutCard {
            (0..6).forEach { offset ->
                val date = monday.plusDays(offset.toLong())
                DayRow(
                    repo = repo,
                    date = date,
                    isToday = date == today,
                    isDone = date.toString() in completedDates,
                    programWeek = repo.programWeek(date),
                )
            }
        }

        CutCard {
            SectionLabel("Deload weeks")
            val deload = repo.protocol.progressionRules.deload
            Text(
                deload.rule,
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
            )
            val start = repo.programStart
            if (start != null) {
                // The program runs until the weight target is met rather than
                // to a date, so this shows the next few rather than all of them.
                val from = if (week != null && repo.isDeloadWeek(week)) week - 1 else week ?: 0
                repo.deloadWeeksFrom(from, DELOADS_SHOWN).forEach { w ->
                    LabelledValue(
                        "Week $w",
                        start.plusWeeks((w - 1).toLong())
                            .format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                        valueColor = if (w == week) SunriseAmber else Mist,
                    )
                }
                if (repo.isOpenEnded) {
                    Text(
                        "Every ${repo.protocol.progressionRules.deload.everyNthWeek}th week, " +
                            "for as long as the program runs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MistDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayRow(
    repo: ProtocolRepository,
    date: LocalDate,
    isToday: Boolean,
    isDone: Boolean,
    programWeek: Int?,
) {
    val schedule = repo.scheduleFor(date.dayOfWeek)
    val session = schedule?.let { repo.session(it.sessionId) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A done day shows a tick; otherwise the icon says what kind of day it
        // is, which the dot never did.
        CutIconView(
            icon = if (isDone) CutIcon.CHECK else sessionIcon(schedule?.sessionId),
            tint = when {
                isDone -> SunriseAmber
                isToday -> CoolSky
                else -> SlateEdge
            },
            size = 18.dp,
        )
        Text(
            date.format(DateTimeFormatter.ofPattern("EEE")),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = if (isToday) Mist else MistDim,
            modifier = Modifier.width(44.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                session?.name ?: "Rest",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isToday) Mist else MistDim,
            )
            val sets = session?.exercises?.sumOf { repo.setsFor(it.sets, programWeek) } ?: 0
            if (sets > 0) {
                Text(
                    "$sets sets",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MistDim,
                )
            }
        }
        Text(
            schedule?.start?.takeIf { it != "any" } ?: "",
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MistDim,
        )
    }
}

/** How many upcoming deloads to list. Enough to plan around, short enough to read. */
private const val DELOADS_SHOWN = 6

/**
 * What kind of day a session is, for the row's icon.
 *
 * Matched on the session id from protocol.json rather than on its name, since
 * ids are what the schedule actually refers to and they do not change when a
 * session is renamed.
 */
internal fun sessionIcon(sessionId: String?): CutIcon = when {
    sessionId == null -> CutIcon.REST
    sessionId.startsWith("strength") -> CutIcon.BARBELL
    sessionId.contains("interval") || sessionId.contains("cardio") -> CutIcon.SHOE
    sessionId.contains("walk") || sessionId.contains("rehab") -> CutIcon.SHOE
    else -> CutIcon.DOT
}
