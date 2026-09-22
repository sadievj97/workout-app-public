package com.cutcoach.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.protocol.ScheduledDay
import com.cutcoach.data.protocol.SessionDefinition
import com.cutcoach.data.poses.PoseLoad
import com.cutcoach.data.poses.PoseRepository
import com.cutcoach.ui.figure.AnimatedStickFigure
import com.cutcoach.ui.figure.FigureRender
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.CutIcon
import com.cutcoach.ui.components.CutIconView
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
    /** What the rules engine makes of all of it. Suggestions only. */
    advice: List<com.cutcoach.rules.Recommendation> = emptyList(),
    onStartSession: () -> Unit,
    /** Opening one exercise's cue card, from a suggestion or from the list. */
    onOpenExercise: (String) -> Unit = {},
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

        // Above the session card: a pain report has to be read before you start.
        AdviceCard(advice, onOpenExercise = onOpenExercise)

        if (schedule != null && session != null) {
            SessionCard(
                repo = repo,
                schedule = schedule,
                session = session,
                programWeek = week,
                onStartSession = onStartSession,
                onOpenExercise = onOpenExercise,
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
    onOpenExercise: (String) -> Unit,
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
                    // The figure is already drawn for every exercise; showing a
                    // small one here means you can see what a name means
                    // without opening anything.
                    exerciseId = item.exerciseId,
                    muscles = repo.exercise(item.exerciseId)?.muscles.orEmpty(),
                    onOpen = { onOpenExercise(item.exerciseId) },
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
private fun ExerciseLine(
    name: String,
    detail: String,
    exerciseId: String? = null,
    muscles: List<String> = emptyList(),
    onOpen: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val poses = remember { PoseRepository.load(context) as? PoseLoad.Success }
    val animation = exerciseId?.let { poses?.library?.animationFor(it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onOpen != null) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpen)
                        .heightIn(min = 56.dp)
                        .padding(vertical = 2.dp)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Small and still: a moving figure on every row would turn the card
        // into a fairground. It animates on the cue card, where you are
        // looking at one.
        if (animation != null && poses != null) {
            AnimatedStickFigure(
                animation = animation,
                style = poses.library.figure,
                muscles = muscles,
                library = poses.library,
                render = FigureRender.LINES,
                paused = true,
                modifier = Modifier.size(46.dp),
            )
        }
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
}

/**
 * The day's shape, from waking to lights out. The entry you are currently in
 * is marked, so a glance answers "am I on time".
 */
@Composable
private fun TimelineCard(repo: ProtocolRepository, now: LocalTime) {
    val entries = repo.protocol.dailyTimeline
    val currentIndex = entries.indexOfLast { LocalTime.parse(it.time) <= now }

    // Ten rows is most of a screen, and nine of them are behind or ahead of
    // you. Collapsed, it shows where you are and what is next.
    var expanded by remember { mutableStateOf(false) }
    val window = if (expanded) {
        entries.indices
    } else {
        val from = currentIndex.coerceAtLeast(0)
        from..(from + TIMELINE_AHEAD).coerceAtMost(entries.lastIndex)
    }

    CutCard {
        SectionLabel("The day")
        entries.forEachIndexed { index, entry ->
            if (index !in window) return@forEachIndexed
            val isNow = index == currentIndex
            val isPast = index < currentIndex

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CutIconView(
                    icon = timelineIcon(entry.label),
                    tint = when {
                        isNow -> SunriseAmber
                        isPast -> SlateEdge
                        else -> MistDim
                    },
                    size = 18.dp,
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

        TextButton(onClick = { expanded = !expanded }) {
            Text(
                if (expanded) "Show less" else "The whole day",
                style = MaterialTheme.typography.labelMedium,
                color = CoolSky,
            )
        }
    }
}

/** How many entries after the current one to show while collapsed. */
private const val TIMELINE_AHEAD = 2

/**
 * An icon for a timeline entry, chosen from what the entry is about.
 *
 * Matched on the label's own words rather than on a field in protocol.json,
 * because a wrong icon costs a moment's confusion while a wrong schema costs an
 * edit to the source of truth. Anything unrecognised gets a plain dot, which is
 * what every row had before.
 */
private fun timelineIcon(label: String): CutIcon {
    val text = label.lowercase()
    return when {
        text.contains("lights out") -> CutIcon.MOON
        text.contains("bedroom") || text.contains("screens") || text.contains("lamps") ->
            CutIcon.LAMP
        text.contains("up,") || text.contains("wake") -> CutIcon.SUNRISE
        text.contains("train") -> CutIcon.BARBELL
        text.contains("tennis") || text.contains("walk") -> CutIcon.SHOE
        text.contains("lunch") || text.contains("dinner") || text.contains("food") ||
            text.contains("whey") || text.contains("caffeine") -> CutIcon.PLATE
        else -> CutIcon.DOT
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
