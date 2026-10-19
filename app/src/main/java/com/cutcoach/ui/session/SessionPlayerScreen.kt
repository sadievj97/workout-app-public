package com.cutcoach.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cutcoach.data.poses.PoseLoad
import com.cutcoach.data.poses.PoseRepository
import com.cutcoach.data.session.SessionStep
import com.cutcoach.data.session.Side
import com.cutcoach.ui.components.CutIcon
import com.cutcoach.ui.components.CutIconView
import com.cutcoach.ui.figure.AnimatedStickFigure
import com.cutcoach.ui.figure.FigureRender
import com.cutcoach.ui.theme.InkLift
import com.cutcoach.ui.theme.Mint
import com.cutcoach.ui.theme.Sky
import com.cutcoach.ui.theme.TextHigh
import com.cutcoach.ui.theme.TextLow
import com.cutcoach.ui.theme.TextMid
import com.cutcoach.ui.theme.Ink
import com.cutcoach.ui.theme.Surface
import com.cutcoach.ui.theme.SurfaceStrong
import com.cutcoach.ui.theme.SurfaceEdge
import com.cutcoach.ui.theme.Ember
import com.cutcoach.ui.theme.tabular

/**
 * The session player.
 *
 * This is where the design spends its boldness: one huge number, one word for
 * the phase, and a colour that says whether you are working or resting. It has
 * to be readable with the phone on the floor and one hand free.
 */
@Composable
fun SessionPlayerScreen(
    vm: SessionPlayerViewModel,
    onExit: () -> Unit,
    onOpenCueCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val chime = remember { Chime(context) }

    // The screen must not sleep between sets.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            chime.release()
        }
    }

    LaunchedEffect(vm.chimeCount) {
        if (vm.chimeCount > 0) {
            if (vm.isFinished) chime.ringFinish() else chime.ring()
        }
    }

    // The phase, as light rather than as a word.
    //
    // The design direction says the phase must be readable from across the
    // floor without reading anything, and a coloured numeral alone was not
    // doing it — at 05:35 the difference between an amber 45 and a blue 45 is
    // easy to miss when you are not looking directly at the phone. A glow
    // filling the top of the screen is visible in peripheral vision.
    val glow by animateColorAsState(
        targetValue = when (vm.current) {
            is SessionStep.Rest, is SessionStep.SwitchSides -> Sky
            SessionStep.Finished -> Mint
            else -> Ember
        },
        animationSpec = tween(durationMillis = 450),
        label = "phaseGlow",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    // Vertical rather than radial: radialGradient takes its
                    // centre and radius in pixels, so centring it needs the
                    // measured width, and a guessed pixel offset puts the glow
                    // in the corner on one screen size and off-screen on
                    // another. A vertical ramp needs no dimensions to be right.
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to glow.copy(alpha = 0.22f),
                            GLOW_FADES_BY to Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SessionHeader(vm = vm, onExit = onExit)

            when (val step = vm.current) {
                is SessionStep.Warmup -> WarmupStep(vm, step)
                is SessionStep.RepSet -> RepSetStep(vm, step, onOpenCueCard)
                is SessionStep.HoldSet -> HoldStep(vm, step, onOpenCueCard)
                is SessionStep.Rest -> TimedPhase(vm, "Rest", Sky, nextLabel(vm))
                is SessionStep.SwitchSides -> TimedPhase(vm, "Switch sides", Sky, nextLabel(vm))
                SessionStep.Finished -> FinishedStep(vm, onExit)
            }
        }
    }
}

/**
 * How far down the screen the phase glow has faded to nothing.
 *
 * A fraction of the height, so it is the same shape on any phone. It stops
 * around the timer rather than running the whole way down: a full-screen wash
 * would lift the black floor the whole design rests on.
 */
private const val GLOW_FADES_BY = 0.55f

/** What comes after the current rest, so you can get into position early. */
private fun nextLabel(vm: SessionPlayerViewModel): String {
    return when (val next = vm.steps.getOrNull(vm.index + 1)) {
        is SessionStep.RepSet ->
            "Next: ${vm.exerciseName(next.exerciseId)}, set ${next.setNumber} of ${next.totalSets}" +
                sideSuffix(next.side)
        is SessionStep.HoldSet ->
            "Next: ${vm.exerciseName(next.exerciseId)}, set ${next.setNumber} of ${next.totalSets}" +
                sideSuffix(next.side)
        is SessionStep.Warmup -> "Next: warm-up block ${next.blockNumber} of ${next.totalBlocks}"
        SessionStep.Finished -> "Last one. Session ends after this."
        else -> ""
    }
}

private fun sideSuffix(side: Side): String = if (side == Side.NONE) "" else ", ${side.label.lowercase()}"

@Composable
private fun SessionHeader(vm: SessionPlayerViewModel, onExit: () -> Unit) {
    var leaving by remember { mutableStateOf(false) }

    if (leaving) {
        LeaveSessionDialog(
            loggedSets = vm.loggedSetCount,
            onMarkDone = {
                leaving = false
                vm.endSessionNow()
                onExit()
            },
            onLeaveUnfinished = {
                leaving = false
                onExit()
            },
            onStay = { leaving = false },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                vm.session?.name ?: "Session",
                style = MaterialTheme.typography.titleMedium,
                color = TextHigh,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatClock(vm.elapsedSeconds),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    // The clock turns amber once the session runs long. It is a
                    // nudge, not an alarm: nothing stops.
                    color = if (vm.isOverrunning) Ember else TextMid,
                )
                TextButton(onClick = { leaving = true }) {
                    CutIconView(CutIcon.STOP, TextMid, size = 14.dp)
                    Box(Modifier.width(6.dp))
                    Text("End", style = MaterialTheme.typography.labelMedium, color = TextMid)
                }
            }
        }

        if (vm.isOverrunning) {
            Text(
                "Running past the planned ${vm.plannedMinutes} minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = Ember,
            )
        }

        LinearProgressIndicator(
            progress = { if (vm.steps.size <= 1) 0f else vm.index.toFloat() / (vm.steps.size - 1) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = Ember,
            trackColor = SurfaceEdge,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/**
 * The timer. One number, one word, one colour.
 */
@Composable
private fun BigTimer(seconds: Int, phase: String, color: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            formatClock(seconds),
            style = MaterialTheme.typography.displayLarge,
            color = color,
        )
        Text(
            phase.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = color,
        )
    }
}

@Composable
private fun TimedPhase(
    vm: SessionPlayerViewModel,
    phase: String,
    color: Color,
    subtitle: String,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BigTimer(vm.secondsLeft, phase, color)

        if (subtitle.isNotBlank()) {
            Box(Modifier.height(16.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = TextMid,
                textAlign = TextAlign.Center,
            )
        }

        Box(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("+30s", CutIcon.CLOCK) { vm.addSeconds(30) }
            SecondaryButton(
                if (vm.running) "Pause" else "Resume",
                if (vm.running) CutIcon.PAUSE else CutIcon.PLAY,
            ) { vm.pauseOrResume() }
            // The case this was built for: a rest skipped by a thumb aiming
            // elsewhere. Back puts you on the set before it, with what you
            // logged still in the field.
            if (vm.canGoBack) {
                SecondaryButton("Back", CutIcon.BACK) { vm.previous() }
            }
        }

        Box(Modifier.height(12.dp))

        PrimaryButton("Skip", CutIcon.SKIP) { vm.skip() }
    }
}

@Composable
private fun WarmupStep(vm: SessionPlayerViewModel, step: SessionStep.Warmup) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Warm-up ${step.blockNumber} of ${step.totalBlocks}",
            style = MaterialTheme.typography.labelMedium,
            color = TextMid,
        )
        Box(Modifier.height(8.dp))

        // No figure during the warm-up. Marching, hip circles, leg swings and
        // arm circles were drawn here for a while, along with the day's first
        // exercise on the ramp-up block. The words carry a warm-up well enough
        // and the drawings were not worth the screen. The exercises keep
        // theirs, which is where not knowing the movement actually costs
        // something.

        BigTimer(vm.secondsLeft, "Warm-up", Ember)
        Box(Modifier.height(20.dp))
        Text(
            step.what,
            style = MaterialTheme.typography.bodyLarge,
            color = TextHigh,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                if (vm.running) "Pause" else "Resume",
                if (vm.running) CutIcon.PAUSE else CutIcon.PLAY,
            ) { vm.pauseOrResume() }
            SecondaryButton("Skip", CutIcon.SKIP) { vm.skip() }
            if (vm.canGoBack) {
                SecondaryButton("Back", CutIcon.BACK) { vm.previous() }
            }
        }
    }
}

@Composable
private fun HoldStep(
    vm: SessionPlayerViewModel,
    step: SessionStep.HoldSet,
    onOpenCueCard: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExerciseHeading(
            name = vm.exerciseName(step.exerciseId),
            exerciseId = step.exerciseId,
            setNumber = step.setNumber,
            totalSets = step.totalSets,
            side = step.side,
            working = true,
            // The ring fills as the hold runs down.
            holdProgress = if (step.seconds > 0) {
                1f - (vm.secondsLeft.toFloat() / step.seconds)
            } else {
                null
            },
            onOpenCueCard = { onOpenCueCard(step.exerciseId) },
        )

        Box(Modifier.height(12.dp))
        BigTimer(vm.secondsLeft, "Hold", Ember)

        if (step.minimumSeconds < step.seconds) {
            Box(Modifier.height(8.dp))
            Text(
                "Anything past ${step.minimumSeconds}s counts.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
            )
        }

        Box(Modifier.height(28.dp))
        PrimaryButton("Done", CutIcon.CHECK) { vm.finishHoldNow() }
        Box(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                if (vm.running) "Pause" else "Resume",
                if (vm.running) CutIcon.PAUSE else CutIcon.PLAY,
            ) { vm.pauseOrResume() }
            SecondaryButton("Skip", CutIcon.SKIP) { vm.skip() }
            if (vm.canGoBack) {
                SecondaryButton("Back", CutIcon.BACK) { vm.previous() }
            }
        }
    }
}

@Composable
private fun RepSetStep(
    vm: SessionPlayerViewModel,
    step: SessionStep.RepSet,
    onOpenCueCard: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExerciseHeading(
            name = vm.exerciseName(step.exerciseId),
            exerciseId = step.exerciseId,
            setNumber = step.setNumber,
            totalSets = step.totalSets,
            side = step.side,
            working = true,
            onOpenCueCard = { onOpenCueCard(step.exerciseId) },
        )

        Box(Modifier.height(10.dp))
        Text(
            "Target ${step.target.describe()}",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMid,
        )

        Box(Modifier.height(16.dp))

        // Big minus, the count, big plus. 72dp targets, well past the 56dp floor,
        // because this is tapped with one hand while breathing hard.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StepperButton("-") { vm.changeReps(-1) }
            Text(
                vm.repsInput.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = Ember,
                modifier = Modifier.width(150.dp),
                textAlign = TextAlign.Center,
            )
            StepperButton("+") { vm.changeReps(1) }
        }

        Text("reps", style = MaterialTheme.typography.bodyMedium, color = TextMid)

        Box(Modifier.height(24.dp))
        PrimaryButton("Log set", CutIcon.CHECK) { vm.logReps() }
        Box(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Skip this set", CutIcon.SKIP) { vm.skip() }
            if (vm.canGoBack) {
                SecondaryButton("Back", CutIcon.BACK) { vm.previous() }
            }
        }
    }
}

@Composable
private fun ExerciseHeading(
    name: String,
    exerciseId: String,
    setNumber: Int,
    totalSets: Int,
    side: Side,
    working: Boolean,
    holdProgress: Float? = null,
    onOpenCueCard: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Small and glanceable: enough to confirm you are in the right shape,
        // not so big it competes with the timer.
        ExerciseFigure(
            exerciseId = exerciseId,
            side = side,
            working = working,
            holdProgress = holdProgress,
        )
        Text(
            name,
            style = MaterialTheme.typography.headlineMedium,
            color = TextHigh,
            textAlign = TextAlign.Center,
        )
        Text(
            buildString {
                append("Set $setNumber of $totalSets")
                if (side != Side.NONE) append("  -  ${side.label}")
            },
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = if (side == Side.NONE) TextMid else Sky,
        )
        TextButton(onClick = onOpenCueCard) {
            Text("How to do it", style = MaterialTheme.typography.labelMedium, color = Sky)
        }
    }
}

/**
 * The figure for the current exercise, or nothing at all if it has no pose yet.
 *
 * The right side of an each-side exercise shows the same figure mirrored, so
 * one set of poses covers both.
 */
@Composable
private fun ExerciseFigure(
    exerciseId: String,
    side: Side,
    working: Boolean,
    holdProgress: Float?,
) {
    val context = LocalContext.current
    val poses = remember { PoseRepository.load(context) as? PoseLoad.Success } ?: return
    val animation = poses.library.animationFor(exerciseId) ?: return

    // The movement in line art, and nothing beside it.
    //
    // Two things were tried here and both came out. Drawn as the solid shaded
    // body it was legible and ugly — the shaded figure is a stack of rounded
    // masses that pile into blobs at a joint — and for something looked at
    // every morning that is the only test that matters. A muscle map stood
    // beside it for a while, a small still body with the worked muscles lit;
    // it answered a question nobody was asking mid-set and took a third of the
    // width to do it.
    //
    // What is left is a diagram of the movement, which is what someone copying
    // one wants. The size stays: going from 150dp to 200dp is what made it
    // usable.
    AnimatedStickFigure(
        animation = animation,
        style = poses.library.figure,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        mirrored = side == Side.RIGHT,
        highlightWorking = working,
        render = FigureRender.LINES,
        library = poses.library,
        holdProgress = holdProgress,
    )
}

@Composable
private fun FinishedStep(vm: SessionPlayerViewModel, onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Done", style = MaterialTheme.typography.displayMedium, color = Ember)
        Text(
            formatClock(vm.elapsedSeconds) + " total",
            style = MaterialTheme.typography.bodyLarge.tabular(),
            color = TextMid,
        )

        Box(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            vm.logged
                .groupBy { it.exerciseId }
                .forEach { (exerciseId, sets) ->
                    Text(
                        vm.exerciseName(exerciseId),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHigh,
                    )
                    Text(
                        sets.joinToString("  ") { s -> s.reps?.toString() ?: "${s.seconds}s" },
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = TextMid,
                    )
                }
            if (vm.logged.isEmpty()) {
                Text(
                    "Nothing logged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                )
            }
        }

        Box(Modifier.height(24.dp))
        PrimaryButton("Back to today") { onExit() }
    }
}

// ---- Buttons --------------------------------------------------------------

@Composable
private fun PrimaryButton(label: String, icon: CutIcon? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(99.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ember,
            contentColor = Ink,
        ),
    ) {
        // Icon and word together, never the icon alone. A glyph is faster to
        // find once you know it and a guess until then, and this is a screen
        // used half awake.
        icon?.let {
            CutIconView(it, Ink, size = 22.dp)
            Box(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SecondaryButton(label: String, icon: CutIcon? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(56.dp),
        shape = RoundedCornerShape(99.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SurfaceStrong,
            contentColor = TextHigh,
        ),
    ) {
        icon?.let {
            CutIconView(it, TextHigh, size = 18.dp)
            Box(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Surface,
            contentColor = TextHigh,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.displaySmall)
    }
}

/** "01:30" for anything under an hour, "1:05:30" beyond it. */
internal fun formatClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * Leaving part way through.
 *
 * Walking out used to leave the session with no end time, which History reads
 * as "Not finished" forever and the progression rules discount. Sometimes that
 * is right — you stopped. Often it is not: you finished the work and put the
 * phone down, or the last two exercises happened without it.
 *
 * So the question is asked once rather than assumed either way. "Mark it done"
 * keeps whatever sets were logged and closes the session; it never fills in the
 * ones that were not.
 */
@Composable
private fun LeaveSessionDialog(
    loggedSets: Int,
    onMarkDone: () -> Unit,
    onLeaveUnfinished: () -> Unit,
    onStay: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onStay,
        containerColor = InkLift,
        titleContentColor = TextHigh,
        textContentColor = TextMid,
        title = { Text("Leave this session?") },
        text = {
            Text(
                if (loggedSets == 0) {
                    "Nothing has been logged yet. Marking it done records that the " +
                        "session happened, with no set numbers."
                } else {
                    "$loggedSets sets logged. Marking it done keeps those and closes " +
                        "the session; the rest are not filled in."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onMarkDone) {
                Text("Mark it done", color = Sky)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onStay) {
                    Text("Keep going", color = TextMid)
                }
                TextButton(onClick = onLeaveUnfinished) {
                    Text("Leave unfinished", color = TextMid)
                }
            }
        },
    )
}


