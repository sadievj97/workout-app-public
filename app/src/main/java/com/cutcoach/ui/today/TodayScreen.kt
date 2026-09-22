package com.cutcoach.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.protocol.ScheduledDay
import com.cutcoach.data.protocol.SessionDefinition
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.LabelledValue
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.Slate
import com.cutcoach.ui.theme.SlateEdge
import com.cutcoach.ui.theme.SunriseAmber
import com.cutcoach.ui.theme.tabular
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The first thing you see. It answers three questions without scrolling:
 * what am I doing today, when, and where am I in the program.
 *
 * Everything below comes from protocol.json. Steps and nutrition are shown as
 * reminders only; the app does not track food in any phase.
 */
@Composable
fun TodayScreen(
    repo: ProtocolRepository,
    today: LocalDate,
    now: LocalTime,
    /** Typed in by the owner in Settings. Displayed, never generated or changed. */
    rehabExercises: List<String>,
    /** What the watch recorded today, and last night. Null until read. */
    health: com.cutcoach.health.DailyHealth? = null,
    lastNight: com.cutcoach.health.DailyHealth? = null,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val week = repo.programWeek(today)
    val isDeload = week != null && repo.isDeloadWeek(week)
    val schedule = repo.scheduleFor(today.dayOfWeek)
    val session = repo.sessionFor(today.dayOfWeek)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(today = today, week = week, totalWeeks = repo.protocol.meta.programLengthWeeks)


        if (isDeload) DeloadBanner(repo.protocol.progressionRules.deload.rule)

        if (schedule != null && session != null) {
            SessionCard(
                repo = repo,
                schedule = schedule,
                session = session,
                programWeek = week,
                onStartSession = onStartSession,
            )
        }

        // Sunday's rehab work, shown exactly as typed. The app never generates,
        // changes, or substitutes it.
        if (schedule?.sessionId == "easy_walk_rehab") {
            CutCard {
                SectionLabel("Rehab")
                if (rehabExercises.isEmpty()) {
                    Text(
                        "No rehab exercises entered yet. Add them in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistDim,
                    )
                } else {
                    rehabExercises.forEach {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = Mist)
                    }
                    Text(
                        "From your rehab program. The app never changes these.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MistDim,
                    )
                }
            }
        }

        // What the watch saw, before the plan for the rest of the day.
        WatchCard(repo = repo, health = health, lastNight = lastNight)

        TimelineCard(repo = repo, now = now)

        TonightCard(repo = repo, today = today)

        TargetsCard(repo = repo)
    }
}

@Composable
private fun Header(today: LocalDate, week: Int?, totalWeeks: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            today.format(DateTimeFormatter.ofPattern("EEEE")),
            style = MaterialTheme.typography.displaySmall,
            color = Mist,
        )
        Text(
            buildString {
                append(today.format(DateTimeFormatter.ofPattern("d MMMM")))
                append("  -  ")
                append(
                    when {
                        week == null -> "before the program starts"
                        // Open-ended: there is no "of N" to count towards,
                        // because the finish line is a weight, not a date.
                        totalWeeks == null -> "week $week"
                        else -> "week $week of $totalWeeks"
                    },
                )
            },
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MistDim,
        )
    }
}

@Composable
private fun DeloadBanner(rule: String) {
    CutCard(background = SlateEdge) {
        Text("Deload week", style = MaterialTheme.typography.titleMedium, color = SunriseAmber)
        Text(rule, style = MaterialTheme.typography.bodyMedium, color = Mist)
        Text(
            "The set counts below are already reduced.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
    }
}

@Composable
private fun SessionCard(
    repo: ProtocolRepository,
    schedule: ScheduledDay,
    session: SessionDefinition,
    programWeek: Int?,
    onStartSession: () -> Unit,
) {
    CutCard {
        SectionLabel("Today's session")

        Text(session.name, style = MaterialTheme.typography.headlineMedium, color = SunriseAmber)

        session.focus?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = Mist)
        }

        Text(
            buildString {
                append(if (schedule.hasFixedStart) schedule.start else "any time")
                append("  -  ")
                append(schedule.durationMin.describe("min"))
                session.warmupMin?.let { append("  -  $it min warm-up first") }
            },
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MistDim,
        )

        Box(Modifier.height(4.dp))

        if (session.isStrength) {
            session.exercises.forEach { item ->
                val name = repo.exercise(item.exerciseId)?.name ?: item.exerciseId
                val sets = repo.setsFor(item.sets, programWeek)
                ExerciseLine(
                    name = name,
                    detail = buildString {
                        append("$sets x ${item.target.describe()}")
                        if (item.eachSide) append(" each side")
                    },
                )
            }
        } else {
            session.options.forEach { optionId ->
                val cardio = repo.library.cardio(optionId)
                ExerciseLine(
                    name = cardio?.name ?: optionId,
                    detail = cardio?.how ?: "",
                )
            }
            session.intervalStructure?.let {
                Box(Modifier.height(4.dp))
                LabelledValue(
                    "Intervals",
                    "${it.rounds} x ${it.workSeconds}s hard, ${it.recoverySeconds}s easy",
                )
                Text(
                    "Start at ${it.roundsStart} rounds in week 1 and build to ${it.rounds}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MistDim,
                )
            }
            session.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MistDim)
            }
        }

        Box(Modifier.height(8.dp))

        Button(
            onClick = onStartSession,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SunriseAmber,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Start session", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ExerciseLine(name: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge, color = Mist)
        if (detail.isNotBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MistDim,
            )
        }
    }
}

/**
 * The day's shape, from waking to lights out. The entry you are currently in
 * is marked, so a glance answers "am I on time".
 */
@Composable
private fun TimelineCard(repo: ProtocolRepository, now: LocalTime) {
    val entries = repo.protocol.dailyTimeline
    val currentIndex = entries.indexOfLast { LocalTime.parse(it.time) <= now }

    CutCard {
        SectionLabel("The day")
        entries.forEachIndexed { index, entry ->
            val isNow = index == currentIndex
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isNow) SunriseAmber else SlateEdge),
                )
                Text(
                    entry.time,
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = if (isNow) SunriseAmber else MistDim,
                )
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isNow) Mist else MistDim,
                )
            }
        }
    }
}

@Composable
private fun TonightCard(repo: ProtocolRepository, today: LocalDate) {
    val night = repo.rampNight(today)
    val lightsOut = repo.lightsOutOn(today)

    CutCard {
        SectionLabel("Tonight")
        if (night == null || lightsOut == null) {
            Text(
                "The bedtime ramp has not started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MistDim,
            )
        } else {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    lightsOut.toString(),
                    style = MaterialTheme.typography.displayMedium.tabular(),
                    color = CoolSky,
                )
                Text(
                    "lights out",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MistDim,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            LabelledValue("Ramp night", "$night")
            LabelledValue("Wake", repo.wakeTime.toString())
        }
    }
}

@Composable
private fun TargetsCard(repo: ProtocolRepository) {
    val targets = repo.protocol.dailyTargets
    val nutrition = targets.nutritionDisplayOnly

    CutCard(background = Slate) {
        SectionLabel("Reminders only")
        LabelledValue("Steps", "${targets.steps}")
        LabelledValue("Calories", "${nutrition.caloriesKcal} kcal")
        LabelledValue("Protein", "${nutrition.proteinG} g", valueColor = SunriseAmber)
        LabelledValue("Fat, at least", "${nutrition.fatMinG} g")
        LabelledValue("Fibre", "${nutrition.fibreG} g")
        LabelledValue("Water", nutrition.waterL.describe("L"))
        Text(
            nutrition.proteinNote,
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
        Text(
            "The app does not track food. These are reminders.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
            textAlign = TextAlign.Start,
        )
    }
}
