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
            val lateralAxis = if (symmetry == Symmetry.MIRROR_X) 0 else 2

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
    fun `the two sides of the body are the same size`() {
        library.exercises.forEach { (id, animation) ->
            val symmetry = animation.symmetryMode
            animation.poses.forEach { (name, pose) ->
                val capsules = pose.body(symmetry, style).filterIsInstance<Capsule>()
                val lengths = capsules.map { (it.to - it.from).length() }.sorted()

                // Every limb capsule appears twice, once per side, plus the
                // single neck. So the sorted lengths pair up apart from one.
                val unpaired = lengths.indices.count { index ->
                    val mine = lengths[index]
                    lengths.count { abs(it - mine) < 0.005f } % 2 == 1
                }
                assertTrue(
                    "$id/$name: limbs do not pair up between the two sides",
                    unpaired <= 1,
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

    private fun lateralOf(point: Vec3, axis: Int): Float =
        if (axis == 0) point.x else point.z
}
