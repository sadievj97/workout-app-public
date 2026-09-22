package com.cutcoach.claude

import com.cutcoach.data.protocol.ProtocolRepository

/**
 * Checking Claude's answers against the protocol's guardrails.
 *
 * The model is a suggestion engine, not an authority. Everything it returns is
 * treated as a proposal from somewhere outside the app, and the rules engine
 * decides whether it may be shown. That ordering is the whole design: the
 * guardrails live in code that is unit tested, and the model cannot talk its
 * way past them because it never gets asked.
 *
 * Every number checked here comes from protocol.json.
 */

/** The rules an answer can break, named as they are in `guardrails`. */
private const val NO_EXTRA_VOLUME = "no_extra_volume"
private const val REHAB_UNTOUCHED = "rehab_untouched"
private const val CALORIE_FLOOR = "calorie_floor"
private const val KEEP_LEG_WORK = "keep_leg_work"
private const val PAIN_STOP = "pain_stop"
private const val NO_WEEKEND_SLEEP_IN = "no_weekend_sleep_in"
private const val NO_EATING_BACK = "no_eating_back_watch_calories"

/**
 * Words that mean a lie-in however they are dressed up.
 *
 * Checked against free text because a model asked for plain language will
 * write plain language, and `no_weekend_sleep_in` is about what the owner ends
 * up reading rather than about a field.
 */
private val LIE_IN_PHRASES = listOf(
    "sleep in", "lie-in", "lie in", "later wake", "wake later",
    "extra hour in bed", "stay in bed", "skip the alarm",
)

/** Words that mean eating back what a watch guessed you burned. */
private val EAT_BACK_PHRASES = listOf(
    "eat back", "eat them back", "calories burned", "burned calories",
    "replace the calories", "earned calories",
)

/**
 * Check a substitution.
 *
 * The dangerous answers here are the plausible ones: a swap that quietly adds
 * a set, names a rehab exercise, or replaces a leg exercise with another
 * upper-body one.
 */
fun validate(
    repo: ProtocolRepository,
    answer: SubstituteAnswer,
    rehabExercises: List<String>,
): Checked<SubstituteAnswer> {
    val reasons = mutableListOf<Rejection>()

    val original = repo.protocol.sessions.values
        .flatMap { it.exercises }
        .firstOrNull { it.exerciseId == answer.replaces }

    if (original == null) {
        reasons += Rejection(
            rule = "unknown_exercise",
            because = "\"${answer.replaces}\" is not an exercise this protocol schedules.",
        )
    }

    // rehab_untouched, in both directions: never replace rehab, never suggest it.
    if (answer.replaces.isRehab(rehabExercises)) {
        reasons += Rejection(
            REHAB_UNTOUCHED,
            "Rehab work is never substituted. It comes from the rehab program, not the app.",
        )
    }
    if (answer.withExerciseId?.isRehab(rehabExercises) == true ||
        answer.withDescription?.mentionsRehab(rehabExercises) == true
    ) {
        reasons += Rejection(
            REHAB_UNTOUCHED,
            "A replacement may not be a rehab exercise.",
        )
    }

    // A named replacement must exist. A model that invents an exercise id would
    // otherwise produce a cue card for something with no cues and no figure.
    answer.withExerciseId?.let { id ->
        if (repo.exercise(id) == null) {
            reasons += Rejection(
                "unknown_exercise",
                "\"$id\" is not in exercises.json, so there are no cues for it.",
            )
        }
    }

    // no_extra_volume: a swap may change the movement, never the amount.
    if (original != null && answer.sets > original.sets) {
        reasons += Rejection(
            NO_EXTRA_VOLUME,
            "That is ${answer.sets} sets where the protocol has ${original.sets}. " +
                "A substitution may not add volume.",
        )
    }

    // keep_leg_work: swapping a leg exercise for an upper-body one is how a
    // cut quietly becomes pull-ups and push-ups only.
    if (original != null && answer.withExerciseId != null) {
        val was = repo.exercise(answer.replaces)?.pattern
        val now = repo.exercise(answer.withExerciseId)?.pattern
        if (was != null && now != null && was.isLegPattern() && !now.isLegPattern()) {
            reasons += Rejection(
                KEEP_LEG_WORK,
                "That swaps leg work ($was) for $now. Unloaded muscle is what the " +
                    "body burns first in a deficit.",
            )
        }
    }

    reasons += answer.reason.checkWording()

    return if (reasons.isEmpty()) Checked.Accepted(answer) else Checked.Rejected(reasons)
}

/** Check a harder variation. The ladder ran out; the volume still may not grow. */
fun validate(
    repo: ProtocolRepository,
    answer: HarderAnswer,
    rehabExercises: List<String>,
): Checked<HarderAnswer> {
    val reasons = mutableListOf<Rejection>()

    val original = repo.protocol.sessions.values
        .flatMap { it.exercises }
        .firstOrNull { it.exerciseId == answer.forExercise }

    if (original == null) {
        reasons += Rejection(
            "unknown_exercise",
            "\"${answer.forExercise}\" is not an exercise this protocol schedules.",
        )
    }
    if (answer.forExercise.isRehab(rehabExercises)) {
        reasons += Rejection(REHAB_UNTOUCHED, "Rehab work is never made harder by the app.")
    }
    if (original != null && answer.sets > original.sets) {
        reasons += Rejection(
            NO_EXTRA_VOLUME,
            "That is ${answer.sets} sets where the protocol has ${original.sets}.",
        )
    }

    reasons += "${answer.name} ${answer.howTo} ${answer.reason}".checkWording()

    return if (reasons.isEmpty()) Checked.Accepted(answer) else Checked.Rejected(reasons)
}

/**
 * Check a weekly review.
 *
 * The review itself is prose and is checked for the things prose can get wrong.
 * Its one proposed change is a typed object, and that is checked properly.
 */
fun validate(
    repo: ProtocolRepository,
    answer: WeeklyReviewAnswer,
    rehabExercises: List<String>,
    /** True when the fast-loss flag is up: no further cut may be proposed. */
    losingTooFast: Boolean = false,
): Checked<WeeklyReviewAnswer> {
    val reasons = mutableListOf<Rejection>()

    val prose = listOf(
        answer.sleep, answer.sleepingHeartRate, answer.steps,
        answer.weight, answer.strength,
    ).joinToString(" ")
    reasons += prose.checkWording()

    answer.oneChange?.let { change ->
        reasons += change.reason.checkWording()

        if (change.setsChange > 0 || change.sessionsChange > 0) {
            reasons += Rejection(
                NO_EXTRA_VOLUME,
                "The only volume lever during the cut is steps. This adds " +
                    "${change.setsChange} sets and ${change.sessionsChange} sessions.",
            )
        }
        if (change.exerciseId?.isRehab(rehabExercises) == true) {
            reasons += Rejection(REHAB_UNTOUCHED, "A weekly review may not change rehab work.")
        }

        // calorie_floor: the daily target already sits on the floor, so any cut
        // at all breaks it. The message says the arithmetic rather than "no".
        if (change.caloriesKcalChange < 0) {
            val nutrition = repo.protocol.dailyTargets.nutritionDisplayOnly
            val after = nutrition.caloriesKcal + change.caloriesKcalChange
            if (after < nutrition.calorieFloorKcal) {
                reasons += Rejection(
                    CALORIE_FLOOR,
                    "That would put the daily target at $after kcal, under the " +
                        "${nutrition.calorieFloorKcal} kcal floor.",
                )
            }
            if (losingTooFast) {
                reasons += Rejection(
                    "fast_loss_flag",
                    "The 7-day average is already falling faster than the protocol " +
                        "allows. No further cut may be suggested.",
                )
            }
        }
    }

    return if (reasons.isEmpty()) Checked.Accepted(answer) else Checked.Rejected(reasons)
}

/**
 * The guardrails that are about words rather than numbers.
 *
 * Applied to every piece of free text a model returns, because these two are
 * about what the owner reads: a lie-in suggested in a sentence is still a
 * lie-in, and "eat back what you burned" is wrong however politely it is put.
 */
private fun String.checkWording(): List<Rejection> {
    val text = lowercase()
    val out = mutableListOf<Rejection>()

    LIE_IN_PHRASES.firstOrNull { it in text }?.let {
        out += Rejection(
            NO_WEEKEND_SLEEP_IN,
            "It suggests a later wake (\"$it\"). The wake time never moves.",
        )
    }
    EAT_BACK_PHRASES.firstOrNull { it in text }?.let {
        out += Rejection(
            NO_EATING_BACK,
            "It talks about calories burned (\"$it\"). Watch energy estimates run " +
                "30 to 90 percent off and are never food to eat back.",
        )
    }
    return out
}

/** Pain is never answered by a swap. It is answered by stopping. */
fun painRejection(): Rejection = Rejection(
    PAIN_STOP,
    "Pain was reported, so no exercise is suggested at all. See a clinician.",
)

private fun String.isRehab(rehab: List<String>): Boolean =
    rehab.any { it.equals(this, ignoreCase = true) }

private fun String.mentionsRehab(rehab: List<String>): Boolean =
    rehab.any { it.isNotBlank() && lowercase().contains(it.lowercase()) }

/** Patterns the protocol counts as leg work, by the words exercises.json uses. */
private fun String.isLegPattern(): Boolean {
    val text = lowercase()
    return text.contains("squat") || text.contains("hinge") || text.contains("lunge") ||
        text.contains("calf") || text.contains("hip") || text.contains("leg")
}
