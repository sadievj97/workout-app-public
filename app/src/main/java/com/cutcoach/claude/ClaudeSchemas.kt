package com.cutcoach.claude

import kotlinx.serialization.Serializable

/**
 * The shapes Claude is allowed to answer in.
 *
 * Claude returns JSON, not prose, so the answer can be checked before anyone
 * reads it. A sentence cannot be validated against a guardrail; a field can.
 *
 * Nothing here is trusted on arrival. Every one of these goes through
 * [com.cutcoach.claude.validate] before it reaches a screen, and a rejected
 * answer is shown as a rejection rather than quietly dropped — a model that
 * keeps proposing banned things is something the owner should be able to see.
 */

/** Swapping one exercise for another, within the same movement pattern. */
@Serializable
data class SubstituteAnswer(
    /** The exercise being replaced, by id. */
    val replaces: String,
    /** The replacement's id, from exercises.json. Never invented. */
    val withExerciseId: String? = null,
    /** Free text only when no library exercise fits; still validated. */
    val withDescription: String? = null,
    val sets: Int,
    val reps: Int? = null,
    val seconds: Int? = null,
    val reason: String,
    /** True when the request described pain rather than a missing bar. */
    val restInstead: Boolean = false,
)

/** A harder version of something whose ladder has run out. */
@Serializable
data class HarderAnswer(
    val forExercise: String,
    val name: String,
    val howTo: String,
    val sets: Int,
    val reps: Int? = null,
    val seconds: Int? = null,
    val reason: String,
)

/** The week, in plain language, with at most one change. */
@Serializable
data class WeeklyReviewAnswer(
    val sleep: String,
    val sleepingHeartRate: String,
    val steps: String,
    val weight: String,
    val strength: String,
    /** At most one, and it must obey the plateau rule. Null means no change. */
    val oneChange: ProposedChange? = null,
)

/**
 * The single change a weekly review may propose.
 *
 * Typed rather than free text precisely so the guardrails can be checked:
 * "eat 300 fewer calories" in a sentence is unenforceable, but
 * `caloriesKcalChange = -300` is a number that can be compared to the floor.
 */
@Serializable
data class ProposedChange(
    /** "steps", "calories", "exercise", or "nothing". */
    val kind: String,
    val stepsChange: Int = 0,
    val caloriesKcalChange: Int = 0,
    val setsChange: Int = 0,
    val sessionsChange: Int = 0,
    val exerciseId: String? = null,
    val reason: String,
)

/** Why an answer was refused, in words the owner can read. */
data class Rejection(val rule: String, val because: String)

/** The outcome of checking one answer. */
sealed interface Checked<out T> {
    data class Accepted<T>(val answer: T) : Checked<T>

    /**
     * Rejected, with every rule it broke.
     *
     * Plural on purpose: an answer that adds a set *and* names a rehab exercise
     * has two problems, and fixing one would still leave it invalid.
     */
    data class Rejected(val reasons: List<Rejection>) : Checked<Nothing>
}

/** What was sent, what came back, and what it cost. Logged for every call. */
@Serializable
data class CallRecord(
    val feature: String,
    val promptVersion: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val rejected: Boolean,
    val at: Long,
)
