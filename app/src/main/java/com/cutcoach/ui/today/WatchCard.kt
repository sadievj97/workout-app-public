package com.cutcoach.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.health.DailyHealth
import com.cutcoach.health.ZoneVerdict
import com.cutcoach.health.describeRampGap
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.LabelledValue
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SunriseAmber
import java.time.format.DateTimeFormatter

/**
 * What the watch recorded, on the Today screen.
 *
 * Where the watch has nothing, this asks rather than showing a zero. A night
 * with no sleep data is not a night of no sleep, and the protocol is explicit
 * that missing data gets a question, not a guess.
 *
 * Watch-estimated calories appear nowhere here, by design.
 */
@Composable
fun WatchCard(
    repo: ProtocolRepository,
    health: DailyHealth?,
    lastNight: DailyHealth?,
    modifier: Modifier = Modifier,
) {
    CutCard(modifier = modifier) {
        SectionLabel("From the watch")

        if (health == null) {
            Text(
                "Not read yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MistDim,
            )
            return@CutCard
        }

        LastNight(repo, lastNight?.night ?: health.night)

        Box(Modifier.height(4.dp))

        Steps(repo, health.steps)

        health.sessions.forEach { session ->
            Box(Modifier.height(4.dp))
            SessionZone(session)
        }

        if (!health.hasAnything && lastNight?.night == null) {
            Text(
                "Nothing from the watch today. Has Samsung Health synced?",
                style = MaterialTheme.typography.bodySmall,
                color = SunriseAmber,
            )
        }
    }
}

@Composable
private fun LastNight(repo: ProtocolRepository, night: com.cutcoach.health.SleepNight?) {
    if (night == null) {
        // The guardrail, spoken: ask, do not assume.
        Text(
            "No sleep recorded for last night.",
            style = MaterialTheme.typography.bodyMedium,
            color = SunriseAmber,
        )
        Text(
            "Did you wear the watch? Nothing is assumed either way.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
        return
    }

    val hours = night.duration.toHours()
    val minutes = night.duration.toMinutes() % 60
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val zone = java.time.ZoneId.systemDefault()

    LabelledValue("Slept", "${hours}h ${minutes}m")
    LabelledValue(
        if (night.isTheNight) "Lights out" else "Recorded from",
        night.start.atZone(zone).format(formatter),
    )

    if (!night.isTheNight) {
        // A doze has no bedtime to be late for. A very late night does, and is
        // reported below however bad it looks.
        Text(
            "Too short to be the night, so it is not measured against the ramp. " +
                "A nap, or the watch caught only part of the night.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
    }

    night.minutesAfterTarget?.let { late ->
        LabelledValue(
            "Against the ramp",
            describeRampGap(late),
            valueColor = if (late > LATE_ENOUGH_TO_MENTION) SunriseAmber else Mist,
        )
    }

    night.sleepingHeartRate?.let { bpm ->
        val goal = repo.protocol.heartRate.sleepingHrGoalBpm
        LabelledValue(
            "Sleeping heart rate",
            "$bpm bpm",
            valueColor = if (bpm.toDouble() in goal) CoolSky else Mist,
        )
        Text(
            "Goal ${goal.describe("bpm")}, from ${repo.protocol.heartRate.sleepingHrBaselineBpm}.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
    }

    if (night.fragments > 1) {
        Text(
            "The watch split the night into ${night.fragments} stretches; the " +
                "longest is used.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
    }
}

/** Beyond this, being late is worth colouring rather than just reporting. */
private const val LATE_ENOUGH_TO_MENTION = 20L

@Composable
private fun Steps(repo: ProtocolRepository, steps: Long?) {
    val target = repo.protocol.dailyTargets.steps

    if (steps == null) {
        Text(
            "No steps recorded today.",
            style = MaterialTheme.typography.bodyMedium,
            color = MistDim,
        )
        return
    }

    LabelledValue(
        "Steps",
        "$steps of $target",
        valueColor = if (steps >= target) CoolSky else Mist,
    )
}

@Composable
private fun SessionZone(session: com.cutcoach.health.WorkoutSession) {
    val minutes = session.total.toMinutes()
    LabelledValue(session.title ?: "Recorded session", "$minutes min")

    if (session.meanHeartRate == null) {
        Text(
            "No heart rate recorded for this session.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
        return
    }

    val inZone = session.inZone.toMinutes()
    LabelledValue("In ${session.zoneName}", "$inZone min")

    val verdict = when (session.verdict) {
        ZoneVerdict.ABOVE -> "Average ${session.meanHeartRate} bpm, above ${session.zoneName}."
        ZoneVerdict.BELOW -> "Average ${session.meanHeartRate} bpm, below ${session.zoneName}."
        ZoneVerdict.IN_ZONE -> "Average ${session.meanHeartRate} bpm, in ${session.zoneName}."
        null -> null
    }
    verdict?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (session.verdict == ZoneVerdict.IN_ZONE) MistDim else SunriseAmber,
        )
    }
}

private operator fun com.cutcoach.data.json.NumRange.contains(value: Double): Boolean =
    value >= min && value <= max
