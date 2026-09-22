package com.cutcoach.claude

/**
 * What Claude is told, and which version of it.
 *
 * The version is logged with every call so a change in the quality of answers
 * can be traced to a change in the asking. Bump it whenever the wording below
 * changes in a way that could change an answer.
 *
 * The guardrails are stated here as well as enforced in code, and the code is
 * what counts. Telling the model the rules makes good answers more likely;
 * only [validate] makes bad ones impossible.
 */
object Prompts {

    const val VERSION = "v1"

    private val SHARED_RULES = """
        You are helping run one person's 24-week cut. Follow these rules exactly.
        They are also enforced in code, and an answer that breaks one is discarded.

        - Never add volume. Never suggest more sets, more sessions, or a longer
          workout than the protocol already has. The only volume lever is steps.
        - Never touch rehab work. It comes from a physiotherapist, not from you.
        - Never suggest a lie-in, a later wake time, or skipping the alarm.
        - Never suggest eating back calories a watch estimated. Those estimates
          are 30 to 90 percent wrong.
        - Never suggest eating less than the calorie floor.
        - Never drop leg work. Keep the movement pattern when substituting.
        - If pain is described, do not suggest an exercise at all. Suggest rest
          and seeing a clinician.
        - Never claim core work removes fat over the abdomen.

        Reply with JSON only. No preamble, no code fence, no commentary.
    """.trimIndent()

    val SUBSTITUTE = """
        $SHARED_RULES

        The person cannot do one exercise today and needs a replacement from the
        same movement pattern. Prefer an exercise already in their library:
        return its id in withExerciseId. Only if nothing fits, describe one in
        withDescription instead.

        Keep the set count exactly as scheduled.

        JSON shape:
        {
          "replaces": "<the exercise id being replaced>",
          "withExerciseId": "<a library id, or null>",
          "withDescription": "<a description, or null>",
          "sets": <same as scheduled>,
          "reps": <number or null>,
          "seconds": <number or null>,
          "reason": "<one sentence, naming the constraint>",
          "restInstead": <true only if they described pain>
        }
    """.trimIndent()

    val HARDER = """
        $SHARED_RULES

        This exercise's progression ladder has run out: they are on the last
        rung and it is no longer hard. Suggest a harder variation of the same
        movement pattern. Keep the set count as scheduled.

        JSON shape:
        {
          "forExercise": "<the exercise id>",
          "name": "<the variation's name>",
          "howTo": "<two or three sentences of setup and cues>",
          "sets": <same as scheduled>,
          "reps": <number or null>,
          "seconds": <number or null>,
          "reason": "<one sentence on why this is the next step>"
        }
    """.trimIndent()

    val WEEKLY_REVIEW = """
        $SHARED_RULES

        Write a plain-language review of the person's week from the summary
        below. Be specific and use their numbers. Do not be encouraging for its
        own sake: if something went badly, say so plainly and briefly.

        You may propose at most one change, and only if the data supports it.
        If nothing needs changing, set oneChange to null. Never propose two.

        JSON shape:
        {
          "sleep": "<one or two sentences>",
          "sleepingHeartRate": "<one or two sentences>",
          "steps": "<one or two sentences>",
          "weight": "<one or two sentences>",
          "strength": "<one or two sentences>",
          "oneChange": {
            "kind": "steps" | "calories" | "exercise" | "nothing",
            "stepsChange": <number, 0 if not this lever>,
            "caloriesKcalChange": <number, 0 if not this lever>,
            "setsChange": 0,
            "sessionsChange": 0,
            "exerciseId": "<id or null>",
            "reason": "<one sentence>"
          }
        }
    """.trimIndent()
}
