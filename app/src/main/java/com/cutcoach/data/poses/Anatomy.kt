package com.cutcoach.data.poses

import kotlinx.serialization.Serializable

/**
 * The muscles that make up the body.
 *
 * Rather than shading stripes onto a smooth dummy, each muscle is its own mass
 * riding on a bone, and together they are the figure's silhouette. The ones an
 * exercise works go amber and the rest stay mist, which is how an anatomy plate
 * reads.
 */
@Serializable
data class Anatomy(
    val note: String = "",
    val segments: String = "",
    val fields: String = "",
    val notReallyMuscles: String = "",
    val fidelity: String = "",
    val forms: List<AnatomyForm> = emptyList(),
) {
    /** Every muscle name any form answers to. */
    val namedMuscles: Set<String> by lazy {
        forms.flatMap { it.muscles }.map { it.trim().lowercase() }.toSet()
    }

    /** Muscle names with no form, so a gap fails a test rather than rendering blank. */
    fun unmapped(names: Collection<String>): List<String> =
        names.filterNot { it.trim().lowercase() in namedMuscles }.distinct()
}

/**
 * One muscle mass.
 *
 * [from] and [to] run along the bone it rides. [side] is which way it sits,
 * relative to the body rather than the screen. [lift] is how far off the bone,
 * as a multiple of its own radius. [across] shifts it sideways, and [mirror]
 * repeats it at the opposite shift, which is how the two columns of the rectus
 * abdominis or the two lats come from one entry.
 */
@Serializable
data class AnatomyForm(
    val id: String,
    /** Which of the names in exercises.json this belongs to. May be several. */
    val muscles: List<String> = emptyList(),
    /** "torso", "upper_arm", "forearm", "thigh" or "shin". */
    val segment: String,
    val from: Float,
    val to: Float,
    /** "front", "back", "lateral" or "medial". */
    val side: String = "front",
    val lift: Float = 0.8f,
    val across: Float = 0f,
    val radius: Float,
    val radiusTo: Float = radius,
    val mirror: Boolean = false,
) {
    val sideMode: RegionSide get() = RegionSide.parse(side)

    fun worksAnyOf(muscleNames: Set<String>): Boolean =
        muscles.any { it.trim().lowercase() in muscleNames }
}

// Muscle masses are drawn as ordinary capsules, tagged with whether they are worked.

/**
 * Places every muscle on this pose.
 *
 * Limb forms are drawn on both limbs, because both arms press and both legs
 * drive. Torso forms with [AnatomyForm.mirror] are drawn at each side of the
 * midline.
 */
fun Pose.anatomy(
    symmetry: Symmetry,
    style: BodyStyle,
    facing: Vec3,
    anatomy: Anatomy,
    workedMuscles: Collection<String>,
): List<Capsule> {
    if (anatomy.forms.isEmpty()) return emptyList()

    val worked = workedMuscles.map { it.trim().lowercase() }.toSet()
    val lateralAxis = symmetry.lateralVector()
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

    return anatomy.forms.flatMap { form ->
        val isWorked = form.worksAnyOf(worked)

        when (form.segment) {
            "torso" -> {
                val shifts = if (form.mirror && form.across != 0f) {
                    listOf(form.across, -form.across)
                } else {
                    listOf(form.across)
                }
                shifts.map { shift ->
                    torsoForm(form, shift, isWorked, neck, pelvis, lateralAxis, front, style)
                }
            }

            "upper_arm" -> listOf(
                limbForm(form, isWorked, shoulderA, armA[0], front, lateralAxis * armSide),
                limbForm(form, isWorked, shoulderB, armB[0], front, lateralAxis * -armSide),
            )

            "forearm" -> listOf(
                limbForm(form, isWorked, armA[0], armA[1], front, lateralAxis * armSide),
                limbForm(form, isWorked, armB[0], armB[1], front, lateralAxis * -armSide),
            )

            "thigh" -> listOf(
                limbForm(form, isWorked, hipA, legA[0], front, lateralAxis * legSide),
                limbForm(form, isWorked, hipB, legB[0], front, lateralAxis * -legSide),
            )

            "shin" -> listOf(
                limbForm(form, isWorked, legA[0], legA[1], front, lateralAxis * legSide),
                limbForm(form, isWorked, legB[0], legB[1], front, lateralAxis * -legSide),
            )

            else -> emptyList()
        }
    }
}

private fun limbForm(
    form: AnatomyForm,
    worked: Boolean,
    start: Vec3,
    end: Vec3,
    front: Vec3,
    lateral: Vec3,
): Capsule {
    val axis = (end - start).normalisedOrZero()
    val a = start + (end - start) * form.from
    val b = start + (end - start) * form.to

    val out = outward(form.sideMode, front, lateral, axis)
    val across = lateral * (form.across * form.radius)

    return Capsule(
        group = "muscle",
        from = a + out * (form.radius * form.lift) + across,
        to = b + out * (form.radiusTo * form.lift) + across,
        radiusFrom = form.radius,
        radiusTo = form.radiusTo,
        worked = worked,
    )
}

private fun torsoForm(
    form: AnatomyForm,
    shift: Float,
    worked: Boolean,
    neck: Vec3,
    pelvis: Vec3,
    lateralAxis: Vec3,
    front: Vec3,
    style: BodyStyle,
): Capsule {
    val axis = (pelvis - neck).normalisedOrZero()

    // The torso narrows from shoulders to hips, so a form shifted sideways
    // follows that taper rather than sitting outside the body at the waist.
    fun halfWidthAt(t: Float) =
        style.shoulderHalfWidth + (style.hipHalfWidth - style.shoulderHalfWidth) * t

    val a = neck + (pelvis - neck) * form.from + lateralAxis * (shift * halfWidthAt(form.from))
    val b = neck + (pelvis - neck) * form.to + lateralAxis * (shift * halfWidthAt(form.to))

    val lateral = if (shift >= 0f) lateralAxis else lateralAxis * -1f
    val out = outward(form.sideMode, front, lateral, axis)

    return Capsule(
        group = "muscle",
        from = a + out * (form.radius * form.lift),
        to = b + out * (form.radiusTo * form.lift),
        radiusFrom = form.radius,
        radiusTo = form.radiusTo,
        worked = worked,
    )
}

/** Which way a mass sits, squared to the bone it rides so it hugs the limb. */
private fun outward(side: RegionSide, front: Vec3, lateral: Vec3, axis: Vec3): Vec3 {
    val wanted = when (side) {
        RegionSide.FRONT -> front
        RegionSide.BACK -> front * -1f
        RegionSide.LATERAL -> lateral
        RegionSide.MEDIAL -> lateral * -1f
        RegionSide.ALL -> return Vec3(0f, 0f, 0f)
    }
    val along = wanted dot axis
    val squared = (wanted - axis * along).normalisedOrZero()
    // A bone pointing straight along the wanted direction leaves nothing square
    // to it, so the mass sits on the bone rather than jumping somewhere random.
    return if (squared.length() < 0.2f) Vec3(0f, 0f, 0f) else squared
}


