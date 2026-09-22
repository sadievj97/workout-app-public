package com.cutcoach.data.poses

import kotlin.math.sqrt

/**
 * The figure with volume: a set of rounded solids built from the joint centres
 * in a pose.
 *
 * This is what makes the figure read as a person seen in three dimensions
 * rather than a diagram of one. Nothing here is authored: shoulders and hips
 * are placed out to each side along the body's own left-right axis, and every
 * thickness comes from [BodyStyle], so a pose file still only has to say where
 * the joints are.
 */
sealed interface BodyPart {
    /** "arms", "legs" or "torso", for the amber highlight. */
    val group: String

    /** Every point the part occupies, for depth sorting. */
    val points: List<Vec3>
}

/** A limb: a tapered tube with rounded ends. */
data class Capsule(
    val from: Vec3,
    val to: Vec3,
    val radiusFrom: Float,
    val radiusTo: Float,
    override val group: String,
) : BodyPart {
    override val points: List<Vec3> get() = listOf(from, to)
}

/** The torso: a slab between the shoulder line and the hip line. */
data class Slab(
    val corners: List<Vec3>,
    val radius: Float,
    override val group: String,
) : BodyPart {
    override val points: List<Vec3> get() = corners
}

/** The head. */
data class Ball(
    val centre: Vec3,
    val radius: Float,
    override val group: String,
) : BodyPart {
    override val points: List<Vec3> get() = listOf(centre)
}

private const val ARMS = "arms"
private const val LEGS = "legs"
private const val TORSO = "torso"

/**
 * Builds the body for one pose.
 *
 * The parts come back unsorted; the renderer orders them by distance so that
 * nearer limbs cover further ones.
 */
fun Pose.body(symmetry: Symmetry, style: BodyStyle): List<BodyPart> {
    val lateral = if (symmetry == Symmetry.MIRROR_X) Vec3(1f, 0f, 0f) else Vec3(0f, 0f, 1f)

    val neck = neckAt
    val pelvis = hipAt
    val head = headAt

    val armA = armA()
    val armB = armB(symmetry)
    val legA = legA()
    val legB = legB(symmetry)

    // Which side of the body the written-down limb is on, so the shoulder it
    // hangs from is the near one rather than the far one.
    val armSide = sideOf(armA.first() - neck, lateral)
    val legSide = sideOf(legA.first() - pelvis, lateral)

    val shoulderA = neck + lateral * (style.shoulderHalfWidth * armSide)
    val shoulderB = neck - lateral * (style.shoulderHalfWidth * armSide)
    val hipA = pelvis + lateral * (style.hipHalfWidth * legSide)
    val hipB = pelvis - lateral * (style.hipHalfWidth * legSide)

    return buildList {
        // Torso first in the list; the renderer sorts by depth anyway.
        add(
            Slab(
                corners = listOf(shoulderA, shoulderB, hipB, hipA),
                radius = (style.chestRadius + style.waistRadius) / 2f,
                group = TORSO,
            ),
        )

        add(Capsule(neck, head, style.neckRadius, style.neckRadius, TORSO))
        add(Ball(head, style.headRadius, TORSO))

        listOf(shoulderA to armA, shoulderB to armB).forEach { (shoulder, arm) ->
            val elbow = arm[0]
            val wrist = arm[1]
            add(Capsule(shoulder, elbow, style.upperArmRadius, style.forearmRadius, ARMS))
            add(Capsule(elbow, wrist, style.forearmRadius, style.handRadius, ARMS))

            // A short hand carrying on past the wrist, so the arm does not
            // simply stop.
            val handDirection = (wrist - elbow).normalisedOrZero()
            add(
                Capsule(
                    from = wrist,
                    to = wrist + handDirection * style.handLength,
                    radiusFrom = style.handRadius,
                    radiusTo = style.handRadius * 0.8f,
                    group = ARMS,
                ),
            )
        }

        listOf(hipA to legA, hipB to legB).forEach { (hip, leg) ->
            val knee = leg[0]
            val ankle = leg[1]
            val toe = leg[2]
            add(Capsule(hip, knee, style.thighRadius, style.shinRadius, LEGS))
            add(Capsule(knee, ankle, style.shinRadius, style.footRadius, LEGS))
            add(Capsule(ankle, toe, style.footRadius, style.footRadius * 0.85f, LEGS))
        }
    }
}

/** Plus one if the limb sits on the positive side of the lateral axis, minus one otherwise. */
private fun sideOf(offset: Vec3, lateral: Vec3): Float {
    val along = offset dot lateral
    return if (along >= 0f) 1f else -1f
}

internal operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
internal operator fun Vec3.minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
internal operator fun Vec3.times(scale: Float) = Vec3(x * scale, y * scale, z * scale)
internal infix fun Vec3.dot(other: Vec3) = x * other.x + y * other.y + z * other.z

internal fun Vec3.length(): Float = sqrt(x * x + y * y + z * z)

internal fun Vec3.normalisedOrZero(): Vec3 {
    val len = length()
    return if (len < 1e-5f) Vec3(0f, 0f, 0f) else Vec3(x / len, y / len, z / len)
}
