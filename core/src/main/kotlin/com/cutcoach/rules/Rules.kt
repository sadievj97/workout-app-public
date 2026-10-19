package com.cutcoach.rules

import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.protocol.SessionExercise
import com.cutcoach.health.DailyHealth
import com.cutcoach.health.ZoneVerdict
import com.cutcoach.health.describeRampGap
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * The rules engine: plain Kotlin functions, no Android, no AI.
 *
 * Every number comes from protocol.json through [ProtocolRepository]. Nothing
 * here invents a threshold, a rate or a rep count, because the file is the
 * source of truth and a number copied into code is a number that will drift
 * from it.
 *
 * All of it is suggestion. The engine never changes a session, a target or a
 * rehab exercise; it produces [Recommendation]s and stops.
 */

/** Everything the day's suggestions are computed from. */
data class RuleInput(
    val today: LocalDate,
    val programWeek: Int?,
    /**
     * The 7-day rolling average weight at each week end, newest first.
     * Nulls are weeks with nothing weighed, and are never treated as zero.
     */
    val weeklyWeightKg: List<Double?> = emptyList(),
    /** Sunday waist measurements, newest first. */
    val weeklyWaistCm: List<Double?> = emptyList(),
    val today_: DailyHealth? = null,
    val lastNight: DailyHealth? = null,
    /** Sleeping heart rates over recent nights, for the readiness baseline. */
    val sleepingHrBaseline: List<Int> = emptyList(),
    val pain: PainReport? = null,
    /** When the plateau rule last suggested a change, so it can wait. */
    val lastPlateauChange: LocalDate? = null,
    /** Typed in by the owner. Never generated, modified or substituted. */
    val rehabExercises: List<String> = emptyList(),
    /** The last session's sets per exercise, for progression. */
    val lastSets: Map<String, List<SetResult>> = emptyMap(),
    /**
     * Exercises whose most recent session finished with nothing written down.
     *
     * [lastSets] may still hold numbers for these, from an older session. They
     * are real numbers and they are out of date, and acting on them would mean
     * advancing a ladder on the strength of a session that has since been
     * superseded by one nobody logged. So these are reported rather than used.
     */
    val unloggedSince: Set<String> = emptySet(),
    /** Which rung each exercise is currently on. */
    val rungs: Map<String, Int> = emptyMap(),
    /**
     * Samsung's Energy Scores, newest first.
     *
     * Only consulted when Health Connect has no sleep for last night. Empty is
     * the ordinary state: it needs the Samsung SDK and developer mode, and
     * neither is something the protocol depends on.
     */
    val energyScores: List<Float> = emptyList(),
    /** Needed to say what time a recorded session started. */
    val zone: ZoneId = ZoneId.systemDefault(),
)

/** What to do with one exercise next time. */
sealed interface Progression {
    /** Every set reached the top of the range: go up a rung and restart at the bottom. */
    data class Advance(val toRung: Int, val restartAt: Int) : Progression

    /** Some sets fell short: stay on this rung and add reps. */
    data object AddReps : Progression

    /** Not enough evidence, or nothing to change. */
    data object StayPut : Progression
}

/** Where an exercise appears in the week's sessions, with its target and sets. */
private fun ProtocolRepository.scheduledExercise(id: String): SessionExercise? =
    protocol.sessions.values.flatMap { it.exercises }.firstOrNull { it.exerciseId == id }

/**
 * Whether an exercise is ready for the next rung of its ladder.
 *
 * Double progression, as `progression_rules.implementation_notes` states: reps
 * to the top of the range on every set, then one rung, then restart at the
 * bottom. Max-rep exercises have no ceiling to hit, so `max_rep_advance` uses
 * the first set of the session as the yardstick instead — normal fatigue across
 * later sets should not block progression.
 *
 * An exercise with no entry in the protocol's sessions gets [Progression.StayPut].
 * Rehab work lives there: it is typed in by the owner, has no ladder and no
 * target, and the `rehab_untouched` guardrail means nothing here may move it.
 */
fun progressionFor(
    repo: ProtocolRepository,
    exerciseId: String,
    lastSets: List<SetResult>,
    currentRung: Int,
): Progression {
    val scheduled = repo.scheduledExercise(exerciseId) ?: return Progression.StayPut
    val ladder = repo.exercise(exerciseId)?.ladder.orEmpty()
    if (lastSets.isEmpty()) return Progression.StayPut

    val target = scheduled.target
    val topRung = (ladder.size - 1).coerceAtLeast(0)

    // Already at the top of the ladder: there is no next rung to offer. Phase 4
    // suggests a harder variation instead, which is what ladder_end_note is for.
    fun advanceOrStay(restartAt: Int): Progression =
        if (currentRung >= topRung) Progression.StayPut
        else Progression.Advance(toRung = currentRung + 1, restartAt = restartAt)

    if (target.isMaxReps) {
        val rule = repo.protocol.progressionRules.maxRepAdvance
        if (exerciseId !in rule.appliesTo) return Progression.StayPut
        val firstSet = lastSets.first().reps ?: return Progression.StayPut
        // The first set is the yardstick, as the protocol explains.
        return if (firstSet >= rule.reps) advanceOrStay(rule.reps) else Progression.AddReps
    }

    val top = target.max ?: return Progression.StayPut
    val bottom = target.min ?: top
    val achieved = lastSets.map { it.value(target.isTimed) }
    if (achieved.any { it == null }) return Progression.StayPut

    val everySetAtTheTop = achieved.all { it!! >= top }
    return if (everySetAtTheTop) advanceOrStay(bottom) else Progression.AddReps
}

/**
 * The progression as a recommendation, with the evidence in one line.
 *
 * The brief's own example: "All 4 sets hit 15 last Monday, so try feet elevated
 * at 8 reps."
 */
fun progressionRecommendation(
    repo: ProtocolRepository,
    exerciseId: String,
    lastSets: List<SetResult>,
    currentRung: Int,
    lastSessionOn: LocalDate? = null,
): Recommendation? {
    val scheduled = repo.scheduledExercise(exerciseId) ?: return null
    val exercise = repo.exercise(exerciseId) ?: return null
    val target = scheduled.target
    val units = if (target.isTimed) "seconds" else "reps"
    val when_ = lastSessionOn?.let { " on ${it.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase)}" } ?: ""

    return when (val next = progressionFor(repo, exerciseId, lastSets, currentRung)) {
        is Progression.Advance -> {
            val rung = exercise.ladder.getOrNull(next.toRung) ?: return null
            val hit = lastSets.mapNotNull { it.value(target.isTimed) }
            val evidence = if (target.isMaxReps) {
                "The first set reached ${hit.firstOrNull()} $units$when_"
            } else {
                "All ${lastSets.size} sets reached ${target.max} $units$when_"
            }
            Recommendation(
                kind = Recommendation.Kind.PROGRESSION,
                headline = "${exercise.name}: try ${rung.replaceFirstChar(Char::lowercase)} " +
                    "at ${next.restartAt} $units",
                reason = "$evidence, so the next rung is due.",
                exerciseId = exerciseId,
            )
        }

        Progression.AddReps -> {
            val hit = lastSets.mapNotNull { it.value(target.isTimed) }
            val short = hit.count { it < (target.max ?: 0) }
            Recommendation(
                kind = Recommendation.Kind.PROGRESSION,
                headline = "${exercise.name}: add $units on the same rung",
                reason = "$short of ${hit.size} sets came in under ${target.max} $units$when_.",
                exerciseId = exerciseId,
            )
        }

        Progression.StayPut -> null
    }
}

/**
 * Whether the last three weeks were flat, as `flat_definition` defines it.
 *
 * Both the weight and the waist must be flat: if the waist is still shrinking
 * it is not a plateau and nothing changes.
 */
fun isFlat(repo: ProtocolRepository, input: RuleInput): Boolean {
    val flat = repo.protocol.tracking.plateauRule.flatDefinition
    val weightDrop = dropAcross(input.weeklyWeightKg, flat.windowWeeks) ?: return false
    val waistDrop = dropAcross(input.weeklyWaistCm, flat.windowWeeks) ?: return false

    val weightIsFlat = weightDrop < flat.maxWeightDropKg
    val waistIsFlat = waistDrop < flat.maxWaistDropCm

    // "AND": if the waist is still shrinking it is not a plateau.
    return if (flat.combine.equals("AND", ignoreCase = true)) {
        weightIsFlat && waistIsFlat
    } else {
        weightIsFlat || waistIsFlat
    }
}

/**
 * How much a newest-first series fell across a window, or null when there is
 * not enough of it. A missing week gives null rather than a fabricated zero.
 */
private fun dropAcross(series: List<Double?>, weeks: Int): Double? {
    if (series.size <= weeks) return null
    val newest = series[0] ?: return null
    val oldest = series[weeks] ?: return null
    return oldest - newest
}

/**
 * Whether the 7-day average is falling faster than the protocol allows, over
 * two consecutive weeks. Triggers the `fast_loss_flag` guardrail.
 */
fun isLosingTooFast(repo: ProtocolRepository, input: RuleInput): Boolean {
    val ceiling = repo.protocol.meta.goal.maxLossKgPerWeek
    val weeks = input.weeklyWeightKg
    if (weeks.size < FAST_LOSS_WEEKS + 1) return false

    return (0 until FAST_LOSS_WEEKS).all { week ->
        val newer = weeks[week]
        val older = weeks[week + 1]
        newer != null && older != null && (older - newer) > ceiling
    }
}

/** The guardrail says two consecutive weeks, not one bad week. */
private const val FAST_LOSS_WEEKS = 2

/**
 * Every suggestion for today, in the order they should be read.
 *
 * A pain report short-circuits the whole list: the `pain_stop` guardrail means
 * no workout is suggested at all, so nothing about training is even computed.
 */
fun recommendationsFor(repo: ProtocolRepository, input: RuleInput): List<Recommendation> {
    val out = mutableListOf<Recommendation>()

    // ---- pain_stop, before anything else ---------------------------------
    if (input.pain?.kind == PainKind.SERIOUS) {
        out += Recommendation(
            kind = Recommendation.Kind.PAIN,
            headline = "No workout today.",
            reason = "You reported pain, so the protocol says stop and see a clinician " +
                "before training again.",
            tone = Recommendation.Tone.STOP,
        )
        return out
    }

    val tooFast = isLosingTooFast(repo, input)

    // ---- the scale --------------------------------------------------------
    out += weightRecommendations(repo, input, tooFast)

    // ---- the plateau rule -------------------------------------------------
    plateauRecommendation(repo, input, tooFast)?.let { out += it }

    // ---- last night -------------------------------------------------------
    out += sleepRecommendations(repo, input)

    // ---- the session the watch recorded -----------------------------------
    out += heartRateRecommendations(input)

    // ---- steps ------------------------------------------------------------
    input.today_?.steps?.let { steps ->
        val target = repo.protocol.dailyTargets.steps
        if (steps < target) {
            out += Recommendation(
                kind = Recommendation.Kind.STEPS,
                headline = "${target - steps} steps to go.",
                reason = "$steps of $target so far today.",
            )
        }
    }

    // ---- readiness, only where it is allowed to speak ---------------------
    readinessRecommendation(repo, input)?.let { out += it }

    // ---- progression ------------------------------------------------------
    input.lastSets.forEach { (exerciseId, sets) ->
        // An exercise whose most recent session was never written down gets an
        // explanation rather than a suggestion. The numbers in `sets` are real
        // but stale, and advancing a ladder on them would be the app telling
        // you to go up on the strength of a session you have since repeated
        // without recording. Saying so is the point: a rule that quietly
        // declines to speak is indistinguishable from a rule that is broken.
        if (exerciseId in input.unloggedSince) {
            unloggedRecommendation(repo, exerciseId)?.let { out += it }
            return@forEach
        }
        progressionRecommendation(
            repo = repo,
            exerciseId = exerciseId,
            lastSets = sets,
            currentRung = input.rungs[exerciseId] ?: 0,
        )?.let { out += it }
    }

    // Exercises with no numbers at all, whose only session was marked done.
    // They are absent from lastSets entirely, so the loop above never sees them.
    (input.unloggedSince - input.lastSets.keys).forEach { exerciseId ->
        unloggedRecommendation(repo, exerciseId)?.let { out += it }
    }

    return out
}

/**
 * "Your last Strength A has no numbers, so nothing moved."
 *
 * Deliberately phrased as a state of the log rather than as a reproach. Doing
 * the session away from the phone is a normal thing to do, and the app's job
 * here is to explain why no suggestion appeared, not to ask for compliance.
 */
private fun unloggedRecommendation(
    repo: ProtocolRepository,
    exerciseId: String,
): Recommendation? {
    val exercise = repo.exercise(exerciseId) ?: return null
    return Recommendation(
        kind = Recommendation.Kind.PROGRESSION,
        headline = "${exercise.name}: nothing to compare.",
        reason = "Your last session with it was recorded without set numbers, so " +
            "the ladder stays where it is. Add them from History and this becomes " +
            "a suggestion.",
        exerciseId = exerciseId,
    )
}

/**
 * The scale, with the week-1 guardrail applied.
 *
 * `no_week1_scale_verdict`: the data is still shown elsewhere, but no judgement
 * is made here, because early change is water and cortisol.
 */
private fun weightRecommendations(
    repo: ProtocolRepository,
    input: RuleInput,
    tooFast: Boolean,
): List<Recommendation> {
    if (input.programWeek == 1) return emptyList()

    val weeks = input.weeklyWeightKg
    val newest = weeks.getOrNull(0) ?: return emptyList()
    val previous = weeks.getOrNull(1) ?: return emptyList()
    val change = previous - newest
    val goal = repo.protocol.meta.goal

    if (tooFast) {
        return listOf(
            Recommendation(
                kind = Recommendation.Kind.WEIGHT,
                headline = "Losing faster than the protocol allows.",
                reason = "The 7-day average fell %.1f kg and %.1f kg over the last two weeks, ".format(
                    change,
                    (weeks.getOrNull(1) ?: 0.0) - (weeks.getOrNull(2) ?: 0.0),
                ).replace("-", "") +
                    "past the %.1f kg ceiling. Eat more, not less.".format(goal.maxLossKgPerWeek),
                tone = Recommendation.Tone.STOP,
            ),
        )
    }

    val expected = goal.expectedLossKgPerWeek
    val headline = when {
        change >= expected.min -> "On track: %.1f kg down this week.".format(change)
        change > 0 -> "Slower than planned: %.1f kg down this week.".format(change)
        else -> "The 7-day average did not fall this week."
    }
    return listOf(
        Recommendation(
            kind = Recommendation.Kind.WEIGHT,
            headline = headline,
            reason = "The protocol expects ${expected.describe("kg")} a week, " +
                "and you are at %.1f kg against a %.1f kg target.".format(newest, goal.targetWeightKg.max),
        ),
    )
}

/**
 * The plateau rule: exactly one change, then silence for the waiting period.
 *
 * The calorie lever is only offered while it would stay at or above
 * `calorie_floor_kcal`. The daily target already sits on that floor, so in
 * practice steps are the only lever this can pull — which is the
 * `calorie_floor` guardrail doing its job rather than a preference.
 */
private fun plateauRecommendation(
    repo: ProtocolRepository,
    input: RuleInput,
    tooFast: Boolean,
): Recommendation? {
    // Never suggest a further cut while already losing too fast.
    if (tooFast) return null
    if (!isFlat(repo, input)) return null

    val rule = repo.protocol.tracking.plateauRule
    input.lastPlateauChange?.let { last ->
        val weeksSince = ChronoUnit.WEEKS.between(last, input.today)
        if (weeksSince < rule.waitWeeksAfterChange) return null
    }

    val nutrition = repo.protocol.dailyTargets.nutritionDisplayOnly
    val afterCut = nutrition.caloriesKcal + rule.levers.caloriesKcalChange
    val calorieLeverAllowed = afterCut >= nutrition.calorieFloorKcal

    val flat = rule.flatDefinition
    val evidence = "The 7-day average and the waist have both been flat for " +
        "${flat.windowWeeks} weeks."

    return if (calorieLeverAllowed) {
        Recommendation(
            kind = Recommendation.Kind.PLATEAU,
            headline = "Take ${abs(rule.levers.caloriesKcalChange)} kcal off the daily target.",
            reason = "$evidence One change only, and not the other one.",
            tone = Recommendation.Tone.ATTENTION,
        )
    } else {
        Recommendation(
            kind = Recommendation.Kind.PLATEAU,
            headline = "Add ${rule.levers.stepsChange} steps to the daily target.",
            reason = "$evidence The calorie lever would go under the " +
                "${nutrition.calorieFloorKcal} kcal floor, so steps are the one change.",
            tone = Recommendation.Tone.ATTENTION,
        )
    }
}

/**
 * Last night against the ramp.
 *
 * Missing data is a question, never a guess: the `no_guessing` guardrail. A
 * night the app could not read is not a night of no sleep.
 */
private fun sleepRecommendations(
    repo: ProtocolRepository,
    input: RuleInput,
): List<Recommendation> {
    val night = input.lastNight?.night
        ?: return listOf(
            Recommendation(
                kind = Recommendation.Kind.CHECK_IN,
                headline = "How did you sleep last night?",
                reason = "Nothing was recorded, so nothing is assumed either way.",
            ),
        )

    val out = mutableListOf<Recommendation>()
    night.minutesAfterTarget?.let { late ->
        val target = repo.lightsOutOn(input.today.minusDays(1))
        out += Recommendation(
            kind = Recommendation.Kind.SLEEP,
            headline = "Lights out was ${describeRampGap(late)}.",
            reason = "The ramp asked for ${target ?: "an earlier bedtime"} last night.",
            tone = if (late > 0) Recommendation.Tone.ATTENTION else Recommendation.Tone.INFO,
        )
    }

    night.sleepingHeartRate?.let { bpm ->
        val goal = repo.protocol.heartRate.sleepingHrGoalBpm
        if (bpm > goal.maxInt) {
            out += Recommendation(
                kind = Recommendation.Kind.HEART_RATE,
                headline = "Sleeping heart rate $bpm bpm, above the goal.",
                reason = "The goal is ${goal.describe("bpm")}, from " +
                    "${repo.protocol.heartRate.sleepingHrBaselineBpm} at the start.",
                tone = Recommendation.Tone.ATTENTION,
            )
        }
    }
    return out
}

/** How a recorded session sat against the zone the protocol asked for. */
private fun heartRateRecommendations(input: RuleInput): List<Recommendation> =
    input.today_?.sessions.orEmpty().mapNotNull { session ->
        val mean = session.meanHeartRate ?: return@mapNotNull null
        val zone = session.zoneName ?: return@mapNotNull null
        // Naming the band matters: "above Zone 2" alone says you missed without
        // saying by how much or against what.
        val band = if (session.zoneLow != null && session.zoneHigh != null) {
            "${session.zoneLow} to ${session.zoneHigh} bpm"
        } else {
            null
        }
        val against = band?.let { " against $it" }.orEmpty()

        // Name the session. The watch often records several in a day, and
        // three identical "That sat in Zone 2" lines say nothing about which.
        // The watch leaves most sessions untitled, and two of the same length
        // in one day read identically, so fall back to when it started.
        val what = session.title?.takeIf { it.isNotBlank() }
            ?: "The ${SESSION_CLOCK.format(session.start.atZone(input.zone))} session"

        when (session.verdict) {
            ZoneVerdict.ABOVE -> Recommendation(
                kind = Recommendation.Kind.HEART_RATE,
                headline = "$what was above $zone.",
                reason = "Average $mean bpm$against, with only " +
                    "${session.inZone.toMinutes()} of ${session.total.toMinutes()} minutes " +
                    "inside it. Ease off.",
                tone = Recommendation.Tone.ATTENTION,
            )
            ZoneVerdict.BELOW -> Recommendation(
                kind = Recommendation.Kind.HEART_RATE,
                headline = "$what was below $zone.",
                reason = "Average $mean bpm$against, so there is room to push a little harder.",
            )
            ZoneVerdict.IN_ZONE -> Recommendation(
                kind = Recommendation.Kind.HEART_RATE,
                headline = "$what sat in $zone.",
                reason = "Average $mean bpm$against, " +
                    "${session.inZone.toMinutes()} minutes in the zone.",
            )
            null -> null
        }
    }

/**
 * The approved readiness rule, with all four of its conditions.
 *
 * It never fires without a baseline, never on missing data, never adds volume,
 * and never touches rehab. It is a suggestion the owner may ignore.
 */
private fun readinessRecommendation(
    repo: ProtocolRepository,
    input: RuleInput,
): Recommendation? {
    val rule = repo.protocol.readinessAdjustment
    if (!rule.isApproved) return null

    // No sleep recorded: the approved rule has nothing to work with, so the
    // fallback gets its turn. It never runs while sleep data exists.
    val night = input.lastNight?.night
        ?: return energyScoreFallback(repo, input)
    // Condition two: never before the baseline is real.
    if (input.sleepingHrBaseline.size < rule.baselineWindowDays) return null

    val hours = night.duration.toMinutes() / 60.0
    val shortSleep = hours < rule.sleepHoursThreshold

    val bpm = night.sleepingHeartRate
    val average = input.sleepingHrBaseline.average()
    val highHeartRate = bpm != null && bpm >= average + rule.sleepingHrAboveBaselineBpm

    // Both read from the protocol rather than written down here. "14-night"
    // was hardcoded in two of these sentences while baseline_window_days held
    // the real number, which is the drift the brief forbids: change the file
    // and the app would have gone on saying fourteen.
    val nights = rule.baselineWindowDays
    val threshold = rule.sleepHoursThreshold.tidy()

    return when {
        shortSleep && highHeartRate -> Recommendation(
            kind = Recommendation.Kind.READINESS,
            headline = "Red day: an easy walk instead of strength, if you want it.",
            reason = "%.1f hours of sleep and a sleeping heart rate of $bpm, ".format(hours) +
                "%.0f above your $nights-night average. A suggestion, not a change.".format(
                    bpm!! - average,
                ),
            tone = Recommendation.Tone.ATTENTION,
        )
        shortSleep -> Recommendation(
            kind = Recommendation.Kind.READINESS,
            headline = "Yellow day: consider dropping one set from each exercise.",
            reason = "%.1f hours of sleep, under the $threshold-hour mark. ".format(hours) +
                "Rehab stays as it is.",
            tone = Recommendation.Tone.ATTENTION,
        )
        highHeartRate -> Recommendation(
            kind = Recommendation.Kind.READINESS,
            headline = "Yellow day: consider dropping one set from each exercise.",
            reason = "Sleeping heart rate $bpm, %.0f above your $nights-night average. ".format(
                bpm!! - average,
            ) + "Rehab stays as it is.",
            tone = Recommendation.Tone.ATTENTION,
        )
        else -> null
    }
}

/** Short enough to sit inside a headline. */
private val SESSION_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Samsung's Energy Score, standing in for a night the watch did not record.
 *
 * Approved on 2026-09-22 as a fallback rather than a third condition: the
 * failure it fixes is a readiness rule that cannot fire, not a rule that was
 * wrong. It runs only where the approved rule is silent.
 *
 * It stops at a yellow day. A red day means both a short night and an elevated
 * heart rate, and one number cannot tell those apart — a stand-in should be
 * more cautious than the thing it stands in for, not less.
 *
 * Returns null, not a verdict, when there is no score or no baseline. That
 * leaves the check-in question to speak instead, which is what `no_guessing`
 * asks for.
 */
private fun energyScoreFallback(
    repo: ProtocolRepository,
    input: RuleInput,
): Recommendation? {
    val fallback = repo.protocol.readinessAdjustment.energyScoreFallback ?: return null
    if (!fallback.isApproved) return null

    val scores = input.energyScores
    // The same bar the sleeping heart rate rule sets: with three days of
    // history, "five below average" is noise.
    if (scores.size < fallback.baselineWindowDays) return null

    val today = scores.first()
    val average = scores.average()
    val below = average - today
    if (below < fallback.belowBaseline) return null

    return Recommendation(
        kind = Recommendation.Kind.READINESS,
        headline = "Yellow day: consider dropping one set from each exercise.",
        reason = "No sleep was recorded, so this is Samsung's Energy Score instead: " +
            "%.0f against a %.0f average over %d days. Rehab stays as it is.".format(
                today,
                average,
                scores.size,
            ),
        tone = Recommendation.Tone.ATTENTION,
    )
}

/**
 * A protocol number as a person would write it: "6", not "6.0", and "6.5" when
 * it really is.
 *
 * These thresholds are Doubles because the protocol is allowed to say 6.5
 * hours, and almost all of them are whole. Writing one into a sentence with
 * "%d" is what crashed the app on every launch —
 * `IllegalFormatConversionException: d != java.lang.Double` — because a format
 * string is a runtime contract the compiler cannot check. This gives the
 * sentence a String and takes the format specifier out of it entirely.
 */
internal fun Double.tidy(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
