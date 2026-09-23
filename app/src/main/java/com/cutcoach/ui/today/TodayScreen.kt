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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cutcoach.data.poses.PoseLoad
import com.cutcoach.data.poses.PoseRepository
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.protocol.ScheduledDay
import com.cutcoach.data.protocol.SessionDefinition
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.CutIcon
import com.cutcoach.ui.components.CutIconView
import com.cutcoach.ui.components.HeroCard
import com.cutcoach.ui.components.LabelledValue
import com.cutcoach.ui.components.Pill
import com.cutcoach.ui.components.PrimaryAction
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.components.StatTile
import com.cutcoach.ui.figure.AnimatedStickFigure
import com.cutcoach.ui.figure.FigureRender
import com.cutcoach.ui.theme.Amber
import com.cutcoach.ui.theme.Ember
import com.cutcoach.ui.theme.Ink
import com.cutcoach.ui.theme.Mint
import com.cutcoach.ui.theme.Sky
import com.cutcoach.ui.theme.Surface
import com.cutcoach.ui.theme.SurfaceEdge
import com.cutcoach.ui.theme.TextHigh
import com.cutcoach.ui.theme.TextLow
import com.cutcoach.ui.theme.TextMid
import com.cutcoach.ui.theme.identityFor
import com.cutcoach.ui.theme.tabular
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The first thing you see. It answers three questions without scrolling: what
 * am I doing, how long will it take, and did last night go well.
 *
 * The version this replaced answered them in a stack of seven identical grey
 * panels, every fact the same size as every other. Here one thing is large and
 * coloured — the session — and everything else is arranged under it. Numbers
 * that change daily get room to be read at arm's length; the rest is quiet.
 *
 * Everything still comes from protocol.json. Steps and nutrition are reminders
 * only; the app does not track food in any phase.
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
    /** Start a session by id, which need not be today's. */
    onStartSession: (String) -> Unit,
    /** Record a session done away from the phone. No sets, and none invented. */
    onMarkDone: (String) -> Unit = {},
    /** Session ids already recorded for today, so the card can say so. */
    doneToday: Set<String> = emptySet(),
    /** Opening one exercise's cue card, from a suggestion or from the list. */
    onOpenExercise: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val week = repo.programWeek(today)
    val isDeload = week != null && repo.isDeloadWeek(week)
    val schedule = repo.scheduleFor(today.dayOfWeek)

    // Which session the screen is showing. Today's by default; the picker can
    // point it at another without the rest of the screen caring which.
    var chosenId by remember(schedule?.sessionId) { mutableStateOf(schedule?.sessionId) }
    var pickerOpen by remember { mutableStateOf(false) }

    val session = chosenId?.let { repo.session(it) }
    val isTodaysOwn = chosenId != null && chosenId == schedule?.sessionId

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(today = today, now = now, week = week, isDeload = isDeload)

        // Above the session card: a pain report has to be read before you start.
        AdviceCard(advice, onOpenExercise = onOpenExercise)

        if (session != null && chosenId != null) {
            SessionHero(
                repo = repo,
                sessionId = chosenId!!,
                session = session,
                schedule = if (isTodaysOwn) schedule else null,
                isTodaysOwn = isTodaysOwn,
                isDeload = isDeload,
                isDone = chosenId in doneToday,
                onStart = { onStartSession(chosenId!!) },
                onSwap = { pickerOpen = true },
                onMarkDone = { onMarkDone(chosenId!!) },
            )
        } else {
            NoSessionCard(onChoose = { pickerOpen = true })
        }

        LastNightRow(repo = repo, health = health, lastNight = lastNight)

        if (session != null && chosenId != null) {
            SessionContents(
                repo = repo,
                sessionId = chosenId!!,
                session = session,
                programWeek = week,
                onOpenExercise = onOpenExercise,
            )
        }

        // Sunday's rehab work, shown exactly as typed. The app never generates,
        // changes, or substitutes it.
        if (chosenId == "easy_walk_rehab" || session?.rehabExercises?.isNotEmpty() == true) {
            RehabCard(rehabExercises)
        }

        WatchCard(repo = repo, health = health, lastNight = lastNight)

        TimelineCard(repo = repo, now = now)

        TonightCard(repo = repo, today = today)

        TargetsCard(repo = repo)
    }

    if (pickerOpen) {
        SessionPickerSheet(
            repo = repo,
            todaysSessionId = schedule?.sessionId,
            onPick = {
                chosenId = it
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

// ---- Header -------------------------------------------------------------

@Composable
private fun Header(today: LocalDate, now: LocalTime, week: Int?, isDeload: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SectionLabel(
                buildString {
                    append(today.format(DateTimeFormatter.ofPattern("EEEE d MMMM")))
                    // Open-ended: there is no "of N" to count towards, because
                    // the finish line is a weight, not a date.
                    if (week != null) append("  ·  Week $week")
                },
            )
            Text(
                greeting(now),
                style = MaterialTheme.typography.displaySmall,
                color = TextHigh,
            )
        }
        if (isDeload) Pill("DELOAD", colour = Amber, fill = SurfaceEdge)
    }
}

/** What to say at the top. The app is opened at 05:35 far more often than not. */
private fun greeting(now: LocalTime): String = when (now.hour) {
    in 0..4 -> "Still up"
    in 5..11 -> "Good morning"
    in 12..17 -> "Afternoon"
    else -> "Evening"
}

// ---- The session --------------------------------------------------------

/**
 * The one loud thing on the screen.
 *
 * The session's own gradient plus its first exercise drawn large and bleeding
 * off the right edge. Both are generated: the gradient is three colours derived
 * from the session's focus, and the figure is the same 3D renderer used in the
 * player, so there is no image file anywhere in this.
 */
@Composable
private fun SessionHero(
    repo: ProtocolRepository,
    sessionId: String,
    session: SessionDefinition,
    schedule: ScheduledDay?,
    isTodaysOwn: Boolean,
    isDeload: Boolean,
    isDone: Boolean,
    onStart: () -> Unit,
    onSwap: () -> Unit,
    onMarkDone: () -> Unit,
) {
    val identity = identityFor(
        id = sessionId,
        name = session.name,
        focus = session.focus,
        hrZone = session.hrZone,
    )
    val context = LocalContext.current
    val poses = remember { PoseRepository.load(context) as? PoseLoad.Success }
    val firstExercise = session.exercises.firstOrNull()?.exerciseId
    val animation = firstExercise?.let { poses?.library?.animationFor(it) }

    HeroCard(
        brush = identity.sweep(),
        art = {
            if (animation != null && poses != null) {
                AnimatedStickFigure(
                    animation = animation,
                    style = poses.library.figure,
                    library = poses.library,
                    render = FigureRender.LINES,
                    highlightWorking = false,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(168.dp)
                        .offset(x = 18.dp, y = 14.dp)
                        .alpha(0.5f),
                )
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            SectionLabel(
                if (isTodaysOwn) "Today" else "Chosen",
                color = Color.White.copy(alpha = 0.75f),
            )
            when {
                isDone -> Pill(
                    "DONE TODAY",
                    colour = Color.White,
                    fill = Color.Black.copy(alpha = 0.28f),
                )

                !isTodaysOwn -> Pill(
                    "NOT TODAY'S",
                    colour = Color.White,
                    fill = Color.Black.copy(alpha = 0.22f),
                )
            }
        }

        Text(
            session.name,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 38.sp,
                lineHeight = 40.sp,
            ),
            color = Color.White,
        )

        Text(
            summary(repo, session, schedule, isDeload),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = Color.White.copy(alpha = 0.85f),
        )

        Box(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryAction(
                text = "Start session",
                onClick = onStart,
                container = Ink,
                content = Color.White,
            )
            // The swap. Deliberately next to Start rather than buried: the
            // schedule is a plan, and doing Monday's session on a Wednesday
            // should cost one tap.
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onSwap)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Change",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }

        // Done away from the phone.
        //
        // Quiet, because on most days the button above it is the right one.
        // Present, because the alternative is a session that happened and a log
        // that says it did not, and the progression rules read the log.
        if (!isDone) {
            TextButton(onClick = onMarkDone) {
                Text(
                    "Did it without the app? Mark as done",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        } else {
            Text(
                "Recorded for today with no set numbers, so the ladders stay " +
                    "where they are. Add them from History whenever.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

/** "Push and legs · 40 min · 5 exercises", built from whatever the session has. */
private fun summary(
    repo: ProtocolRepository,
    session: SessionDefinition,
    schedule: ScheduledDay?,
    isDeload: Boolean,
): String = buildString {
    session.focus?.let { append(it) }

    schedule?.durationMin?.let {
        if (isNotEmpty()) append("  ·  ")
        append(it.describe("min"))
    }

    if (session.exercises.isNotEmpty()) {
        if (isNotEmpty()) append("  ·  ")
        append("${session.exercises.size} exercises")
    }

    session.warmupMin?.let {
        if (isNotEmpty()) append("  ·  ")
        append("$it min warm-up")
    }

    if (isDeload) {
        if (isNotEmpty()) append("  ·  ")
        append("sets reduced")
    }

    if (isEmpty()) append(repo.protocol.sessionDefaults.restSeconds.let { "Rest ${it}s" })
}

@Composable
private fun NoSessionCard(onChoose: () -> Unit) {
    CutCard {
        SectionLabel("No session scheduled")
        Text(
            "Nothing is planned for today. You can still do any of them.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid,
        )
        Box(Modifier.height(4.dp))
        PrimaryAction("Choose a session", onChoose, container = Surface)
    }
}

// ---- Last night, in three numbers ---------------------------------------

/**
 * Sleep, steps and sleeping heart rate, side by side.
 *
 * These are the three numbers that change every day and decide how the session
 * should feel, and they used to be buried in a column of label-and-value rows
 * two cards down. A missing one still says so rather than showing a zero:
 * nothing here invents a number the watch did not record.
 */
@Composable
private fun LastNightRow(
    repo: ProtocolRepository,
    health: com.cutcoach.health.DailyHealth?,
    lastNight: com.cutcoach.health.DailyHealth?,
) {
    val night = lastNight?.night ?: health?.night
    val steps = health?.steps
    val target = repo.protocol.dailyTargets.steps

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatTile(
            label = "Slept",
            value = night?.let { "${it.duration.toHours()}:${"%02d".format(it.duration.toMinutes() % 60)}" }
                ?: "--",
            caption = night?.minutesAfterTarget?.let {
                com.cutcoach.health.describeRampGap(it)
            } ?: if (night == null) "not recorded" else null,
            valueColor = if (night == null) TextLow else TextHigh,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Steps",
            value = steps?.let { if (it >= 1000) "%.1fk".format(it / 1000.0) else "$it" } ?: "--",
            caption = steps?.let { "of ${target / 1000}k" } ?: "not recorded",
            valueColor = when {
                steps == null -> TextLow
                steps >= target -> Mint
                else -> TextHigh
            },
            progress = steps?.let { it.toFloat() / target },
            progressColor = if (steps != null && steps >= target) Mint else Ember,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Sleep HR",
            value = night?.sleepingHeartRate?.toString() ?: "--",
            unit = if (night?.sleepingHeartRate != null) "bpm" else null,
            caption = "goal ${repo.protocol.heartRate.sleepingHrGoalBpm.describe("")}",
            valueColor = if (night?.sleepingHeartRate == null) TextLow else Sky,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---- What is in the session ---------------------------------------------

@Composable
private fun SessionContents(
    repo: ProtocolRepository,
    sessionId: String,
    session: SessionDefinition,
    programWeek: Int?,
    onOpenExercise: (String) -> Unit,
) {
    var expanded by remember(sessionId) { mutableStateOf(false) }
    val identity = identityFor(
        id = sessionId,
        name = session.name,
        focus = session.focus,
        hrZone = session.hrZone,
    )

    CutCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("In this session")
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) "Less" else "All",
                    style = MaterialTheme.typography.labelMedium,
                    color = identity.accent,
                )
            }
        }

        if (session.isStrength) {
            val shown = if (expanded) session.exercises else session.exercises.take(PREVIEW_ROWS)
            shown.forEach { item ->
                val name = repo.exercise(item.exerciseId)?.name ?: item.exerciseId
                val sets = repo.setsFor(item.sets, programWeek)
                ExerciseLine(
                    name = name,
                    detail = buildString {
                        append("$sets × ${item.target.describe()}")
                        if (item.eachSide) append(" each side")
                    },
                    exerciseId = item.exerciseId,
                    muscles = repo.exercise(item.exerciseId)?.muscles.orEmpty(),
                    onOpen = { onOpenExercise(item.exerciseId) },
                )
            }
            val hidden = session.exercises.size - shown.size
            if (hidden > 0) {
                Text(
                    "and $hidden more",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                )
            }
        } else {
            session.options.forEach { optionId ->
                val cardio = repo.library.cardio(optionId)
                ExerciseLine(name = cardio?.name ?: optionId, detail = cardio?.how ?: "")
            }
            session.intervalStructure?.let {
                LabelledValue(
                    "Intervals",
                    "${it.rounds} × ${it.workSeconds}s hard, ${it.recoverySeconds}s easy",
                )
                Text(
                    "Start at ${it.roundsStart} rounds in week 1 and build to ${it.rounds}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                )
            }
            session.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextMid)
            }
        }
    }
}

/** How many exercises to show before "All". */
private const val PREVIEW_ROWS = 3

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
                        .clip(RoundedCornerShape(12.dp))
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
                modifier = Modifier.size(44.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = TextHigh)
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = TextMid,
                )
            }
        }
    }
}

@Composable
private fun RehabCard(rehabExercises: List<String>) {
    CutCard {
        SectionLabel("Rehab")
        if (rehabExercises.isEmpty()) {
            Text(
                "No rehab exercises entered yet. Add them in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
            )
        } else {
            rehabExercises.forEach {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = TextHigh)
            }
            Text(
                "From your rehab program. The app never changes these.",
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
            )
        }
    }
}

// ---- The rest of the day ------------------------------------------------

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("The day")
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) "Less" else "All",
                    style = MaterialTheme.typography.labelMedium,
                    color = Sky,
                )
            }
        }

        entries.forEachIndexed { index, entry ->
            if (index !in window) return@forEachIndexed
            val isNow = index == currentIndex
            val isPast = index < currentIndex

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isNow) {
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceEdge)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        } else {
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CutIconView(
                    icon = timelineIcon(entry.label),
                    tint = when {
                        isNow -> Ember
                        isPast -> TextLow
                        else -> TextMid
                    },
                    size = 17.dp,
                )
                Text(
                    entry.time,
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = if (isNow) Ember else TextLow,
                    fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isNow) TextHigh else TextMid,
                )
            }
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
                color = TextMid,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    lightsOut.toString(),
                    style = MaterialTheme.typography.displayMedium.tabular(),
                    color = Sky,
                )
                Column(
                    modifier = Modifier.padding(bottom = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        "lights out",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMid,
                    )
                    Text(
                        "night $night  ·  up at ${repo.wakeTime}",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = TextLow,
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetsCard(repo: ProtocolRepository) {
    val targets = repo.protocol.dailyTargets
    val nutrition = targets.nutritionDisplayOnly

    CutCard {
        SectionLabel("Reminders only")
        LabelledValue("Steps", "${targets.steps}")
        LabelledValue("Calories", "${nutrition.caloriesKcal} kcal")
        LabelledValue("Protein", "${nutrition.proteinG} g", valueColor = Ember)
        LabelledValue("Fat, at least", "${nutrition.fatMinG} g")
        LabelledValue("Fibre", "${nutrition.fibreG} g")
        LabelledValue("Water", nutrition.waterL.describe("L"))
        Text(
            nutrition.proteinNote,
            style = MaterialTheme.typography.bodySmall,
            color = TextLow,
        )
        Text(
            "The app does not track food. These are reminders.",
            style = MaterialTheme.typography.bodySmall,
            color = TextLow,
        )
    }
}
