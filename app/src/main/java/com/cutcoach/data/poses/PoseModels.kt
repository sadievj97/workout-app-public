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
    val anatomy: Anatomy = Anatomy(),
    val exercises: Map<String, ExerciseAnimation>,
    /**
     * Warm-up movements, kept apart from the exercises.
     *
     * They are drawn by the same renderer and are not exercises: no ladder, no
     * sets, no muscles of their own, and nothing in the rules engine may ever
     * progress one. A test asserts every entry under `exercises` is a real
     * exercise, so a marching figure filed there would fail it — correctly.
     */
    val warmup: WarmupPoses = WarmupPoses(),
) {
    fun animationFor(exerciseId: String): ExerciseAnimation? = exercises[exerciseId]

    /**
     * The animation for an exercise at a given ladder rung.
     *
     * A rung that changes body position is drawn by its own variant, which may
     * borrow another exercise's animation wholesale: a feet-elevated push-up is
     * already drawn by `push_up_feet_up`, so it is referenced rather than
     * copied. Anything else falls back to the base, which is correct for the
     * rungs that only change tempo or add load.
     */
    fun animationFor(exerciseId: String, rung: Int): ExerciseAnimation? {
        val base = exercises[exerciseId] ?: return null
        val borrowed = base.variantFor(rung)?.sameAs
        if (borrowed != null) return exercises[borrowed] ?: base
        return base.atRung(rung)
    }

    /** True when this rung is drawn as itself rather than as the base movement. */
    fun isDrawnAtRung(exerciseId: String, rung: Int): Boolean {
        val animation = exercises[exerciseId] ?: return true
        return rung == animation.baseRung || animation.variantFor(rung) != null
    }

    /** Exercise ids with no animation yet. Used by the Form check screen. */
    fun missingFrom(allExerciseIds: Collection<String>): List<String> =
        allExerciseIds.filterNot { it in exercises }

    /** Muscle names no form answers to, so a gap fails a test rather than rendering blank. */
    fun unmappedMuscles(muscleNames: Collection<String>): List<String> =
        anatomy.unmapped(muscleNames)
}

/** Which way a muscle mass sits, relative to the body rather than the screen. */
enum class RegionSide {
    FRONT, BACK, LATERAL, MEDIAL, ALL;

    companion object {
        fun parse(value: String): RegionSide = when (value.trim().lowercase()) {
            "front" -> FRONT
            "back" -> BACK
            "lateral" -> LATERAL
            "medial" -> MEDIAL
            else -> ALL
        }
    }
}

@Serializable
data class FigureStyle(
    val headRadius: Float,
    val strokeWidth: Float,
    /** How faint the furthest part of the figure goes. 1 would be no depth at all. */
    val farFade: Float = 0.45f,
    val body: BodyStyle = BodyStyle(),
)

/**
 * How thick each part of the body is.
 *
 * The joint centres in a pose are a skeleton. These numbers turn it into a
 * body, so nothing in poses.json has to describe shape as well as position.
 * Shoulders and hips are placed out to each side along the body's own
 * left-right axis, which the exercise's symmetry already names.
 */
@Serializable
data class BodyStyle(
    val headRadius: Float = 0.055f,
    val neckRadius: Float = 0.020f,
    val shoulderHalfWidth: Float = 0.058f,
    val hipHalfWidth: Float = 0.044f,
    val chestRadius: Float = 0.042f,
    val waistRadius: Float = 0.034f,
    val upperArmRadius: Float = 0.026f,
    val forearmRadius: Float = 0.020f,
    val handRadius: Float = 0.019f,
    val handLength: Float = 0.042f,
    val thighRadius: Float = 0.035f,
    val shinRadius: Float = 0.025f,
    val footRadius: Float = 0.019f,
    /**
     * How slim the underlying frame is drawn, as a fraction of its full size.
     * The muscle forms riding on it are what make the silhouette.
     */
    val coreScale: Float = 0.5f,
    /** A dark rim drawn behind each part, so overlapping limbs stay separate. */
    val outline: Float = 0.007f,
    /**
     * The rim between one muscle and the next, which has to be far slimmer.
     * Give muscles the same rim as limbs and the body reads as a string of
     * separate pills rather than as anatomy flowing together.
     */
    val muscleOutline: Float = 0.0015f,
    /** Milder than the line figure's, because shading already carries depth. */
    val farFade: Float = 0.62f,
    /** Which way the light comes from, in screen space. */
    val lightFrom: List<Float> = listOf(-0.45f, -0.80f),
)

/**
 * How the two sides of the body relate.
 *
 * Most exercises are symmetrical, so only one arm and one leg are written down
 * and the other is the same limb reflected. That halves the numbers to author
 * and removes a whole class of typo.
 */
enum class Symmetry {
    /** Reflect across z: for exercises laid out along x, such as a push-up. */
    MIRROR_Z,

    /** Reflect across x: for exercises facing the camera, such as a pull-up. */
    MIRROR_X,

    /**
     * Reflect across y: for exercises lying on your side, where the body's own
     * left-right axis points up and down. Without it a side plank puts the
     * shoulders side by side and reads as lying face down with a propped arm.
     */
    MIRROR_Y,

    /** The sides genuinely differ, as in a split squat. Both are written out. */
    NONE,
    ;

    /** Which way the body's left and right lie. */
    val lateralAxis: Int
        get() = when (this) {
            MIRROR_X -> 0
            MIRROR_Y -> 1
            else -> 2
        }

    /**
     * Which axis to negate to show the same movement on the other side.
     *
     * For a side-lying exercise this is deliberately not the lateral axis:
     * negating up-and-down would stand the figure on its head. Turning it
     * around is what "the other side" looks like.
     */
    val mirrorAxis: Int
        get() = if (this == MIRROR_Y) 0 else lateralAxis

    companion object {
        fun parse(value: String?): Symmetry = when (value?.lowercase()) {
            "mirror_x" -> MIRROR_X
            "mirror_y" -> MIRROR_Y
            "none" -> NONE
            else -> MIRROR_Z
        }
    }
}

@Serializable
data class ExerciseAnimation(
    /** Where the viewer stands. Chosen per exercise to show the movement clearly. */
    val camera: Camera = Camera(),
    /** "mirror_z", "mirror_x" or "none". */
    val symmetry: String = "mirror_z",
    /**
     * Which way the front of the body points, in world space.
     *
     * Without this, "the front of the arm" has no meaning: a push-up faces the
     * floor, a hollow hold faces the ceiling, a pull-up faces the viewer.
     */
    val facing: List<Float> = listOf(0f, 0f, 1f),
    val facingNote: String = "",
    /** Why this camera was chosen, for whoever edits the file later. Not read by code. */
    val cameraNote: String = "",
    /** Which limbs go amber during a work phase: "arms", "legs", "torso". */
    val workingLimbs: List<String> = emptyList(),
    val props: List<Prop> = emptyList(),
    val poses: Map<String, Pose>,
    val sequence: List<Keyframe>,
    /** The pose a "pause" ladder step holds at. */
    val hardestPose: String? = null,
    /**
     * Which rung of the ladder the base poses actually depict.
     *
     * Usually the first, but not always: the hollow hold is drawn with the arms
     * overhead and the legs straight, which is its third rung, and the side
     * plank is drawn straight-legged, which is its second. Assuming zero would
     * have the app claim a rung was drawn when it was not.
     */
    val baseRung: Int = 0,
    /**
     * Rungs of this exercise's ladder that change body position.
     *
     * Rungs that only change tempo or add load are not here: the same drawing
     * is correct for them, and [Tempo] already handles the timing. A rung with
     * no entry is drawn with the base animation.
     */
    val variants: List<ExerciseVariant> = emptyList(),
) {
    val isStaticHold: Boolean get() = sequence.size <= 1

    val symmetryMode: Symmetry get() = Symmetry.parse(symmetry)

    val facingAt: Vec3 get() = Vec3.of(facing)

    fun pose(name: String): Pose? = poses[name]

    fun variantFor(rung: Int): ExerciseVariant? = variants.firstOrNull { it.rung == rung }

    /**
     * This animation as it should be drawn at a given ladder rung.
     *
     * A variant overrides only what it states, so a rung that changes the legs
     * does not have to restate the camera, the symmetry or the sequence.
     */
    fun atRung(rung: Int): ExerciseAnimation {
        val variant = variantFor(rung) ?: return this
        return copy(
            camera = variant.camera ?: camera,
            facing = variant.facing ?: facing,
            symmetry = variant.symmetry ?: symmetry,
            props = variant.props ?: props,
            poses = variant.poses ?: poses,
            sequence = variant.sequence ?: sequence,
            hardestPose = variant.hardestPose ?: hardestPose,
        )
    }
}

/**
 * One rung drawn differently from the base.
 *
 * [sameAs] names another exercise whose animation already is this rung — a
 * feet-elevated push-up is drawn by `push_up_feet_up` — so the geometry is
 * referenced rather than copied and cannot drift from it.
 *
 * Everything else is an override on the base animation. Null means "unchanged",
 * which keeps a variant down to the handful of numbers that actually differ.
 */
@Serializable
data class ExerciseVariant(
    /** The index into this exercise's ladder in exercises.json. */
    val rung: Int,
    /** The rung's own words, so the file can be read against exercises.json. */
    val note: String = "",
    val sameAs: String? = null,
    val camera: Camera? = null,
    /**
     * Which way the body faces, when the rung changes that.
     *
     * A hanging knee raise's easiest rung is a lying reverse crunch: same
     * muscles, same ladder, but you are on your back instead of under a bar.
     * Without these the variant would inherit "facing the viewer" and put every
     * muscle highlight on the wrong side of the body.
     */
    val facing: List<Float>? = null,
    val symmetry: String? = null,
    val props: List<Prop>? = null,
    val poses: Map<String, Pose>? = null,
    val sequence: List<Keyframe>? = null,
    val hardestPose: String? = null,
)

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
 * Where each joint sits in the figure's world, as [x, y, z].
 *
 * Limbs are drawn between joints: neck to hip is the torso, neck to elbow to
 * wrist is the arm, hip to knee to ankle to toe is the leg. Only one arm and
 * one leg are written down unless the exercise's symmetry is "none".
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
    // The other side, written out only where it genuinely differs.
    val elbowOther: List<Float>? = null,
    val wristOther: List<Float>? = null,
    val kneeOther: List<Float>? = null,
    val ankleOther: List<Float>? = null,
    val toeOther: List<Float>? = null,
) {
    val headAt: Vec3 get() = Vec3.of(head)
    val neckAt: Vec3 get() = Vec3.of(neck)
    val hipAt: Vec3 get() = Vec3.of(hip)

    /** The arm that was written down: elbow then wrist. */
    fun armA(): List<Vec3> = listOf(Vec3.of(elbow), Vec3.of(wrist))

    /** The leg that was written down: knee, ankle, toe. */
    fun legA(): List<Vec3> = listOf(Vec3.of(knee), Vec3.of(ankle), Vec3.of(toe))

    /** The other arm, reflected unless it was written out. */
    fun armB(symmetry: Symmetry): List<Vec3> =
        if (elbowOther != null && wristOther != null) {
            listOf(Vec3.of(elbowOther), Vec3.of(wristOther))
        } else {
            armA().map { it.reflect(symmetry) }
        }

    /** The other leg, reflected unless it was written out. */
    fun legB(symmetry: Symmetry): List<Vec3> =
        if (kneeOther != null && ankleOther != null && toeOther != null) {
            listOf(Vec3.of(kneeOther), Vec3.of(ankleOther), Vec3.of(toeOther))
        } else {
            legA().map { it.reflect(symmetry) }
        }

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
        elbowOther = lerpOrNull(elbowOther, other.elbowOther, t),
        wristOther = lerpOrNull(wristOther, other.wristOther, t),
        kneeOther = lerpOrNull(kneeOther, other.kneeOther, t),
        ankleOther = lerpOrNull(ankleOther, other.ankleOther, t),
        toeOther = lerpOrNull(toeOther, other.toeOther, t),
    )

    /**
     * The same pose performed on the other side, for the second half of an
     * each-side exercise. It reflects across the body's own left-right axis, so
     * the movement stays the same movement and only the side changes.
     */
    fun mirrored(symmetry: Symmetry): Pose {
        val axis = symmetry.mirrorAxis
        fun flip(p: List<Float>): List<Float> =
            p.mapIndexed { index, value -> if (index == axis) -value else value }
        fun flipOrNull(p: List<Float>?): List<Float>? = p?.let { flip(it) }

        return Pose(
            head = flip(head),
            neck = flip(neck),
            hip = flip(hip),
            elbow = flip(elbow),
            wrist = flip(wrist),
            knee = flip(knee),
            ankle = flip(ankle),
            toe = flip(toe),
            elbowOther = flipOrNull(elbowOther),
            wristOther = flipOrNull(wristOther),
            kneeOther = flipOrNull(kneeOther),
            ankleOther = flipOrNull(ankleOther),
            toeOther = flipOrNull(toeOther),
        )
    }

    private fun lerp(a: List<Float>, b: List<Float>, t: Float): List<Float> =
        List(maxOf(a.size, b.size)) { i ->
            val from = a.getOrElse(i) { 0f }
            val to = b.getOrElse(i) { 0f }
            from + (to - from) * t
        }

    /** Named apart from [lerp] because both erase to the same JVM signature. */
    private fun lerpOrNull(a: List<Float>?, b: List<Float>?, t: Float): List<Float>? =
        if (a == null || b == null) null else lerp(a, b, t)
}

private fun Vec3.reflect(symmetry: Symmetry): Vec3 = when (symmetry.lateralAxis) {
    0 -> negateX()
    1 -> copy(y = -y)
    else -> negateZ()
}

/**
 * Simple furniture: a floor line, a bar, a chair. Drawn as plain shapes in the
 * same world as the figure, so they turn with the camera.
 */
@Serializable
data class Prop(
    /** "floor", "bar", "chair", "step", "table", "towel". */
    val type: String,
    val y: Float = 0f,
    val fromX: Float = -0.5f,
    val toX: Float = 0.5f,
    val z: Float = 0f,
    /** How deep the prop runs, so a floor reads as a plane rather than a line. */
    val depth: Float = 0f,
    val height: Float = 0f,
) {
    fun mirrored(symmetry: Symmetry): Prop = when (symmetry.mirrorAxis) {
        0 -> copy(fromX = -toX, toX = -fromX)
        else -> copy(z = -z)
    }
}

/**
 * The warm-up movements, drawn by the same machinery as the exercises.
 *
 * [movements] is keyed by the ids the warm-up blocks in exercises.json name.
 * A block naming a movement that is absent draws nothing rather than failing,
 * and a test names the gap.
 */
@Serializable
data class WarmupPoses(
    val note: String = "",
    /** Why cat-cow is named in the block text but has no figure. */
    val catCowNote: String = "",
    val movements: Map<String, WarmupMovement> = emptyMap(),
) {
    fun movement(id: String): WarmupMovement? = movements[id]
}

/**
 * One warm-up movement.
 *
 * Carries its own name because the block text names several at once — "hip
 * circles, leg swings, arm circles, cat-cow" — and the figure has to say which
 * one it is currently showing.
 */
@Serializable
data class WarmupMovement(
    val name: String,
    val note: String = "",
    val camera: Camera = Camera(),
    val facing: List<Float> = listOf(1f, 0f, 0f),
    val symmetry: String = "none",
    val props: List<Prop> = emptyList(),
    val poses: Map<String, Pose>,
    val sequence: List<Keyframe>,
) {
    /** The same shape the figure renderer already knows how to draw. */
    fun asAnimation(): ExerciseAnimation = ExerciseAnimation(
        camera = camera,
        symmetry = symmetry,
        facing = facing,
        props = props,
        poses = poses,
        sequence = sequence,
    )
}
