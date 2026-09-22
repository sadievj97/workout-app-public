package com.cutcoach.data.poses

import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The animation engine, checked without drawing anything.
 *
 * Keeping the poses and the timeline separate from the rendering is what makes
 * this possible: a 3-second lowering really lasting 3 seconds, and a figure
 * staying inside its frame at any camera angle, are both arithmetic rather than
 * things you have to sit and watch.
 */
class PoseTest {

    private lateinit var library: PoseLibrary
    private lateinit var repo: ProtocolRepository

    private fun asset(name: String) = File("src/main/assets/$name").readText()

    /** The first three, built for approval before the other twelve. */
    private val pilotExercises = listOf("push_up", "pull_up", "hollow_hold")

    @Before
    fun setUp() {
        val poses = PoseRepository.parse(asset("poses.json"))
        if (poses is PoseLoad.Failure) throw AssertionError(poses.message, poses.cause)
        library = (poses as PoseLoad.Success).library

        val protocol = ProtocolRepository.parse(asset("protocol.json"), asset("exercises.json"))
        repo = (protocol as ProtocolLoad.Success).repository
    }

    // ---- The data itself --------------------------------------------------

    @Test
    fun `the three pilot exercises have animations`() {
        pilotExercises.forEach {
            assertTrue("No animation for $it", library.animationFor(it) != null)
        }
    }

    /** The coverage check the brief asks for before Phase 2. */
    @Test
    fun `every exercise in the protocol has a figure`() {
        val scheduled = repo.protocol.sessions.values
            .flatMap { it.exercises }
            .map { it.exerciseId }
            .distinct()

        val missing = library.missingFrom(scheduled)
        assertTrue("Scheduled exercises with no figure: $missing", missing.isEmpty())

        // And the whole library, not only what is scheduled today.
        val allMissing = library.missingFrom(repo.library.exercises.map { it.id })
        assertTrue("Library exercises with no figure: $allMissing", allMissing.isEmpty())
    }

    @Test
    fun `every animation that exists belongs to a real exercise`() {
        val known = repo.library.exercises.map { it.id }.toSet()
        val strays = library.exercises.keys.filterNot { it in known }
        assertTrue("Animations for exercises that do not exist: $strays", strays.isEmpty())
    }

    @Test
    fun `every exercise declares which way its body faces`() {
        // Without it, "the front of the arm" means nothing and every muscle
        // ends up on the wrong side.
        library.exercises.forEach { (id, animation) ->
            assertEquals("$id has a malformed facing", 3, animation.facing.size)
            assertTrue(
                "$id faces nowhere, so its muscles cannot be placed",
                animation.facingAt.length() > 0.5f,
            )
        }
    }

    @Test
    fun `every pose referenced by a sequence exists`() {
        library.exercises.forEach { (id, animation) ->
            animation.sequence.forEach { frame ->
                assertTrue(
                    "$id references pose \"${frame.pose}\" which is not defined",
                    animation.pose(frame.pose) != null,
                )
            }
            animation.hardestPose?.let {
                assertTrue(
                    "$id names hardest pose \"$it\" which is not defined",
                    animation.pose(it) != null,
                )
            }
        }
    }

    @Test
    fun `every joint carries all three coordinates`() {
        library.exercises.forEach { (id, animation) ->
            animation.poses.forEach { (name, pose) ->
                jointsOf(pose).forEach { (joint, values) ->
                    assertEquals("$id/$name: $joint needs [x, y, z]", 3, values.size)
                }
            }
        }
    }

    @Test
    fun `limbs keep their length across every pose of an exercise`() {
        // A limb that stretches between keyframes is the clearest sign of a
        // mistyped coordinate, so it is worth failing over. This caught the
        // push-up's torso shrinking by three hundredths on the first attempt.
        library.exercises.forEach { (id, animation) ->
            val symmetry = animation.symmetryMode
            val measured = animation.poses.mapValues { (_, pose) ->
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
    fun `left and right limbs are the same length as each other`() {
        library.exercises.forEach { (id, animation) ->
            val symmetry = animation.symmetryMode
            animation.poses.forEach { (name, pose) ->
                val armA = pose.armA()
                val armB = pose.armB(symmetry)
                assertEquals(
                    "$id/$name: the two upper arms differ",
                    distance(pose.neckAt, armA[0]),
                    distance(pose.neckAt, armB[0]),
                    0.02f,
                )
                val legA = pose.legA()
                val legB = pose.legB(symmetry)
                assertEquals(
                    "$id/$name: the two thighs differ",
                    distance(pose.hipAt, legA[0]),
                    distance(pose.hipAt, legB[0]),
                    0.02f,
                )
            }
        }
    }

    // ---- The camera ------------------------------------------------------

    @Test
    fun `figures stay inside the frame at their chosen camera`() {
        library.exercises.forEach { (id, animation) ->
            animation.poses.forEach { (name, pose) ->
                allPoints(pose, animation.symmetryMode).forEach { (joint, point) ->
                    val flat = point.project(animation.camera)
                    assertTrue(
                        "$id/$name: $joint projects to (${flat.x}, ${flat.y}), outside the frame",
                        flat.x in -0.02f..1.02f && flat.y in -0.02f..1.02f,
                    )
                }
            }
        }
    }

    @Test
    fun `figures stay inside the frame when turned right around`() {
        // A viewer can drag the figure, so it has to survive any angle rather
        // than only the one it was authored for.
        library.exercises.forEach { (id, animation) ->
            (0 until 360 step 30).forEach { turn ->
                val camera = animation.camera.turnedBy(turn.toFloat())
                animation.poses.forEach { (name, pose) ->
                    allPoints(pose, animation.symmetryMode).forEach { (joint, point) ->
                        val flat = point.project(camera)
                        assertTrue(
                            "$id/$name: $joint leaves the frame at $turn degrees",
                            flat.x in -0.12f..1.12f && flat.y in -0.12f..1.12f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `projection keeps a limb the same length when seen face on`() {
        // Turning the camera to look along a limb foreshortens it, which is
        // correct. Turning it perpendicular must not.
        val pose = library.animationFor("pull_up")!!.pose("hang")!!
        val worldLength = distance(pose.neckAt, pose.hipAt)

        listOf(0f, 15f, 45f, 90f).forEach { azimuth ->
            val camera = Camera(azimuth = azimuth)
            val neck = pose.neckAt.project(camera)
            val hip = pose.hipAt.project(camera)
            val onScreen = sqrt(
                (neck.x - hip.x) * (neck.x - hip.x) + (neck.y - hip.y) * (neck.y - hip.y),
            )
            // The torso runs down y, which no amount of azimuth foreshortens.
            assertEquals("Torso changed length at $azimuth degrees", worldLength, onScreen, 0.01f)
        }
    }

    @Test
    fun `depth tells the two sides of the body apart`() {
        // If both arms projected to the same depth there would be nothing for
        // the fading to work with and the figure would read flat.
        val animation = library.animationFor("push_up")!!
        val pose = animation.pose("top")!!
        val symmetry = animation.symmetryMode

        val near = pose.armA().first().project(animation.camera).depth
        val far = pose.armB(symmetry).first().project(animation.camera).depth

        assertTrue("The two arms landed at the same depth", abs(near - far) > 0.01f)
    }

    @Test
    fun `a camera turn is remembered and clamped in tilt`() {
        val camera = Camera(azimuth = 20f, elevation = 10f)
        assertEquals(50f, camera.turnedBy(30f).azimuth, 0.001f)
        // Tilting past straight down is disorienting, so it stops.
        assertEquals(60f, camera.turnedBy(0f, 90f).elevation, 0.001f)
        assertEquals(-60f, camera.turnedBy(0f, -90f).elevation, 0.001f)
    }

    // ---- Tempo -----------------------------------------------------------

    @Test
    fun `a default tempo gives about a second each way`() {
        val timeline = library.animationFor("push_up")!!.timeline(Tempo.Default)
        assertEquals(2f, timeline.totalSeconds, 0.01f)
    }

    @Test
    fun `a 3-second lowering rung really lasts three seconds`() {
        val tempo = Tempo.fromLadderStep("3-second lowering")
        assertEquals(3f, tempo.lowerSeconds!!, 0.001f)

        val timeline = library.animationFor("push_up")!!.timeline(tempo)
        val lowering = timeline.segments.first { it.role == "lower" }
        assertEquals(3f, lowering.seconds, 0.01f)
        assertEquals(4f, timeline.totalSeconds, 0.01f)
    }

    @Test
    fun `a pause rung holds at the hardest point for the time it states`() {
        val tempo = Tempo.fromLadderStep("Pause 1 to 2 seconds just above the floor")
        assertEquals(1f, tempo.pauseSeconds, 0.001f)

        val timeline = library.animationFor("push_up")!!.timeline(tempo)
        val pause = timeline.segments.first { it.role == "pause" }
        assertTrue("A pause must not move", pause.isHold)
        assertEquals(1f, pause.seconds, 0.01f)
    }

    @Test
    fun `a pause rung with no stated time falls back to one second`() {
        assertEquals(1f, Tempo.fromLadderStep("Pause at the bottom").pauseSeconds, 0.001f)
    }

    @Test
    fun `a rung that changes neither tempo is left alone`() {
        listOf("Standard push-up", "Feet elevated on a chair", "Bodyweight").forEach {
            assertEquals(Tempo.Default, Tempo.fromLadderStep(it))
        }
        // A backpack rung carries numbers but changes no timing.
        assertEquals(
            Tempo.Default,
            Tempo.fromLadderStep("Backpack load, 1 to 2 kg at a time, up to about 12 kg"),
        )
    }

    @Test
    fun `a timed hold is static and needs no clock`() {
        val timeline = library.animationFor("hollow_hold")!!.timeline()
        assertTrue(timeline.isStatic)
        assertEquals(0f, timeline.totalSeconds, 0.001f)
        assertTrue("It still has a pose to draw", timeline.segments.isNotEmpty())
    }

    @Test
    fun `the timeline loops rather than running off the end`() {
        val timeline = library.animationFor("push_up")!!.timeline()
        val atStart = timeline.poseAt(0f)
        val oneLoopLater = timeline.poseAt(timeline.totalSeconds)

        assertEquals(atStart.neck[1], oneLoopLater.neck[1], 0.01f)
        val middle = timeline.poseAt(timeline.totalSeconds / 2f)
        assertTrue(abs(middle.neck[1] - atStart.neck[1]) > 0.01f)
    }

    @Test
    fun `a blend keeps limbs the right length in the middle of a rep`() {
        // Interpolating positions can in principle shorten a limb mid-swing.
        // Over a real rep it must stay within the same tolerance as the
        // keyframes themselves.
        val animation = library.animationFor("push_up")!!
        val timeline = animation.timeline()
        val reference = distance(
            animation.pose("top")!!.neckAt,
            animation.pose("top")!!.hipAt,
        )

        (0..20).forEach { step ->
            val pose = timeline.poseAt(timeline.totalSeconds * step / 20f)
            assertEquals(
                "The torso changed length part way through the rep",
                reference,
                distance(pose.neckAt, pose.hipAt),
                0.02f,
            )
        }
    }

    // ---- Mirroring -------------------------------------------------------

    @Test
    fun `mirroring flips the body's own left-right axis and leaves height alone`() {
        val animation = library.animationFor("push_up")!!
        val pose = animation.pose("top")!!
        val mirrored = pose.mirrored(animation.symmetryMode)

        // push_up is laid out along x, so its left-right axis is z.
        assertEquals(-pose.elbow[2], mirrored.elbow[2], 0.0001f)
        assertEquals(pose.elbow[1], mirrored.elbow[1], 0.0001f)
        assertEquals(pose.elbow[0], mirrored.elbow[0], 0.0001f)
        assertSamePose(pose, mirrored.mirrored(animation.symmetryMode))
    }

    @Test
    fun `a front-on exercise mirrors across x instead`() {
        val animation = library.animationFor("pull_up")!!
        val pose = animation.pose("hang")!!
        val mirrored = pose.mirrored(animation.symmetryMode)

        assertEquals(-pose.elbow[0], mirrored.elbow[0], 0.0001f)
        assertEquals(pose.elbow[1], mirrored.elbow[1], 0.0001f)
    }

    @Test
    fun `mirroring works for every each-side exercise that has an animation`() {
        val eachSide = repo.protocol.sessions.values
            .flatMap { it.exercises }
            .filter { it.eachSide }
            .map { it.exerciseId }
            .distinct()

        eachSide.mapNotNull { id -> library.animationFor(id)?.let { id to it } }
            .forEach { (id, animation) ->
                val symmetry = animation.symmetryMode
                animation.poses.forEach { (name, pose) ->
                    val other = pose.mirrored(symmetry)
                    assertSamePose(pose, other.mirrored(symmetry), "$id/$name did not mirror back")
                    assertFalse(
                        "$id/$name is unchanged by mirroring, so the figure will not flip",
                        samePose(pose, other),
                    )
                }
            }
    }

    // ---- Helpers ---------------------------------------------------------

    private fun jointsOf(pose: Pose): List<Pair<String, List<Float>>> = listOfNotNull(
        "head" to pose.head,
        "neck" to pose.neck,
        "hip" to pose.hip,
        "elbow" to pose.elbow,
        "wrist" to pose.wrist,
        "knee" to pose.knee,
        "ankle" to pose.ankle,
        "toe" to pose.toe,
        pose.elbowOther?.let { "other elbow" to it },
        pose.wristOther?.let { "other wrist" to it },
        pose.kneeOther?.let { "other knee" to it },
        pose.ankleOther?.let { "other ankle" to it },
        pose.toeOther?.let { "other toe" to it },
    )

    /** Every point actually drawn, including the reflected side. */
    private fun allPoints(pose: Pose, symmetry: Symmetry): List<Pair<String, Vec3>> = buildList {
        add("head" to pose.headAt)
        add("neck" to pose.neckAt)
        add("hip" to pose.hipAt)
        pose.armA().forEachIndexed { i, p -> add("arm A $i" to p) }
        pose.armB(symmetry).forEachIndexed { i, p -> add("arm B $i" to p) }
        pose.legA().forEachIndexed { i, p -> add("leg A $i" to p) }
        pose.legB(symmetry).forEachIndexed { i, p -> add("leg B $i" to p) }
    }

    private fun samePose(a: Pose, b: Pose): Boolean =
        jointsOf(a).zip(jointsOf(b)).all { (left, right) ->
            left.second.indices.all { abs(left.second[it] - right.second[it]) < 0.0005f }
        }

    /**
     * Poses compare with a tolerance, not exactly. Mirroring negates a
     * coordinate, and doing it twice leaves float dust that means nothing on
     * screen.
     */
    private fun assertSamePose(expected: Pose, actual: Pose, message: String = "Poses differ") {
        jointsOf(expected).zip(jointsOf(actual)).forEach { (left, right) ->
            left.second.indices.forEach { axis ->
                assertEquals(
                    "$message: ${left.first} axis $axis",
                    left.second[axis],
                    right.second[axis],
                    0.0005f,
                )
            }
        }
    }

    private fun distance(a: Vec3, b: Vec3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
