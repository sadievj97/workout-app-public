package com.cutcoach.rules

/**
 * What the app suggests, and why.
 *
 * Every recommendation carries a [reason] because the protocol asks for one:
 * "All 4 sets hit 15 last Monday, so try feet elevated at 8 reps." A suggestion
 * without its evidence is an instruction, and this app does not give those.
 *
 * Nothing here decides anything. The rules produce suggestions; the owner acts
 * on them or does not.
 */
data class Recommendation(
    val kind: Kind,
    val headline: String,
    /** One line, naming the evidence. Never empty. */
    val reason: String,
    /** Set when the suggestion is about one exercise. */
    val exerciseId: String? = null,
    val tone: Tone = Tone.INFO,
) {
    enum class Kind {
        PROGRESSION,
        DELOAD,
        PLATEAU,
        WEIGHT,
        SLEEP,
        HEART_RATE,
        STEPS,
        READINESS,
        PAIN,
        /** Missing data: a question, never a guess. */
        CHECK_IN,
    }

    enum class Tone {
        INFO,

        /** Worth noticing: a ramp miss, an elevated sleeping heart rate. */
        ATTENTION,

        /** Stop: pain, or losing faster than the protocol allows. */
        STOP,
    }
}

/** One set as it was actually performed. */
data class SetResult(
    val reps: Int? = null,
    val seconds: Int? = null,
) {
    /** What this set achieved against its target, whichever units apply. */
    fun value(timed: Boolean): Int? = if (timed) seconds else reps
}

/**
 * What the owner reported about how they feel.
 *
 * Pain is deliberately not a number. The protocol's guardrail is absolute: sharp
 * or worsening pain, chest pain, dizziness or fainting means no workout is
 * suggested and a clinician is recommended.
 */
data class PainReport(
    val kind: PainKind,
    val note: String = "",
)

enum class PainKind {
    /** Sharp, worsening, chest pain, dizziness or fainting. */
    SERIOUS,

    /** Ordinary training soreness. */
    SORENESS,
}
