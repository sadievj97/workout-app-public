package com.cutcoach.claude

import android.content.Context
import com.cutcoach.data.db.TrainingRepository
import com.cutcoach.data.db.sevenDayAverageKg
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.settings.SettingsRepository
import com.cutcoach.health.DailyHealth
import com.cutcoach.health.readTodayAndLastNight
import com.cutcoach.rules.PainKind
import com.cutcoach.rules.RuleInput
import com.cutcoach.rules.isLosingTooFast
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * The three things Claude is asked for, end to end.
 *
 * Each one does the same five steps: build a summary, ask, parse, validate,
 * log. The validation is not optional and not last-minute — the answer never
 * reaches a screen without passing through it, and a rejection is returned as
 * a rejection rather than swallowed.
 */

/** What a feature gives back: something to show, or something that went wrong. */
sealed interface Outcome<out T> {
    data class Ready<T>(val answer: T) : Outcome<T>
    data class Refused(val reasons: List<Rejection>) : Outcome<Nothing>
    data class Broke(val message: String) : Outcome<Nothing>
}

private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * A swap for today.
 *
 * Pain short-circuits before any request is made. Sending "my knee hurts" to a
 * model that has been told not to suggest exercises would still be sending it,
 * and the answer is the same either way: stop, and see a clinician.
 */
suspend fun substitute(
    context: Context,
    repo: ProtocolRepository,
    exerciseId: String,
    because: String,
): Outcome<SubstituteAnswer> {
    if (soundsLikePain(because)) {
        return Outcome.Refused(listOf(com.cutcoach.claude.painRejection()))
    }

    val settings = SettingsRepository.get(context)
    val rehab = settings.rehabExercises.first()
    if (rehab.any { it.equals(exerciseId, ignoreCase = true) }) {
        return Outcome.Refused(
            listOf(
                Rejection(
                    "rehab_untouched",
                    "Rehab work is never substituted. It comes from your rehab program.",
                ),
            ),
        )
    }

    val rung = TrainingRepository.get(context).ladderStep(exerciseId)
    val answer = askFor<SubstituteAnswer>(
        context = context,
        feature = "substitute",
        model = ClaudeClient.HAIKU,
        system = Prompts.SUBSTITUTE,
        prompt = Summaries.forSubstitute(repo, exerciseId, because, rung),
    ) ?: return Outcome.Broke("Claude's answer did not match the expected shape.")

    return when (val checked = validate(repo, answer.first, rehab)) {
        is Checked.Accepted -> Outcome.Ready(checked.answer)
        is Checked.Rejected -> Outcome.Refused(checked.reasons)
    }
}

/** A harder version, for a ladder that has run out. */
suspend fun harderVariation(
    context: Context,
    repo: ProtocolRepository,
    exerciseId: String,
): Outcome<HarderAnswer> {
    val rehab = SettingsRepository.get(context).rehabExercises.first()

    val answer = askFor<HarderAnswer>(
        context = context,
        feature = "harder",
        model = ClaudeClient.HAIKU,
        system = Prompts.HARDER,
        prompt = Summaries.forHarder(repo, exerciseId),
    ) ?: return Outcome.Broke("Claude's answer did not match the expected shape.")

    return when (val checked = validate(repo, answer.first, rehab)) {
        is Checked.Accepted -> Outcome.Ready(checked.answer)
        is Checked.Rejected -> Outcome.Refused(checked.reasons)
    }
}

/** The week, in plain language, with at most one change. */
suspend fun weeklyReview(
    context: Context,
    repo: ProtocolRepository,
    today: LocalDate = LocalDate.now(),
): Outcome<WeeklyReviewAnswer> {
    val training = TrainingRepository.get(context)
    val settings = SettingsRepository.get(context)
    val rehab = settings.rehabExercises.first()

    val body = training.recentBody(today, days = 40).first()

    // Fourteen days of aggregates, as the brief specifies.
    val days = (0 until REVIEW_DAYS).mapNotNull { back ->
        val date = today.minusDays(back.toLong())
        runCatching { readTodayAndLastNight(context, repo, date).first }.getOrNull()
    }.filterNotNull()

    val strength = repo.protocol.sessions.values
        .flatMap { it.exercises }
        .map { it.exerciseId }
        .distinct()
        .mapNotNull { id ->
            val sets = training.lastSessionSets(id)
            if (sets.isEmpty()) return@mapNotNull null
            id to sets.joinToString(", ") { set ->
                set.reps?.toString() ?: set.seconds?.let { "${it}s" } ?: "?"
            }
        }
        .toMap()

    val answer = askFor<WeeklyReviewAnswer>(
        context = context,
        feature = "weekly_review",
        model = ClaudeClient.SONNET,
        system = Prompts.WEEKLY_REVIEW,
        prompt = Summaries.forWeeklyReview(repo, today, days, body, strength),
        maxTokens = 1536,
    ) ?: return Outcome.Broke("Claude's answer did not match the expected shape.")

    // The fast-loss flag is checked here rather than trusted to the model: a
    // further cut while already losing too fast is the most harmful thing it
    // could propose.
    val tooFast = isLosingTooFast(
        repo,
        RuleInput(
            today = today,
            programWeek = repo.programWeek(today),
            weeklyWeightKg = (0..3).map { w ->
                body.sevenDayAverageKg(today.minusWeeks(w.toLong()))
            },
        ),
    )

    return when (val checked = validate(repo, answer.first, rehab, losingTooFast = tooFast)) {
        is Checked.Accepted -> Outcome.Ready(checked.answer)
        is Checked.Rejected -> Outcome.Refused(checked.reasons)
    }
}

/** Two weeks, which is what the brief asks a review to look at. */
private const val REVIEW_DAYS = 14

/**
 * Ask, parse, and log the call whatever happened.
 *
 * The log is written before the answer is validated, so a rejected answer is
 * still recorded as having cost tokens. An audit that only counted the useful
 * calls would understate what the layer costs.
 */
private suspend inline fun <reified T> askFor(
    context: Context,
    feature: String,
    model: String,
    system: String,
    prompt: String,
    maxTokens: Int = 1024,
): Pair<T, ClaudeClient.Answer>? {
    val result = ClaudeClient.ask(model, system, prompt, maxTokens)
    if (result !is ClaudeClient.Result.Ok) return null

    val obj = extractJsonObject(result.answer.text) ?: run {
        CallLog.record(context, feature, model, result.answer, rejected = true)
        return null
    }
    val parsed = runCatching {
        lenient.decodeFromString<T>(obj.toString())
    }.getOrNull()

    CallLog.record(context, feature, model, result.answer, rejected = parsed == null)
    return parsed?.let { it to result.answer }
}

/**
 * Whether a reason given for a substitution is really a report of pain.
 *
 * Matched on words rather than asked of a model, because the answer decides
 * whether a model is consulted at all. "Sore" is deliberately absent: ordinary
 * training soreness is not the guardrail's subject.
 */
fun soundsLikePain(text: String): Boolean {
    val t = text.lowercase()
    return PAIN_WORDS.any { it in t }
}

private val PAIN_WORDS = listOf(
    "pain", "hurts", "hurting", "sharp", "chest", "dizzy", "dizziness",
    "faint", "fainted", "pinch", "stabbing", "numb", "tingling",
)

/** Pain as the protocol treats it, for callers that need the enum. */
fun painKindFor(text: String): PainKind? =
    if (soundsLikePain(text)) PainKind.SERIOUS else null
