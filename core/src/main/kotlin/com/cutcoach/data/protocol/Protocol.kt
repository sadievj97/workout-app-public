package com.cutcoach.data.protocol

import com.cutcoach.data.json.NightSpan
import com.cutcoach.data.json.NumRange
import kotlinx.serialization.Serializable

/**
 * The shape of protocol.json.
 *
 * This file is the source of truth for every number in the app. Nothing here
 * carries a default that invents a value: where a field is optional it is
 * optional in the protocol too. If a number is wrong, the fix is to edit
 * protocol.json, not Kotlin.
 *
 * Property names are camelCase and map onto the file's snake_case
 * automatically, so `programStartDate` reads `program_start_date`.
 */
@Serializable
data class Protocol(
    val meta: Meta,
    val sessionDefaults: SessionDefaults,
    val dailyTimeline: List<TimelineEntry>,
    val reminders: Reminders = Reminders(),
    /** Keyed by "MON" ... "SUN". */
    val weeklySchedule: Map<String, ScheduledDay>,
    /** Keyed by session id, for example "strength_a". */
    val sessions: Map<String, SessionDefinition>,
    val dailyTargets: DailyTargets,
    val heartRate: HeartRate,
    val sleep: Sleep,
    val tracking: Tracking,
    val progressionRules: ProgressionRules,
    val guardrails: Guardrails,
    val readinessAdjustment: ReadinessAdjustment,
)

@Serializable
data class Meta(
    val name: String,
    val principle: String,
    /**
     * How many weeks the program runs, or null for open-ended.
     *
     * Null is the normal case: the finish line is a weight, not a date. A
     * number here caps the week counter, which silently switches the deloads
     * off past the end, so it should only be set deliberately.
     */
    val programLengthWeeks: Int? = null,
    val programLengthNote: String = "",
    /** ISO date, for example "2026-09-21". Null until the owner sets it. */
    val programStartDate: String? = null,
    val goal: Goal,
    val equipment: List<String>,
    val note: String = "",
)

@Serializable
data class Goal(
    val startWeightKg: Double,
    val targetWeightKg: NumRange,
    /** Why the target is what it is, when it has been changed. */
    val targetWeightNote: String = "",
    val targetMonth: String,
    val expectedLossKgPerWeek: NumRange,
    val maxLossKgPerWeek: Double,
    val approxDailyDeficitKcal: Int,
)

/** Timings the protocol does not state, resolved with the owner. */
@Serializable
data class SessionDefaults(
    val restSeconds: Int,
    val switchSidesSeconds: Int,
    /** How far past the planned duration a session runs before the clock flags it. */
    val sessionOverrunGraceMin: Int = 5,
)

@Serializable
data class TimelineEntry(
    /** 24-hour "HH:mm". */
    val time: String,
    val label: String,
    /**
     * The one entry that means bedtime.
     *
     * Its [time] is the end-state target. During the ramp the real target for
     * tonight comes from `bedtime_ramp` instead, which is why this needs
     * marking rather than being found by matching the label text.
     */
    val isLightsOut: Boolean = false,
)

/**
 * Which parts of the day are worth a notification.
 *
 * The evening half of the timeline is the machinery that makes a 05:00 wake
 * possible, and it is the half the owner will not open the app to read.
 */
@Serializable
data class Reminders(
    /** Timeline entries at or after this time are on by default. "HH:mm". */
    val defaultOnFrom: String = "20:00",
    val note: String = "",
    val lieInGuardrail: String = "",
)

@Serializable
data class ScheduledDay(
    val sessionId: String,
    /** "HH:mm", or "any" for Sunday. */
    val start: String,
    val durationMin: NumRange,
) {
    val hasFixedStart: Boolean get() = start != "any"
}

/**
 * One session. Strength days carry `exercises`; cardio days carry `options` and
 * a heart-rate zone instead. Both shapes live in one class because the protocol
 * keeps them in one object.
 */
@Serializable
data class SessionDefinition(
    val name: String,
    val focus: String? = null,
    val warmupMin: Int? = null,
    val exercises: List<SessionExercise> = emptyList(),
    val options: List<String> = emptyList(),
    val hrZone: String? = null,
    val intervalStructure: IntervalStructure? = null,
    /** Entered by the owner from the rehab program. The app never changes these. */
    val rehabExercises: List<String> = emptyList(),
    val note: String? = null,
) {
    val isStrength: Boolean get() = exercises.isNotEmpty()
}

@Serializable
data class SessionExercise(
    val exerciseId: String,
    val sets: Int,
    val target: Target,
    val eachSide: Boolean = false,
)

/**
 * What a set is aiming at: a rep range, a hold in seconds, or as many reps as
 * possible.
 */
@Serializable
data class Target(
    /** "reps", "seconds", or "max_reps". */
    val type: String,
    val min: Int? = null,
    val max: Int? = null,
) {
    val isMaxReps: Boolean get() = type == "max_reps"
    val isTimed: Boolean get() = type == "seconds"

    /** "8 to 15 reps", "30 seconds", "max reps". */
    fun describe(): String = when {
        isMaxReps -> "max reps"
        min == null -> type
        isTimed -> if (min == max) "$min seconds" else "$min to $max seconds"
        else -> if (min == max) "$min reps" else "$min to $max reps"
    }
}

@Serializable
data class IntervalStructure(
    val appliesTo: String,
    val workSeconds: Int,
    val recoverySeconds: Int,
    val rounds: Int,
    /** Where to begin in week 1, building toward `rounds`. */
    val roundsStart: Int,
    val warmupMin: Int,
    val cooldownMin: Int,
)

@Serializable
data class DailyTargets(
    val steps: Int,
    val stepsBaselineAtStart: Int,
    val nutritionDisplayOnly: Nutrition,
)

/** Shown as reminders only. The app does not track food in any phase. */
@Serializable
data class Nutrition(
    val caloriesKcal: Int,
    /** The guardrail calorie_floor as a number. Never suggest going below it. */
    val calorieFloorKcal: Int = 1900,
    val calorieFloorNote: String = "",
    val proteinG: Int,
    val proteinNote: String,
    val fatMinG: Int,
    val fibreG: Int,
    val waterL: NumRange,
    val note: String,
)

@Serializable
data class HeartRate(
    val zone2Bpm: NumRange,
    val zone2TalkTest: String,
    val intervalsBpm: NumRange,
    val sleepingHrBaselineBpm: Int,
    val sleepingHrGoalBpm: NumRange,
)

@Serializable
data class Sleep(
    /** "HH:mm". The same every day, weekends included. */
    val wakeTime: String,
    val wakeSameTimeEveryDay: Boolean,
    /** ISO date of night 1 of the ramp. */
    val rampStartDate: String? = null,
    val bedtimeRamp: List<RampStep>,
    /**
     * Shorter than this and a recorded sleep is a nap, not the night.
     *
     * Judged by length rather than by the hour it started, because a 5 a.m.
     * bedtime is a very late night and the ramp needs to say so.
     */
    val napThresholdHours: Double = 2.0,
    val napThresholdNote: String = "",
    val notes: List<String> = emptyList(),
)

@Serializable
data class RampStep(
    val nights: NightSpan,
    /** "HH:mm". */
    val lightsOut: String,
)

@Serializable
data class Tracking(
    val weight: WeightTracking,
    val waist: WaistTracking,
    val plateauRule: PlateauRule,
)

@Serializable
data class WeightTracking(val frequency: String, val display: String)

@Serializable
data class WaistTracking(val frequency: String, val day: String, val how: String)

@Serializable
data class PlateauRule(
    val condition: String,
    val action: String,
    val levers: PlateauLevers = PlateauLevers(),
    val waitWeeksAfterChange: Int,
    val flatDefinition: FlatDefinition,
)

/**
 * The two levers as numbers, so the engine never parses them out of prose.
 * Exactly one is ever suggested: the protocol says never both.
 */
@Serializable
data class PlateauLevers(
    val caloriesKcalChange: Int = -150,
    val stepsChange: Int = 1500,
    val note: String = "",
)

@Serializable
data class FlatDefinition(
    val windowWeeks: Int,
    val maxWeightDropKg: Double,
    val maxWaistDropCm: Double,
    /** "AND": both conditions must hold before a plateau is called. */
    val combine: String,
    val explanation: String,
)

@Serializable
data class ProgressionRules(
    /** Reps first, then leverage, then load. */
    val order: List<String>,
    val reps: String,
    val leverageOptions: List<String>,
    val load: LoadRule,
    val deload: DeloadRule,
    val maxRepAdvance: MaxRepAdvance,
)

@Serializable
data class LoadRule(
    val method: String,
    val conversion: String,
    val referenceMaxKg: Int,
)

@Serializable
data class DeloadRule(
    val everyNthWeek: Int,
    val rule: String,
    val setMultiplier: Double,
    val rounding: String,
)

@Serializable
data class MaxRepAdvance(
    val appliesTo: List<String>,
    val trigger: String,
    val reps: Int,
    val explanation: String,
)

@Serializable
data class Guardrails(
    val fromProtocol: List<Guardrail>,
    val addedForTheApp: List<Guardrail>,
) {
    val all: List<Guardrail> get() = fromProtocol + addedForTheApp
}

@Serializable
data class Guardrail(val id: String, val rule: String)

@Serializable
data class ReadinessAdjustment(
    val status: String,
    val rule: String,
    val sleepHoursThreshold: Double,
    val sleepingHrAboveBaselineBpm: Int,
    val baselineWindowDays: Int,
    val energyScoreFallback: EnergyScoreFallback? = null,
    val conditions: List<String>,
) {
    val isApproved: Boolean get() = status == "approved"
}

/**
 * What to do when Health Connect has no sleep for last night.
 *
 * A fallback, not a third condition: the approved rule is unchanged, and this
 * only speaks where that rule would otherwise have nothing to say. It stops at
 * a yellow day because one number cannot tell a short night from an elevated
 * heart rate, and a stand-in should be more cautious than what it stands in
 * for.
 */
@Serializable
data class EnergyScoreFallback(
    val status: String,
    val rule: String,
    /** How far below its own average counts as a low day. */
    val belowBaseline: Int,
    val baselineWindowDays: Int,
    val whyFive: String = "",
    val whyNeverRed: String = "",
    val conditions: List<String> = emptyList(),
) {
    val isApproved: Boolean get() = status == "approved"
}
