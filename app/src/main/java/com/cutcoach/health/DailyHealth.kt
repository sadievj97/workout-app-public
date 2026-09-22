package com.cutcoach.health

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * What the watch knows about one day.
 *
 * Every field is nullable on purpose. Null means the watch recorded nothing,
 * and the screens turn that into a question rather than a number: a night with
 * no data is not a night of zero sleep, and a day with no steps recorded is not
 * a day spent sitting still.
 */
data class DailyHealth(
    val date: LocalDate,
    val steps: Long? = null,
    val night: SleepNight? = null,
    val weightKg: Double? = null,
    val sessions: List<WorkoutSession> = emptyList(),
) {
    val hasAnything: Boolean
        get() = steps != null || night != null || weightKg != null || sessions.isNotEmpty()
}

/**
 * Last night, as the watch saw it.
 *
 * [minutesAfterTarget] is null when the bedtime ramp has not started or the
 * protocol has no target for that night: there is nothing to be late against.
 */
data class SleepNight(
    val start: Instant,
    val end: Instant,
    val duration: Duration,
    /** Mean heart rate across the night. Null if the watch recorded none. */
    val sleepingHeartRate: Int? = null,
    /** Null when there is no ramp target, or when this was a nap rather than the night. */
    val minutesAfterTarget: Long? = null,
    /**
     * False when the sleep was too short to be the night: a nap, or a fragment
     * the watch caught after you were already up. The ramp comparison is then
     * withheld, because there is no bedtime in a twenty-minute doze.
     *
     * A very late night is still a night. Length decides this, not the hour.
     */
    val isTheNight: Boolean = true,
) {
    val fragments: Int get() = fragmentCount
    internal var fragmentCount: Int = 1
}

/**
 * One recorded workout, with the part that matters for a cardio day: how long
 * it actually spent in the zone the protocol asked for.
 */
data class WorkoutSession(
    val start: Instant,
    val end: Instant,
    val title: String?,
    val total: Duration,
    val meanHeartRate: Int? = null,
    val inZone: Duration = Duration.ZERO,
    /** Which zone it was measured against, for the label. */
    val zoneName: String? = null,
    /**
     * The band itself, in bpm.
     *
     * Carried rather than looked up again, so anything reporting on the session
     * can say what it was measured against. "Above Zone 2" without naming where
     * Zone 2 ends tells you that you failed without telling you at what.
     */
    val zoneLow: Int? = null,
    val zoneHigh: Int? = null,
    val verdict: ZoneVerdict? = null,
)
