package com.cutcoach.data.poses

import kotlinx.serialization.Serializable

/**
 * The shape of poses.json.
 *
 * Poses are data, not code, so a figure that looks wrong is fixed by moving
 * numbers in the asset file rather than by editing the drawing.
 */
@Serializable
data class PoseLibrary(
    val figure: FigureStyle,
    val exercises: Map<String, ExerciseAnimation>,
) {
    fun animationFor(exerciseId: String): ExerciseAnimation? = exercises[exerciseId]

    /** Exercise ids with no animation yet. Used by the Form check screen. */
    fun missingFrom(allExerciseIds: Collection<String>): List<String> =
        allExerciseIds.filterNot { it in exercises }
}

@Serializable
data class FigureStyle(
    val headRadius: Float,
    val strokeWidth: Float,
)

@Serializable
data class ExerciseAnimation(
    /** "side" or "front". Side is the default; front is used where it shows the key joint better. */
    val view: String = "side",
    /** Why this view was chosen, for whoever edits the file later. Not used in code. */
    val viewNote: String = "",
    /** Which limbs go amber during a work phase: "arms", "legs", "torso". */
    val workingLimbs: List<String> = emptyList(),
    val props: List<Prop> = emptyList(),
    val poses: Map<String, Pose>,
    val sequence: List<Keyframe>,
    /** The pose a "pause" ladder step holds at. */
    val hardestPose: String? = null,
) {
    val isStaticHold: Boolean get() = sequence.size <= 1

    fun pose(name: String): Pose? = poses[name]
}

/**
 * One moment in the movement. [seconds] is how long it takes to arrive here
 * from the previous keyframe, so the first one is always zero.
 */
@Serializable
data class Keyframe(
    val pose: String,
    val seconds: Float,
    /** "start", "lower", "raise" or "hold". The tempo rules key off this. */
    val role: String = "",
)

/**
 * Where each joint sits, normalised 0 to 1 inside a square box, y downward.
 *
 * Limbs are drawn between joints: neck to hip is the torso, neck to elbow to
 * wrist is the arm, hip to knee to ankle to toe is the leg.
 */
@Serializable
data class Pose(
    val head: List<Float>,
    val neck: List<Float>,
    val hip: List<Float>,
    val elbow: List<Float>,
    val wrist: List<Float>,
    val knee: List<Float>,
    val ankle: List<Float>,
    val toe: List<Float>,
    // The far-side limbs, left out where one arm and one leg tell the whole
    // story. A front view needs them: without a second arm a pull-up reads as
    // a pole with a ball on it.
    val elbowFar: List<Float>? = null,
    val wristFar: List<Float>? = null,
    val kneeFar: List<Float>? = null,
    val ankleFar: List<Float>? = null,
    val toeFar: List<Float>? = null,
) {
    val hasFarArm: Boolean get() = elbowFar != null && wristFar != null
    val hasFarLeg: Boolean get() = kneeFar != null && ankleFar != null && toeFar != null

    /** Straight-line blend toward another pose. Positions, so nothing can wander off-model. */
    fun blendTo(other: Pose, t: Float): Pose = Pose(
        head = lerp(head, other.head, t),
        neck = lerp(neck, other.neck, t),
        hip = lerp(hip, other.hip, t),
        elbow = lerp(elbow, other.elbow, t),
        wrist = lerp(wrist, other.wrist, t),
        knee = lerp(knee, other.knee, t),
        ankle = lerp(ankle, other.ankle, t),
        toe = lerp(toe, other.toe, t),
        elbowFar = lerpOrNull(elbowFar, other.elbowFar, t),
        wristFar = lerpOrNull(wristFar, other.wristFar, t),
        kneeFar = lerpOrNull(kneeFar, other.kneeFar, t),
        ankleFar = lerpOrNull(ankleFar, other.ankleFar, t),
        toeFar = lerpOrNull(toeFar, other.toeFar, t),
    )

    /** Mirrored left to right, for the second side of an each-side exercise. */
    fun mirrored(): Pose = Pose(
        head = flip(head),
        neck = flip(neck),
        hip = flip(hip),
        elbow = flip(elbow),
        wrist = flip(wrist),
        knee = flip(knee),
        ankle = flip(ankle),
        toe = flip(toe),
        elbowFar = flipOrNull(elbowFar),
        wristFar = flipOrNull(wristFar),
        kneeFar = flipOrNull(kneeFar),
        ankleFar = flipOrNull(ankleFar),
        toeFar = flipOrNull(toeFar),
    )

    private fun lerp(a: List<Float>, b: List<Float>, t: Float): List<Float> =
        listOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

    /** Named apart from [lerp] because both erase to the same JVM signature. */
    private fun lerpOrNull(a: List<Float>?, b: List<Float>?, t: Float): List<Float>? =
        if (a == null || b == null) null else lerp(a, b, t)

    private fun flip(p: List<Float>): List<Float> = listOf(1f - p[0], p[1])

    private fun flipOrNull(p: List<Float>?): List<Float>? = p?.let { flip(it) }
}

/** Simple furniture: a floor line, a bar, a chair. Drawn as plain shapes. */
@Serializable
data class Prop(
    /** "floor", "bar", "chair", "step", "table", "towel". */
    val type: String,
    val y: Float = 0f,
    val fromX: Float = 0f,
    val toX: Float = 1f,
    val height: Float = 0f,
) {
    fun mirrored(): Prop = copy(fromX = 1f - toX, toX = 1f - fromX)
}
