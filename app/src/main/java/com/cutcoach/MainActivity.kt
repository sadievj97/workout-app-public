package com.cutcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.CutCoachTheme
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SunriseAmber
import com.cutcoach.ui.theme.tabular
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Step 3 of Phase 1: proving the protocol loads.
 *
 * The screen below is a check, not a feature. It reads protocol.json and
 * exercises.json out of the app's assets and prints what it found, so a
 * mistake in either file shows up here rather than halfway through a session.
 * Step 4 replaces it with the real Today screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CutCoachTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LoadCheckScreen()
                }
            }
        }
    }
}

@Composable
private fun LoadCheckScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val result = remember { ProtocolRepository.load(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Load check", style = MaterialTheme.typography.displaySmall, color = SunriseAmber)

        when (result) {
            is ProtocolLoad.Failure -> FailureReport(result)
            is ProtocolLoad.Success -> SuccessReport(result.repository)
        }
    }
}

@Composable
private fun FailureReport(failure: ProtocolLoad.Failure) {
    Text(
        "The protocol did not load.",
        style = MaterialTheme.typography.titleLarge,
        color = SunriseAmber,
    )
    Text(failure.message, style = MaterialTheme.typography.bodyMedium, color = Mist)
    Text(
        "Fix the file in app/src/main/assets and run again.",
        style = MaterialTheme.typography.bodySmall,
        color = MistDim,
    )
}

@Composable
private fun SuccessReport(repo: ProtocolRepository) {
    val today = LocalDate.now()
    val protocol = repo.protocol
    val week = repo.programWeek(today)
    val schedule = repo.scheduleFor(today.dayOfWeek)
    val session = repo.sessionFor(today.dayOfWeek)

    Section("Both files parsed") {
        Row2("Protocol", protocol.meta.name)
        Row2("Exercises in library", "${repo.library.exercises.size}")
        Row2("Cardio options", "${repo.library.cardio.size}")
        Row2("Warm-up blocks", "${repo.warmup.blocks.size} over ${repo.warmup.durationMin} min")
        Row2("Guardrails", "${protocol.guardrails.all.size}")
    }

    Section("Where we are") {
        Row2("Today", today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")))
        Row2("Program starts", protocol.meta.programStartDate ?: "not set")
        Row2("Program week", week?.let { "$it of ${protocol.meta.programLengthWeeks}" } ?: "not started")
        Row2("Deload week", if (week != null && repo.isDeloadWeek(week)) "yes, sets cut by a third" else "no")
    }

    Section("Today's session") {
        if (schedule == null || session == null) {
            Text("No session scheduled.", style = MaterialTheme.typography.bodyMedium, color = Mist)
        } else {
            Row2("Session", session.name)
            Row2("Starts", if (schedule.hasFixedStart) schedule.start else "any time")
            Row2("Planned", schedule.durationMin.describe("min"))

            if (session.isStrength) {
                session.exercises.forEach { item ->
                    val name = repo.exercise(item.exerciseId)?.name ?: item.exerciseId
                    val sets = repo.setsFor(item.sets, week)
                    val sides = if (item.eachSide) " each side" else ""
                    Text(
                        "$name  -  $sets x ${item.target.describe()}$sides",
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = Mist,
                    )
                }
            } else {
                Row2("Options", session.options.joinToString(", "))
                session.intervalStructure?.let {
                    Row2(
                        "Intervals",
                        "${it.rounds} x ${it.workSeconds}s hard / ${it.recoverySeconds}s easy",
                    )
                }
            }
        }
    }

    Section("Tonight") {
        val night = repo.rampNight(today)
        Row2("Ramp night", night?.toString() ?: "ramp not started")
        Row2("Lights out", repo.lightsOutOn(today)?.toString() ?: "not set")
        Row2("Wake", repo.wakeTime.toString())
    }

    Section("Targets, reminders only") {
        val n = protocol.dailyTargets.nutritionDisplayOnly
        Row2("Steps", "${protocol.dailyTargets.steps}")
        Row2("Calories", "${n.caloriesKcal} kcal")
        Row2("Protein", "${n.proteinG} g")
        Row2("Water", n.waterL.describe("L"))
        Row2("Rest between sets", "${repo.restSeconds}s")
    }

    Section("Numbers line up") {
        Text("11111111", style = MaterialTheme.typography.headlineSmall.tabular(), color = CoolSky)
        Text("88888888", style = MaterialTheme.typography.headlineSmall.tabular(), color = CoolSky)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MistDim)
        content()
    }
}

/** A label on the left and its value on the right, for a scannable column. */
@Composable
private fun Row2(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MistDim)
        Text(value, style = MaterialTheme.typography.bodyMedium.tabular(), color = Mist)
    }
}
