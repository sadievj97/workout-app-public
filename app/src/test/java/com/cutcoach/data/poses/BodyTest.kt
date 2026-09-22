package com.cutcoach.data.poses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * The solid figure: the body built from the joint centres in a pose.
 *
 * Nothing here is authored, which is the point. If these hold, then adding an
 * exercise means writing joint positions and nothing about shape.
 */
class BodyTest {

    private lateinit var library: PoseLibrary

    @Before
    fun setUp() {
        val text = File("src/main/assets/poses.json").readText()
        library = (PoseRepository.parse(text) as PoseLoad.Success).library
    }

    private val style get() = library.figure.body

    @Test
    fun `every pose builds a whole body`() {
        library.exercises.forEach { (id, animation) ->
            animation.poses.forEach { (name, pose) ->
                val parts = pose.body(animation.symmetryMode, style)

                assertEquals("$id/$name: one torso", 1, parts.count { it is Slab })
                assertEquals("$id/$name: one head", 1, parts.count { it is Ball })
                // Neck, plus three capsules per arm and three per leg.
                assertEquals("$id/$name: limb count", 13, parts.count { it is Capsule })
            }
        }
    }

    @Test
    fun `the written-down arm hangs from the shoulder on its own side`() {
        library.exercises.forEach { (id, animation) ->
            val symmetry = animation.symmetryMode
            val lateralAxis = symmetry.lateralAxis

            animation.poses.forEach { (name, pose) ->
                val elbow = pose.armA().first()
                val neck = pose.neckAt
                val elbowSide = lateralOf(elbow, lateralAxis) - lateralOf(neck, lateralAxis)

                // The upper arm is the capsule whose far end is that elbow.
                val upperArm = pose.body(symmetry, style)
                    .filterIsInstance<Capsule>()
                    .first { it.to == elbow }

                val shoulderSide = lateralOf(upperArm.from, lateralAxis) -
                    lateralOf(neck, lateralAxis)

                // If the arm reaches out to one side, its shoulder must be on
                // that side too, or the limb crosses the body.
                if (abs(elbowSide) > 0.005f) {
                    assertTrue(
                        "$id/$name: the arm attaches to the wrong shoulder",
                        elbowSide * shoulderSide > 0f,
                    )
                }
            }
        }
    }

    @Test
    fun `a symmetrical exercise has identical limbs on both sides`() {
        // Compared segment by segment rather than by sorting all the lengths
        // and looking for pairs: that earlier version matched a foot to the
        // neck because they happened to be within a few thousandths, and
        // failed a figure that was perfectly fine.
        library.exercises.forEach { (id, animation) ->
            val symmetry = animation.symmetryMode
            if (symmetry == Symmetry.NONE) return@forEach

            animation.poses.forEach { (name, pose) ->
                val armA = pose.armA()
                val armB = pose.armB(symmetry)
                val legA = pose.legA()
                val legB = pose.legB(symmetry)

                assertEquals(
                    "$id/$name: forearms differ",
                    (armA[1] - armA[0]).length(),
                    (armB[1] - armB[0]).length(),
                    0.005f,
                )
                assertEquals(
                    "$id/$name: shins differ",
                    (legA[1] - legA[0]).length(),
                    (legB[1] - legB[0]).length(),
                    0.005f,
                )
                assertEquals(
                    "$id/$name: feet differ",
                    (legA[2] - legA[1]).length(),
                    (legB[2] - legB[1]).length(),
                    0.005f,
                )
            }
        }
    }

    @Test
    fun `the whole solid body stays inside the frame`() {
        // The body is fatter than the skeleton, so a figure whose joints fit
        // can still have a shoulder hanging out of the picture.
        library.exercises.forEach { (id, animation) ->
            animation.poses.forEach { (name, pose) ->
                pose.body(animation.symmetryMode, style).forEach { part ->
                    val radius = when (part) {
                        is Capsule -> maxOf(part.radiusFrom, part.radiusTo)
                        is Slab -> part.radius
                        is Ball -> part.radius
                    } + style.outline

                    part.points.forEach { point ->
                        val flat = point.project(animation.camera)
                        assertTrue(
                            "$id/$name: a ${part.group} part reaches " +
                                "(${flat.x}, ${flat.y}) with radius $radius, outside the frame",
                            flat.x - radius > -0.03f && flat.x + radius < 1.03f &&
                                flat.y - radius > -0.03f && flat.y + radius < 1.03f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a body survives being turned right around`() {
        library.exercises.forEach { (id, animation) ->
            (0 until 360 step 45).forEach { turn ->
                val camera = animation.camera.turnedBy(turn.toFloat())
                animation.poses.forEach { (name, pose) ->
                    pose.body(animation.symmetryMode, style).forEach { part ->
                        part.points.forEach { point ->
                            val flat = point.project(camera)
                            assertTrue(
                                "$id/$name: a ${part.group} part leaves the frame at $turn degrees",
                                flat.x in -0.16f..1.16f && flat.y in -0.16f..1.16f,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `a head is a little under half the torso's length`() {
        // The first attempt had a torso as wide as it was long, which read as a
        // slab and swallowed the head. These are the proportions that fixed it.
        val torsoLength = 0.26f
        val headDiameter = style.headRadius * 2f
        val shoulderWidth = style.shoulderHalfWidth * 2f + style.chestRadius * 2f

        assertTrue(
            "The head is out of proportion: ${headDiameter / torsoLength}",
            headDiameter / torsoLength in 0.3f..0.5f,
        )
        assertTrue(
            "The torso is too wide: ${shoulderWidth / torsoLength}",
            shoulderWidth / torsoLength in 0.35f..0.65f,
        )
    }

    @Test
    fun `limbs are slimmer than they are long`() {
        val checks = listOf(
            "upper arm" to (style.upperArmRadius * 2f to 0.105f),
            "forearm" to (style.forearmRadius * 2f to 0.105f),
            "thigh" to (style.thighRadius * 2f to 0.135f),
            "shin" to (style.shinRadius * 2f to 0.135f),
        )
        checks.forEach { (name, sizes) ->
            val (thickness, length) = sizes
            assertTrue(
                "$name is $thickness thick over $length long, which will look stubby",
                thickness / length < 0.45f,
            )
        }
    }

    // ---- The anatomy -----------------------------------------------------

    private val anatomy get() = library.anatomy

    @Test
    fun `every muscle the exercise library names has a form on the body`() {
        val named = com.cutcoach.data.protocol.ProtocolRepository.parse(
            File("src/main/assets/protocol.json").readText(),
            File("src/main/assets/exercises.json").readText(),
        ).let { it as com.cutcoach.data.protocol.ProtocolLoad.Success }
            .repository.library.exercises
            .flatMap { it.muscles }
            .distinct()

        val unmapped = library.unmappedMuscles(named)
        assertTrue(
            "Named in exercises.json but with nothing to shade: $unmapped",
            unmapped.isEmpty(),
        )
    }

    @Test
    fun `a form only ever rides a segment the body actually has`() {
        val known = setOf("torso", "upper_arm", "forearm", "thigh", "shin")
        anatomy.forms.forEach { form ->
            assertTrue(
                "\"${form.id}\" rides \"${form.segment}\", which does not exist",
                form.segment in known,
            )
            assertTrue(
                "\"${form.id}\" has a backwards or empty stretch",
                form.to > form.from,
            )
            assertTrue("\"${form.id}\" has no size", form.radius > 0f && form.radiusTo > 0f)
        }
    }

    @Test
    fun `form ids are unique`() {
        val duplicates = anatomy.forms.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate form ids: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `a limb muscle is placed on both limbs`() {
        // Both arms press in a push-up, so the triceps has to appear twice.
        val animation = library.animationFor("push_up")!!
        val pose = animation.pose("top")!!
        val placed = pose.anatomy(
            animation.symmetryMode, style, animation.facingAt, anatomy, listOf("Triceps"),
        )
        assertEquals(2, placed.count { it.worked })
    }

    @Test
    fun `only the worked muscles are marked`() {
        val animation = library.animationFor("push_up")!!
        val pose = animation.pose("top")!!
        val placed = pose.anatomy(
            animation.symmetryMode, style, animation.facingAt, anatomy, listOf("Triceps"),
        )
        assertTrue("Nothing was marked", placed.any { it.worked })
        assertTrue("Everything was marked", placed.any { !it.worked })
    }

    @Test
    fun `front and back muscles end up on opposite sides of the same bone`() {
        val animation = library.animationFor("pull_up")!!
        val pose = animation.pose("hang")!!
        val placed = pose.anatomy(
            animation.symmetryMode, style, animation.facingAt, anatomy, emptyList(),
        )
        val biceps = placed.first { it.id() == "biceps_brachii" }
        val triceps = placed.first { it.id() == "triceps_brachii" }

        // The comparison has to be made sideways from the bone, not from a
        // joint: both muscles start near the shoulder, so measuring from the
        // elbow is dominated by the length of the arm and says nothing about
        // which side they sit on.
        val elbow = pose.armA().first()
        val shoulder = pose.body(animation.symmetryMode, style)
            .filterIsInstance<Capsule>()
            .first { it.to == elbow }
            .from

        fun offsetOf(muscle: Capsule, along: Float): Vec3 =
            muscle.from - (shoulder + (elbow - shoulder) * along)

        val toBiceps = offsetOf(biceps, 0.28f)
        val toTriceps = offsetOf(triceps, 0.24f)

        assertTrue(
            "Biceps and triceps ended up on the same side of the arm",
            (toBiceps dot toTriceps) < 0f,
        )
    }

    @Test
    fun `the whole anatomy stays inside the frame`() {
        library.exercises.forEach { (id, animation) ->
            animation.poses.forEach { (name, pose) ->
                pose.anatomy(animation.symmetryMode, style, animation.facingAt, anatomy, emptyList())
                    .forEach { muscle ->
                        val radius = maxOf(muscle.radiusFrom, muscle.radiusTo) + style.outline
                        muscle.points.forEach { point ->
                            val flat = point.project(animation.camera)
                            assertTrue(
                                "$id/$name: ${muscle.id()} reaches (${flat.x}, ${flat.y})",
                                flat.x - radius > -0.05f && flat.x + radius < 1.05f &&
                                    flat.y - radius > -0.05f && flat.y + radius < 1.05f,
                            )
                        }
                    }
            }
        }
    }

    @Test
    fun `a muscle sits on its bone rather than floating off it`() {
        // Every mass must stay within a sensible reach of the segment it rides,
        // which catches a lift or across that has been typed an order out.
        library.exercises.forEach { (id, animation) ->
            animation.poses.forEach { (name, pose) ->
                val frame = pose.body(animation.symmetryMode, style)
                    .filterIsInstance<Capsule>()
                    .flatMap { it.points }
                pose.anatomy(animation.symmetryMode, style, animation.facingAt, anatomy, emptyList())
                    .forEach { muscle ->
                        val nearest = frame.minOf { (it - muscle.from).length() }
                        assertTrue(
                            "$id/$name: ${muscle.id()} floats ${nearest} away from any bone",
                            nearest < 0.14f,
                        )
                    }
            }
        }
    }

    private fun Capsule.id(): String = anatomyIdOf(this)

    private fun anatomyIdOf(capsule: Capsule): String =
        anatomy.forms.firstOrNull { form ->
            kotlin.math.abs(capsule.radiusFrom - form.radius) < 1e-5f &&
                kotlin.math.abs(capsule.radiusTo - form.radiusTo) < 1e-5f
        }?.id ?: "unknown"

    private fun lateralOf(point: Vec3, axis: Int): Float = when (axis) {
        0 -> point.x
        1 -> point.y
        else -> point.z
    }
}
