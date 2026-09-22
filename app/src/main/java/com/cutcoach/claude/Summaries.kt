package com.cutcoach.claude

import com.cutcoach.data.db.BodyMetricEntity
import com.cutcoach.data.db.sevenDayAverageKg
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.health.DailyHealth
import com.cutcoach.health.describeRampGap
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * What the app is allowed to send.
 *
 * The rule from the brief is "only the summaries a request needs, never raw
 * health records", and this file is where that is true or not. Nothing here
 * takes a [DailyHealth] and serialises it: each function picks specific
 * numbers, rounds them, and writes a sentence.
 *
 * That means a heart rate sample never leaves the phone. Nor does a sleep
 * session's start instant, a step record's id, or anything that could be
 * matched back against Health Connect. What goes out is the sort of thing you
 * would tell a coach out loud.
 *
 * A missing number is written as "not recorded", never as zero, for the same
 * reason the screens do it: the model would otherwise reason about a night of
 * no sleep that never happened.
 */
object Summaries {

    /** What a substitution request needs: the exercise, and why it cannot be done. */
    fun forSubstitute(
        repo: ProtocolRepository,
        exerciseId: String,
        because: String,
        currentRung: Int,
    ): String {
        val exercise = repo.exercise(exerciseId)
        val scheduled = repo.protocol.sessions.values
            .flatMap { it.exercises }
            .firstOrNull { it.exerciseId == exerciseId }

        return buildString {
            appendLine("Exercise to replace: $exerciseId (${exercise?.name ?: "unknown"})")
            appendLine("Movement pattern: ${exercise?.pattern ?: "unknown"}")
            appendLine("Muscles: ${exercise?.muscles?.joinToString(", ") ?: "unknown"}")
            appendLine("Scheduled as: ${scheduled?.sets ?: "?"} sets of ${scheduled?.target?.describe() ?: "?"}")
            appendLine("Currently on ladder rung ${currentRung + 1}: " +
                "${exercise?.ladder?.getOrNull(currentRung) ?: "unknown"}")
            appendLine("Reason given: $because")
            appendLine()
            appendLine("Equipment available: ${repo.protocol.meta.equipment.joinToString(", ")}")
            appendLine()
            appendLine("Exercises in the library, by pattern:")
            repo.library.exercises
                .groupBy { it.pattern }
                .forEach { (pattern, list) ->
                    appendLine("  $pattern: ${list.joinToString(", ") { it.id }}")
                }
        }
    }

    /** What a harder-variation request needs: the ladder, and that it has run out. */
    fun forHarder(repo: ProtocolRepository, exerciseId: String): String {
        val exercise = repo.exercise(exerciseId)
        val scheduled = repo.protocol.sessions.values
            .flatMap { it.exercises }
            .firstOrNull { it.exerciseId == exerciseId }

        return buildString {
            appendLine("Exercise: $exerciseId (${exercise?.name ?: "unknown"})")
            appendLine("Movement pattern: ${exercise?.pattern ?: "unknown"}")
            appendLine("Scheduled as: ${scheduled?.sets ?: "?"} sets of ${scheduled?.target?.describe() ?: "?"}")
            appendLine("The whole ladder, now finished:")
            exercise?.ladder?.forEachIndexed { i, rung -> appendLine("  ${i + 1}. $rung") }
            exercise?.ladderEndNote?.let { appendLine("Note from the library: $it") }
            appendLine()
            appendLine("Equipment available: ${repo.protocol.meta.equipment.joinToString(", ")}")
        }
    }

    /**
     * Fourteen days, as aggregates.
     *
     * Per-day numbers, not per-record: one line a day with rounded values, as
     * the brief asks for. Nothing in here identifies a device, a timestamp or a
     * sample.
     */
    fun forWeeklyReview(
        repo: ProtocolRepository,
        today: LocalDate,
        days: List<DailyHealth>,
        body: List<BodyMetricEntity>,
        strength: Map<String, String>,
    ): String = buildString {
        appendLine("Program week: ${repo.programWeek(today) ?: "not started"}")
        appendLine("Targets: ${repo.protocol.dailyTargets.steps} steps a day, " +
            "sleeping heart rate goal ${repo.protocol.heartRate.sleepingHrGoalBpm.describe("bpm")}.")
        appendLine("Weight target: ${repo.protocol.meta.goal.targetWeightKg.describe("kg")}, " +
            "expected ${repo.protocol.meta.goal.expectedLossKgPerWeek.describe("kg")} a week.")
        appendLine()

        appendLine("Last 14 days, one line each (oldest first):")
        days.sortedBy { it.date }.forEach { day ->
            val night = day.night
            val slept = night?.let { "%.1fh".format(it.duration.toMinutes() / 60.0) } ?: "not recorded"
            val ramp = night?.minutesAfterTarget?.let { describeRampGap(it) } ?: "no target"
            val hr = night?.sleepingHeartRate?.toString() ?: "not recorded"
            val steps = day.steps?.toString() ?: "not recorded"
            appendLine("  ${day.date}: slept $slept, lights out $ramp, sleeping HR $hr, steps $steps")
        }
        appendLine()

        appendLine("7-day average weight, one per week (newest first):")
        (0..3).forEach { week ->
            val at = today.minusWeeks(week.toLong())
            val average = body.sevenDayAverageKg(at)
            appendLine("  week ending $at: ${average?.let { "%.1f kg".format(it) } ?: "not recorded"}")
        }
        appendLine()

        appendLine("Waist, Sundays (newest first):")
        body.filter { it.waistCm != null }.take(4).forEach {
            appendLine("  ${it.date}: %.1f cm".format(it.waistCm))
        }
        appendLine()

        appendLine("Strength, last session per exercise:")
        if (strength.isEmpty()) appendLine("  nothing logged yet")
        strength.forEach { (id, summary) -> appendLine("  $id: $summary") }
    }

    /** The mean of a run, rounded, or null when there is nothing to average. */
    fun meanOrNull(values: List<Int?>): Int? =
        values.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}
