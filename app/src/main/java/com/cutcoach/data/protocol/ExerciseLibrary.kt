package com.cutcoach.data.protocol

import kotlinx.serialization.Serializable

/**
 * The shape of exercises.json: the coaching content behind each exercise id
 * that protocol.json refers to.
 *
 * Rehab exercises are deliberately absent. They come from the owner's rehab
 * program and the app never generates, changes, or substitutes them.
 */
@Serializable
data class ExerciseLibrary(
    val warmupDefault: Warmup,
    val exercises: List<Exercise>,
    val cardio: List<CardioOption>,
) {
    private val byId: Map<String, Exercise> by lazy { exercises.associateBy { it.id } }
    private val cardioById: Map<String, CardioOption> by lazy { cardio.associateBy { it.id } }

    fun exercise(id: String): Exercise? = byId[id]
    fun cardio(id: String): CardioOption? = cardioById[id]
}

@Serializable
data class Warmup(
    val durationMin: Int,
    val blocks: List<WarmupBlock>,
)

@Serializable
data class WarmupBlock(
    val min: Int,
    val what: String,
    /**
     * Set to "first_exercise" on the ramp-up block, so the player can draw the
     * movement it is talking about.
     *
     * Declared in exercises.json rather than inferred from the block's text or
     * its position. Matching on the words "first exercise" would break the
     * moment the wording changed, and assuming it is always the last block is
     * an assumption about a file the owner is told to edit freely.
     */
    val shows: String? = null,
    /**
     * Warm-up movements to draw during this block, by their id in poses.json.
     *
     * A list, because a block names several at once: "hip circles, leg swings,
     * arm circles, cat-cow" is four things in one line, and one animation of
     * all four would be a lie. The player shows them in turn and names the one
     * on screen.
     *
     * Shorter than the block's text on purpose. Cat-cow is named in the words
     * and absent here, because a rigid torso cannot do it without sliding the
     * hands across the floor; poses.json records why.
     */
    val movements: List<String> = emptyList(),
) {
    /** Whether this block is a ramp-up of the day's own first exercise. */
    val showsFirstExercise: Boolean get() = shows == "first_exercise"
}

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    /** The movement pattern, used later to find a like-for-like substitute. */
    val pattern: String,
    val muscles: List<String>,
    val equipment: List<String>,
    /** "reps" or "hold". */
    val type: String,
    val setup: String,
    val cues: List<String>,
    val commonMistakes: List<String>,
    /** Present only where the setup itself can hurt you, such as the inverted row. */
    val safety: String? = null,
    val easier: String,
    /** Rungs in order: reps, then leverage, then load. */
    val ladder: List<String>,
    /** Set where load is impractical, so a harder variation is needed instead. */
    val ladderEndNote: String? = null,
    /** Empty until the owner pastes a demo link. The cue card hides the button while it is. */
    val videoUrl: String = "",
    /**
     * The few seconds of that video worth watching, looped in the cue card.
     * Zero to zero means play from the start with no loop point.
     */
    val videoStartS: Float = 0f,
    val videoEndS: Float = 0f,
) {
    val isHold: Boolean get() = type == "hold"
    val hasVideo: Boolean get() = videoUrl.isNotBlank()
}

@Serializable
data class CardioOption(
    val id: String,
    val name: String,
    /** "duration" or "intervals". */
    val type: String,
    val hrZone: String,
    val how: String,
)
