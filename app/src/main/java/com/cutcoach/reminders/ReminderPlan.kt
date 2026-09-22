package com.cutcoach.reminders

import com.cutcoach.data.protocol.ReminderMoment
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * When the next reminder is due, and what it should say.
 *
 * Plain Kotlin with no Android in it, so the awkward cases — the moment that
 * has already passed today, the ramp target that moves overnight — can be
 * tested on the desktop rather than discovered by a notification arriving at
 * the wrong hour.
 *
 * Nothing here can produce a wake-up reminder or a later bedtime. The
 * protocol's lie-in guardrail is not a display rule: it is a limit on what this
 * file is able to emit at all.
 */

/** One scheduled reminder: when it fires, and the words it carries. */
data class PlannedReminder(
    val momentId: String,
    val at: LocalDateTime,
    val title: String,
    val body: String,
)

/**
 * Every reminder the owner has switched on, with the next time each is due.
 *
 * [chosen] holds moment ids. A moment absent from it falls back to the
 * protocol's own default, so a fresh install reminds the evening without the
 * owner having to set anything up.
 *
 * [momentsOn] is asked separately for today and for tomorrow because the
 * bedtime target moves: tonight's 22:45 becomes 22:15 four nights later, and a
 * reminder scheduled from today's table would fire at yesterday's target.
 */
fun plan(
    momentsOn: (LocalDate) -> List<ReminderMoment>,
    chosen: Map<String, Boolean>,
    now: LocalDateTime,
): List<PlannedReminder> {
    val today = now.toLocalDate()
    val tomorrow = today.plusDays(1)

    val stillToCome = momentsOn(today)
        .map { it to LocalDateTime.of(today, it.time) }
        .filter { (_, at) -> at.isAfter(now) }

    val doneIds = stillToCome.map { (moment, _) -> moment.id }.toSet()

    val nextTime = momentsOn(tomorrow)
        .filter { it.id !in doneIds }
        .map { it to LocalDateTime.of(tomorrow, it.time) }

    return (stillToCome + nextTime)
        .filter { (moment, _) -> chosen[moment.id] ?: moment.onByDefault }
        .map { (moment, at) -> reminderFor(moment, at) }
        .sortedBy { it.at }
}

/**
 * What a reminder says.
 *
 * The lights-out one carries its own time, because during the ramp that is not
 * the time written in the timeline and the difference is the whole point.
 */
fun reminderFor(moment: ReminderMoment, at: LocalDateTime): PlannedReminder {
    val body = if (moment.isLightsOut) {
        "Lights out now. Tonight's target is ${at.toLocalTime().format(CLOCK)}."
    } else {
        "${moment.label}. On the way to lights out."
    }
    return PlannedReminder(
        momentId = moment.id,
        at = at,
        title = if (moment.isLightsOut) "Lights out" else moment.label,
        body = body,
    )
}

/** How long to wait before firing. Never negative: a missed moment fires at once. */
fun delayUntil(at: LocalDateTime, now: LocalDateTime): Duration {
    val gap = Duration.between(now, at)
    return if (gap.isNegative) Duration.ZERO else gap
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
