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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cutcoach.data.session.SessionStep
import com.cutcoach.data.session.Side
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.Slate
import com.cutcoach.ui.theme.SlateEdge
import com.cutcoach.ui.theme.SunriseAmber
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

    Column(
        modifier = modifier
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
            is SessionStep.Rest -> TimedPhase(vm, "Rest", CoolSky, nextLabel(vm))
            is SessionStep.SwitchSides -> TimedPhase(vm, "Switch sides", CoolSky, nextLabel(vm))
            SessionStep.Finished -> FinishedStep(vm, onExit)
        }
    }
}

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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                vm.session?.name ?: "Session",
                style = MaterialTheme.typography.titleMedium,
                color = Mist,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatClock(vm.elapsedSeconds),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    // The clock turns amber once the session runs long. It is a
                    // nudge, not an alarm: nothing stops.
                    color = if (vm.isOverrunning) SunriseAmber else MistDim,
                )
                TextButton(onClick = onExit) {
                    Text("End", style = MaterialTheme.typography.labelMedium, color = MistDim)
                }
            }
        }

        if (vm.isOverrunning) {
            Text(
                "Running past the planned ${vm.plannedMinutes} minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = SunriseAmber,
            )
        }

        LinearProgressIndicator(
            progress = { if (vm.steps.size <= 1) 0f else vm.index.toFloat() / (vm.steps.size - 1) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = SunriseAmber,
            trackColor = SlateEdge,
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
                color = MistDim,
                textAlign = TextAlign.Center,
            )
        }

        Box(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("+30s") { vm.addSeconds(30) }
            SecondaryButton(if (vm.running) "Pause" else "Resume") { vm.pauseOrResume() }
        }

        Box(Modifier.height(12.dp))

        PrimaryButton("Skip") { vm.skip() }
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
            color = MistDim,
        )
        Box(Modifier.height(8.dp))
        BigTimer(vm.secondsLeft, "Warm-up", SunriseAmber)
        Box(Modifier.height(20.dp))
        Text(
            step.what,
            style = MaterialTheme.typography.bodyLarge,
            color = Mist,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(if (vm.running) "Pause" else "Resume") { vm.pauseOrResume() }
            SecondaryButton("Skip") { vm.skip() }
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
            setNumber = step.setNumber,
            totalSets = step.totalSets,
            side = step.side,
            onOpenCueCard = { onOpenCueCard(step.exerciseId) },
        )

        Box(Modifier.height(12.dp))
        BigTimer(vm.secondsLeft, "Hold", SunriseAmber)

        if (step.minimumSeconds < step.seconds) {
            Box(Modifier.height(8.dp))
            Text(
                "Anything past ${step.minimumSeconds}s counts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MistDim,
            )
        }

        Box(Modifier.height(28.dp))
        PrimaryButton("Done") { vm.finishHoldNow() }
        Box(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(if (vm.running) "Pause" else "Resume") { vm.pauseOrResume() }
            SecondaryButton("Skip") { vm.skip() }
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
            setNumber = step.setNumber,
            totalSets = step.totalSets,
            side = step.side,
            onOpenCueCard = { onOpenCueCard(step.exerciseId) },
        )

        Box(Modifier.height(10.dp))
        Text(
            "Target ${step.target.describe()}",
            style = MaterialTheme.typography.bodyLarge,
            color = MistDim,
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
                color = SunriseAmber,
                modifier = Modifier.width(150.dp),
                textAlign = TextAlign.Center,
            )
            StepperButton("+") { vm.changeReps(1) }
        }

        Text("reps", style = MaterialTheme.typography.bodyMedium, color = MistDim)

        Box(Modifier.height(24.dp))
        PrimaryButton("Log set") { vm.logReps() }
        Box(Modifier.height(12.dp))
        SecondaryButton("Skip this set") { vm.skip() }
    }
}

@Composable
private fun ExerciseHeading(
    name: String,
    setNumber: Int,
    totalSets: Int,
    side: Side,
    onOpenCueCard: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            name,
            style = MaterialTheme.typography.headlineMedium,
            color = Mist,
            textAlign = TextAlign.Center,
        )
        Text(
            buildString {
                append("Set $setNumber of $totalSets")
                if (side != Side.NONE) append("  -  ${side.label}")
            },
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = if (side == Side.NONE) MistDim else CoolSky,
        )
        TextButton(onClick = onOpenCueCard) {
            Text("How to do it", style = MaterialTheme.typography.labelMedium, color = CoolSky)
        }
    }
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
        Text("Done", style = MaterialTheme.typography.displayMedium, color = SunriseAmber)
        Text(
            formatClock(vm.elapsedSeconds) + " total",
            style = MaterialTheme.typography.bodyLarge.tabular(),
            color = MistDim,
        )

        Box(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Slate)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            vm.logged
                .groupBy { it.exerciseId }
                .forEach { (exerciseId, sets) ->
                    Text(
                        vm.exerciseName(exerciseId),
                        style = MaterialTheme.typography.titleMedium,
                        color = Mist,
                    )
                    Text(
                        sets.joinToString("  ") { s -> s.reps?.toString() ?: "${s.seconds}s" },
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = MistDim,
                    )
                }
            if (vm.logged.isEmpty()) {
                Text(
                    "Nothing logged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MistDim,
                )
            }
        }

        Box(Modifier.height(24.dp))
        PrimaryButton("Back to today") { onExit() }
    }
}

// ---- Buttons --------------------------------------------------------------

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SunriseAmber,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Slate,
            contentColor = Mist,
        ),
    ) {
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
            containerColor = Slate,
            contentColor = Mist,
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
