package com.cutcoach.data.poses

/**
 * A patch of the body to shade amber, worked out from a muscle region.
 *
 * [outward] is the direction the patch faces, so the renderer can tell whether
 * you are looking at it. When a patch faces away, the whole segment is tinted
 * faintly instead, because a highlight that silently vanishes is worse than one
 * that is approximate: on a push-up the chest points at the floor.
 */
sealed interface MusclePatch {
    /** Null means the patch wraps the segment all the way round. */
    val outward: Vec3?

    val points: List<Vec3>
}

/** A stripe along part of a limb. */
data class PatchCapsule(
    val from: Vec3,
    val to: Vec3,
    val radiusFrom: Float,
    val radiusTo: Float,
    override val outward: Vec3?,
) : MusclePatch {
    override val points: List<Vec3> get() = listOf(from, to)
}

/** A band across part of the torso. */
data class PatchQuad(
    val corners: List<Vec3>,
    val radius: Float,
    override val outward: Vec3?,
) : MusclePatch {
    override val points: List<Vec3> get() = corners
}

/**
 * Turns the muscles an exercise works into patches on this pose's body.
 *
 * Every limb region produces two patches, one per side of the body, because
 * both arms and both legs are doing the work.
 */
fun Pose.musclePatches(
    symmetry: Symmetry,
    style: BodyStyle,
    facing: Vec3,
    regions: List<MuscleRegion>,
): List<MusclePatch> {
    if (regions.isEmpty()) return emptyList()

    val lateralAxis = if (symmetry == Symmetry.MIRROR_X) Vec3(1f, 0f, 0f) else Vec3(0f, 0f, 1f)
    val front = facing.normalisedOrZero()

    val neck = neckAt
    val pelvis = hipAt
    val armA = armA()
    val armB = armB(symmetry)
    val legA = legA()
    val legB = legB(symmetry)

    val armSide = if ((armA.first() - neck) dot lateralAxis >= 0f) 1f else -1f
    val legSide = if ((legA.first() - pelvis) dot lateralAxis >= 0f) 1f else -1f

    val shoulderA = neck + lateralAxis * (style.shoulderHalfWidth * armSide)
    val shoulderB = neck - lateralAxis * (style.shoulderHalfWidth * armSide)
    val hipA = pelvis + lateralAxis * (style.hipHalfWidth * legSide)
    val hipB = pelvis - lateralAxis * (style.hipHalfWidth * legSide)

    return regions.flatMap { region ->
        when (region.segment) {
            "torso" -> torsoPatches(region, shoulderA, shoulderB, hipA, hipB, front, style)

            "upper_arm" -> listOf(
                limbPatch(region, shoulderA, armA[0], style.upperArmRadius, style.forearmRadius, front, lateralAxis * armSide),
                limbPatch(region, shoulderB, armB[0], style.upperArmRadius, style.forearmRadius, front, lateralAxis * -armSide),
            )

            "forearm" -> listOf(
                limbPatch(region, armA[0], armA[1], style.forearmRadius, style.handRadius, front, lateralAxis * armSide),
                limbPatch(region, armB[0], armB[1], style.forearmRadius, style.handRadius, front, lateralAxis * -armSide),
            )

            "hand" -> listOf(armA, armB).mapIndexed { index, arm ->
                val side = if (index == 0) armSide else -armSide
                val direction = (arm[1] - arm[0]).normalisedOrZero()
                limbPatch(
                    region = region,
                    start = arm[1],
                    end = arm[1] + direction * style.handLength,
                    radiusStart = style.handRadius,
                    radiusEnd = style.handRadius * 0.8f,
                    front = front,
                    lateral = lateralAxis * side,
                )
            }

            "thigh" -> listOf(
                limbPatch(region, hipA, legA[0], style.thighRadius, style.shinRadius, front, lateralAxis * legSide),
                limbPatch(region, hipB, legB[0], style.thighRadius, style.shinRadius, front, lateralAxis * -legSide),
            )

            "shin" -> listOf(
                limbPatch(region, legA[0], legA[1], style.shinRadius, style.footRadius, front, lateralAxis * legSide),
                limbPatch(region, legB[0], legB[1], style.shinRadius, style.footRadius, front, lateralAxis * -legSide),
            )

            "foot" -> listOf(
                limbPatch(region, legA[1], legA[2], style.footRadius, style.footRadius * 0.85f, front, lateralAxis * legSide),
                limbPatch(region, legB[1], legB[2], style.footRadius, style.footRadius * 0.85f, front, lateralAxis * -legSide),
            )

            else -> emptyList()
        }
    }
}

/**
 * A stripe sitting on one side of a limb.
 *
 * It is offset toward that side and made narrower than the limb, so it reads as
 * a marking on the arm rather than a second arm.
 */
private fun limbPatch(
    region: MuscleRegion,
    start: Vec3,
    end: Vec3,
    radiusStart: Float,
    radiusEnd: Float,
    front: Vec3,
    lateral: Vec3,
): MusclePatch {
    val from = region.from.coerceIn(0f, 1f)
    val to = region.to.coerceIn(0f, 1f)

    val axis = (end - start).normalisedOrZero()
    val a = start + (end - start) * from
    val b = start + (end - start) * to
    val rA = radiusStart + (radiusEnd - radiusStart) * from
    val rB = radiusStart + (radiusEnd - radiusStart) * to

    val outward = when (region.sideMode) {
        RegionSide.FRONT -> perpendicularTo(front, axis)
        RegionSide.BACK -> perpendicularTo(front * -1f, axis)
        RegionSide.LATERAL -> perpendicularTo(lateral, axis)
        RegionSide.MEDIAL -> perpendicularTo(lateral * -1f, axis)
        RegionSide.ALL -> null
    }

    return if (outward == null) {
        // Wraps the whole segment, so it can just overlay that band.
        PatchCapsule(a, b, rA, rB, null)
    } else {
        PatchCapsule(
            from = a + outward * (rA * OFFSET),
            to = b + outward * (rB * OFFSET),
            radiusFrom = rA * WIDTH,
            radiusTo = rB * WIDTH,
            outward = outward,
        )
    }
}

/**
 * A band across the torso.
 *
 * Front and back are the two faces of the torso slab. Lateral becomes two
 * narrow strips down its edges, which is where the obliques and glute medius
 * actually sit.
 */
private fun torsoPatches(
    region: MuscleRegion,
    shoulderA: Vec3,
    shoulderB: Vec3,
    hipA: Vec3,
    hipB: Vec3,
    front: Vec3,
    style: BodyStyle,
): List<MusclePatch> {
    val from = region.from.coerceIn(0f, 1f)
    val to = region.to.coerceIn(0f, 1f)

    fun along(top: Vec3, bottom: Vec3, t: Float) = top + (bottom - top) * t

    val topA = along(shoulderA, hipA, from)
    val topB = along(shoulderB, hipB, from)
    val bottomA = along(shoulderA, hipA, to)
    val bottomB = along(shoulderB, hipB, to)

    val slabRadius = (style.chestRadius + style.waistRadius) / 2f

    return when (region.sideMode) {
        RegionSide.LATERAL -> listOf(
            PatchCapsule(topA, bottomA, slabRadius * 0.5f, slabRadius * 0.5f, null),
            PatchCapsule(topB, bottomB, slabRadius * 0.5f, slabRadius * 0.5f, null),
        )

        RegionSide.MEDIAL, RegionSide.ALL -> listOf(
            PatchQuad(listOf(topA, topB, bottomB, bottomA), slabRadius * 0.6f, null),
        )

        RegionSide.FRONT -> listOf(
            PatchQuad(listOf(topA, topB, bottomB, bottomA), slabRadius * 0.6f, front),
        )

        RegionSide.BACK -> listOf(
            PatchQuad(listOf(topA, topB, bottomB, bottomA), slabRadius * 0.6f, front * -1f),
        )
    }
}

/** The part of [direction] that is square to [axis], so a stripe sits across a limb. */
private fun perpendicularTo(direction: Vec3, axis: Vec3): Vec3 {
    val along = direction dot axis
    val squared = direction - axis * along
    val result = squared.normalisedOrZero()
    // A limb pointing straight along the facing direction has no usable side,
    // so the patch wraps instead of picking an arbitrary one.
    return if (result.length() < 0.2f) direction.normalisedOrZero() else result
}

/** How far out from the limb's centre the stripe sits, as a fraction of its radius. */
private const val OFFSET = 0.42f

/** How wide the stripe is, as a fraction of the limb's radius. */
private const val WIDTH = 0.58f
