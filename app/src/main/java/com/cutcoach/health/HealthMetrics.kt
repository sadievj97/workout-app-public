package com.cutcoach.health

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * The numbers the protocol actually judges you by, worked out from what the
 * watch recorded.
 *
 * Everything here is plain Kotlin with no Android in it, so it can be tested
 * against awkward nights on the desktop rather than only discovered at 05:35.
 *
 * Nulls are load-bearing. A missing value means the watch has nothing, and the
 * app must then ask rather than guess: the protocol's own guardrail. Nothing
 * here ever returns a zero to stand in for an absence.
 */

/** One heart rate reading. */
data class HeartSample(val time: Instant, val bpm: Long)

/** A stretch of sleep as the watch recorded it. */
data class SleepWindow(val start: Instant, val end: Instant) {
    val duration: Duration get() = Duration.between(start, end)
}

/**
 * The main sleep of a night, which is the longest stretch recorded.
 *
 * Watches fragment sleep: a night often arrives as several sessions with gaps
 * where you stirred. Taking the longest rather than the first avoids treating a
 * twenty-minute early doze as the night.
 */
fun mainSleep(sessions: List<SleepWindow>): SleepWindow? =
    sessions.maxByOrNull { it.duration }

/**
 * Which night a sleep belongs to, dated by the evening you went to bed.
 *
 * Sleep that starts after midnight belongs to the night before, which is what
 * the bedtime ramp counts. The cut-off is late afternoon so an evening nap is
 * not mistaken for the following night.
 */
fun nightOf(start: Instant, zone: ZoneId): LocalDate {
    val local = ZonedDateTime.ofInstant(start, zone)
    return if (local.hour < EVENING_STARTS_AT) local.toLocalDate().minusDays(1) else local.toLocalDate()
}

/** Before this hour, a sleep belongs to the previous night. */
private const val EVENING_STARTS_AT = 16

/**
 * How late lights-out was against the ramp target for that night, in minutes.
 *
 * Positive is late, negative is early. Reported rather than judged: the app
 * says "45 minutes late" and leaves the verdict alone.
 */
fun minutesAfterTarget(
    sleepStart: Instant,
    target: LocalTime,
    night: LocalDate,
    zone: ZoneId,
): Long {
    val targetInstant = night.atTime(target).atZone(zone).toInstant()
    return Duration.between(targetInstant, sleepStart).toMinutes()
}

/**
 * The average heart rate across a sleep, which is what the protocol means by
 * sleeping heart rate.
 *
 * Null when the watch recorded no heart rate during the night, which happens.
 * A night with no samples is not a night with a heart rate of zero.
 */
fun sleepingHeartRate(samples: List<HeartSample>, sleep: SleepWindow): Int? {
    val during = samples.filter { it.time >= sleep.start && it.time <= sleep.end }
    if (during.isEmpty()) return null
    return during.map { it.bpm }.average().roundToInt()
}

/**
 * How long was spent with the heart rate inside a zone.
 *
 * Each sample is taken to describe the stretch until the next one, which is
 * the honest reading of a sampled signal. A long gap between samples is capped
 * rather than counted in full: a watch that stopped recording for twenty
 * minutes should not report twenty minutes in Zone 2.
 */
fun timeInZone(samples: List<HeartSample>, low: Int, high: Int): Duration {
    if (samples.size < 2) return Duration.ZERO
    val ordered = samples.sortedBy { it.time }

    var total = Duration.ZERO
    ordered.zipWithNext { sample, next ->
        if (sample.bpm >= low && sample.bpm <= high) {
            val gap = Duration.between(sample.time, next.time)
            total += if (gap > MAX_GAP) MAX_GAP else gap
        }
    }
    return total
}

/** A gap longer than this means the watch stopped recording, not that you held a zone. */
private val MAX_GAP: Duration = Duration.ofMinutes(2)

/** The mean heart rate across a set of samples, or null if there are none. */
fun meanHeartRate(samples: List<HeartSample>): Int? =
    if (samples.isEmpty()) null else samples.map { it.bpm }.average().roundToInt()

/**
 * Where a session's average heart rate sat against a target zone.
 *
 * The protocol wants this said plainly on a Zone 2 day: an average of 150
 * against a 115 to 140 zone is "above Zone 2", not a number to interpret.
 */
enum class ZoneVerdict { BELOW, IN_ZONE, ABOVE }

fun zoneVerdict(meanBpm: Int, low: Int, high: Int): ZoneVerdict = when {
    meanBpm < low -> ZoneVerdict.BELOW
    meanBpm > high -> ZoneVerdict.ABOVE
    else -> ZoneVerdict.IN_ZONE
}

/**
 * A rolling average over the most recent entries.
 *
 * Days with nothing recorded are skipped rather than counted as zero, and an
 * empty run gives null, because an average of nothing is not a number.
 */
fun rollingAverage(values: List<Double?>, days: Int): Double? {
    val present = values.take(days).filterNotNull()
    return if (present.isEmpty()) null else present.average()
}

/**
 * Whether a sleeping heart rate is far enough above its own baseline to mean
 * something, as the approved readiness rule defines it.
 *
 * Returns null until there is enough baseline to compare against: with three
 * nights of history, "five above average" is noise.
 */
fun isSleepingHeartRateElevated(
    tonight: Int?,
    baseline: List<Int>,
    aboveBy: Int,
    minimumNights: Int,
): Boolean? {
    if (tonight == null || baseline.size < minimumNights) return null
    return tonight >= baseline.average() + aboveBy
}
