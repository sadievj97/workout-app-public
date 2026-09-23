package com.cutcoach.data.session

import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.protocol.SessionDefinition
import com.cutcoach.data.protocol.Target

/** Which side an each-side exercise is working. */
enum class Side { NONE, LEFT, RIGHT;

    val label: String
        get() = when (this) {
            NONE -> ""
            LEFT -> "Left side"
            RIGHT -> "Right side"
        }
}

/**
 * One thing the player asks you to do, in order.
 *
 * A session is flattened into a list of these up front, so the player is just
 * an index walking a list. That keeps the screen simple and makes the whole
 * plan testable without touching the UI.
 */
sealed interface SessionStep {

    /** One block of the 8-minute warm-up. */
    data class Warmup(
        val blockNumber: Int,
        val totalBlocks: Int,
        val what: String,
        val seconds: Int,
        /** The exercise to draw, when this block is a ramp-up of one. */
        val exerciseId: String? = null,
    ) : SessionStep

    /** A set you count out and then type in. */
    data class RepSet(
        val exerciseId: String,
        val setNumber: Int,
        val totalSets: Int,
        val side: Side,
        val target: Target,
    ) : SessionStep

    /** A hold the app counts down for you. */
    data class HoldSet(
        val exerciseId: String,
        val setNumber: Int,
        val totalSets: Int,
        val side: Side,
        /** Counts down from here: the top of the protocol's range. */
        val seconds: Int,
        /** The floor that still counts as a completed hold. */
        val minimumSeconds: Int,
    ) : SessionStep

    data class Rest(val seconds: Int) : SessionStep

    /** The short gap between the first and second side of an exercise. */
    data class SwitchSides(val seconds: Int) : SessionStep

    /** The end of the session. */
    data object Finished : SessionStep
}

/**
 * Builds the ordered plan for a session.
 *
 * Set counts come from [ProtocolRepository.setsFor], so a deload week is
 * already reflected here rather than being handled again in the UI. Rest is
 * inserted after every working set except the last, because resting at the end
 * of a session is just standing around.
 */
fun buildSessionPlan(
    repo: ProtocolRepository,
    session: SessionDefinition,
    programWeek: Int?,
): List<SessionStep> {
    if (!session.isStrength) return emptyList()

    val steps = mutableListOf<SessionStep>()

    // Warm-up first. The protocol gives blocks in minutes.
    val warmup = repo.warmup
    warmup.blocks.forEachIndexed { index, block ->
        steps += SessionStep.Warmup(
            blockNumber = index + 1,
            totalBlocks = warmup.blocks.size,
            what = block.what,
            seconds = block.min * 60,
            // The ramp-up block is a set of the day's own first exercise, so
            // the player can draw it rather than leaving the one warm-up block
            // that has a movement looking like the two that do not.
            exerciseId = if (block.showsFirstExercise) {
                session.exercises.firstOrNull()?.exerciseId
            } else {
                null
            },
        )
    }

    val rest = repo.restSeconds
    val switchSides = repo.switchSidesSeconds

    session.exercises.forEach { item ->
        val totalSets = repo.setsFor(item.sets, programWeek)

        for (setNumber in 1..totalSets) {
            if (item.eachSide) {
                steps += workStep(item.exerciseId, setNumber, totalSets, Side.LEFT, item.target)
                steps += SessionStep.SwitchSides(switchSides)
                steps += workStep(item.exerciseId, setNumber, totalSets, Side.RIGHT, item.target)
            } else {
                steps += workStep(item.exerciseId, setNumber, totalSets, Side.NONE, item.target)
            }
            steps += SessionStep.Rest(rest)
        }
    }

    // No rest at the very end.
    if (steps.lastOrNull() is SessionStep.Rest) steps.removeAt(steps.lastIndex)

    steps += SessionStep.Finished
    return steps
}

private fun workStep(
    exerciseId: String,
    setNumber: Int,
    totalSets: Int,
    side: Side,
    target: Target,
): SessionStep = if (target.isTimed) {
    // Count down from the top of the range, which is what progression aims at.
    // The floor is what still counts as done if you stop early.
    val top = target.max ?: target.min ?: 0
    SessionStep.HoldSet(
        exerciseId = exerciseId,
        setNumber = setNumber,
        totalSets = totalSets,
        side = side,
        seconds = top,
        minimumSeconds = target.min ?: top,
    )
} else {
    SessionStep.RepSet(
        exerciseId = exerciseId,
        setNumber = setNumber,
        totalSets = totalSets,
        side = side,
        target = target,
    )
}
