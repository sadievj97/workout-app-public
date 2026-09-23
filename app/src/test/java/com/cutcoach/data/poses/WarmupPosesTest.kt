package com.cutcoach.data.poses

import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.ui.session.warmupMovementIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * The warm-up figures: that they exist, that they hold together, and that the
 * player cycles them.
 *
 * The exercise poses already have all of this. These are drawn by the same
 * renderer from the same kind of hand-authored coordinates, and go wrong the
 * same ways, so they get the same checks rather than being trusted because
 * they are only the warm-up.
 */
class WarmupPosesTest {

    private lateinit var library: PoseLibrary
    private lateinit var repo: ProtocolRepository

    private fun asset(name: String) = File("src/main/assets/$name").readText()

    @Before
    fun setUp() {
        library = (PoseRepository.parse(asset("poses.json")) as PoseLoad.Success).library
        repo = (ProtocolRepository.parse(asset("protocol.json"), asset("exercises.json"))
            as ProtocolLoad.Success).repository
    }

    @Test
    fun `every movement a warm-up block names has a figure`() {
        val named = repo.warmup.blocks.flatMap { it.movements }.distinct()
        assertTrue("No warm-up block names any movement", named.isNotEmpty())

        val missing = named.filter { library.warmup.movement(it) == null }
        assertTrue(
            "Warm-up blocks name movements with no pose in poses.json: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `warm-up movements are not filed as exercises`() {
        // The separation is the point: they have no ladder, no sets and no
        // muscles, and PoseTest asserts every entry under `exercises` is a real
        // exercise. One filed there would fail that test for the right reason,
        // and this fails first with a clearer message.
        val strays = library.warmup.movements.keys.filter { it in library.exercises.keys }
        assertTrue("These are in both `warmup` and `exercises`: $strays", strays.isEmpty())
    }

    @Test
    fun `limbs keep their length across every pose of a warm-up movement`() {
        // The same check the exercises get. These poses were generated from
        // segment angles precisely so this cannot fail, which makes it the
        // check that the generator did what it claimed.
        library.warmup.movements.forEach { (id, movement) ->
            val symmetry = Symmetry.parse(movement.symmetry)
            val measured = movement.poses.mapValues { (_, pose) ->
                val armA = pose.armA()
                val armB = pose.armB(symmetry)
                val legA = pose.legA()
                val legB = pose.legB(symmetry)
                mapOf(
                    "torso" to distance(pose.neckAt, pose.hipAt),
                    "head" to distance(pose.neckAt, pose.headAt),
                    "upper arm" to distance(pose.neckAt, armA[0]),
                    "forearm" to distance(armA[0], armA[1]),
                    "other upper arm" to distance(pose.neckAt, armB[0]),
                    "other forearm" to distance(armB[0], armB[1]),
                    "thigh" to distance(pose.hipAt, legA[0]),
                    "shin" to distance(legA[0], legA[1]),
                    "foot" to distance(legA[1], legA[2]),
                    "other thigh" to distance(pose.hipAt, legB[0]),
                    "other shin" to distance(legB[0], legB[1]),
                    "other foot" to distance(legB[1], legB[2]),
                )
            }

            val names = measured.keys.toList()
            if (names.size < 2) return@forEach
            val reference = measured.getValue(names.first())

            reference.keys.forEach { limb ->
                names.drop(1).forEach { poseName ->
                    val actual = measured.getValue(poseName).getValue(limb)
                    val expected = reference.getValue(limb)
                    assertTrue(
                        "$id: $limb is $expected in ${names.first()} but $actual in $poseName",
                        abs(actual - expected) < 0.02f,
                    )
                }
            }
        }
    }

    @Test
    fun `a standing warm-up figure keeps its feet on the floor`() {
        // The pike push-up floated above its own floor for weeks because the
        // check looked at the lowest point and the lowest point was correct.
        // This compares the lowest point across the poses of one movement: if
        // the body rises between keyframes, something is hovering in one of
        // them.
        library.warmup.movements.forEach { (id, movement) ->
            val floor = movement.props.firstOrNull { it.type == "floor" }?.y ?: return@forEach
            val symmetry = Symmetry.parse(movement.symmetry)

            movement.poses.forEach { (name, pose) ->
                val contacts = pose.legA() + pose.legB(symmetry) + pose.armA() + pose.armB(symmetry)
                val lowest = contacts.maxOf { it.y }
                assertTrue(
                    "$id/$name: nothing reaches the floor — lowest point is $lowest, " +
                        "floor is $floor",
                    abs(lowest - floor) < 0.02f,
                )
            }
        }
    }

    @Test
    fun `every warm-up movement names itself`() {
        // The block text names several at once, so the figure has to say which
        // one it is showing. A blank name would leave the viewer guessing.
        library.warmup.movements.forEach { (id, movement) ->
            assertTrue("$id has no name", movement.name.isNotBlank())
        }
    }

    // ---- the player's cycling through them -------------------------------

    @Test
    fun `a block with one movement always shows it`() {
        assertEquals(0, warmupMovementIndex(secondsLeft = 120, blockSeconds = 120, count = 1))
        assertEquals(0, warmupMovementIndex(secondsLeft = 0, blockSeconds = 120, count = 1))
    }

    @Test
    fun `three movements each get a third of the block`() {
        val block = 180
        // Start, a third in, two thirds in, and the last second.
        assertEquals(0, warmupMovementIndex(secondsLeft = 180, blockSeconds = block, count = 3))
        assertEquals(0, warmupMovementIndex(secondsLeft = 121, blockSeconds = block, count = 3))
        assertEquals(1, warmupMovementIndex(secondsLeft = 119, blockSeconds = block, count = 3))
        assertEquals(1, warmupMovementIndex(secondsLeft = 61, blockSeconds = block, count = 3))
        assertEquals(2, warmupMovementIndex(secondsLeft = 59, blockSeconds = block, count = 3))
        assertEquals(2, warmupMovementIndex(secondsLeft = 1, blockSeconds = block, count = 3))
    }

    @Test
    fun `the last instant belongs to the last movement`() {
        // elapsed == blockSeconds gives an index of exactly count, which is one
        // past the end. The first version of this arithmetic divided by the
        // seconds remaining instead of the block length and showed the first
        // movement for the entire block, silently.
        assertEquals(2, warmupMovementIndex(secondsLeft = 0, blockSeconds = 180, count = 3))
        assertEquals(2, warmupMovementIndex(secondsLeft = -5, blockSeconds = 180, count = 3))
    }

    @Test
    fun `a block with no length does not divide by zero`() {
        assertEquals(0, warmupMovementIndex(secondsLeft = 0, blockSeconds = 0, count = 3))
    }

    private fun distance(a: Vec3, b: Vec3): Float = (a - b).length()
}
