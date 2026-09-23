package com.cutcoach.data.poses

import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/**
 * Writes every exercise's limb lengths, base pose and ladder to a file.
 *
 * A working tool rather than a check. Authoring a variant means matching the
 * base figure's limb lengths exactly, and computing them by hand from raw
 * coordinates is how a stretched thigh gets written down. This prints the
 * numbers to match, alongside which rung the base pose depicts and which rungs
 * still have no drawing.
 *
 * Same idea as the contact sheet: a test whose output is for reading.
 */
class LimbReportTest {

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
    fun `write the limb report`() {
        val report = buildString {
            appendLine("LIMB LENGTHS AND VARIANT COVERAGE")
            appendLine("A variant must match its base figure's lengths to within 0.02.")
            appendLine()

            library.exercises.toSortedMap().forEach { (id, animation) ->
                val ladder = repo.exercise(id)?.ladder.orEmpty()
                appendLine("=== $id ===")
                appendLine("  symmetry: ${animation.symmetry}   base_rung: ${animation.baseRung}")

                val base = animation.poses.values.firstOrNull()
                if (base != null) {
                    val symmetry = animation.symmetryMode
                    val armA = base.armA()
                    val armB = base.armB(symmetry)
                    val legA = base.legA()
                    val legB = base.legB(symmetry)
                    appendLine("  torso %.4f  head %.4f".format(
                        distance(base.neckAt, base.hipAt),
                        distance(base.neckAt, base.headAt),
                    ))
                    appendLine("  upper arm %.4f  forearm %.4f".format(
                        distance(base.neckAt, armA[0]),
                        distance(armA[0], armA[1]),
                    ))
                    appendLine("  other upper arm %.4f  other forearm %.4f".format(
                        distance(base.neckAt, armB[0]),
                        distance(armB[0], armB[1]),
                    ))
                    appendLine("  thigh %.4f  shin %.4f  foot %.4f".format(
                        distance(base.hipAt, legA[0]),
                        distance(legA[0], legA[1]),
                        distance(legA[1], legA[2]),
                    ))
                    appendLine("  other thigh %.4f  other shin %.4f  other foot %.4f".format(
                        distance(base.hipAt, legB[0]),
                        distance(legB[0], legB[1]),
                        distance(legB[1], legB[2]),
                    ))
                    appendLine("  poses: ${animation.poses.keys.joinToString(", ")}")
                    animation.props.forEach { appendLine("  prop: ${it.type} y=${it.y} h=${it.height}") }
                }

                appendLine("  ladder:")
                ladder.forEachIndexed { rung, text ->
                    appendLine("    [${stateOf(animation, ladder, rung)}] $rung. $text")
                }
                appendLine()
            }

            val todo = library.exercises.entries.flatMap { (id, animation) ->
                val ladder = repo.exercise(id)?.ladder.orEmpty()
                ladder.indices
                    .filter { stateOf(animation, ladder, it) == "TODO  " }
                    .map { "$id r$it: ${ladder[it]}" }
            }
            appendLine("STILL TO DRAW (${todo.size}):")
            todo.forEach { appendLine("  $it") }
        }

        File("build").mkdirs()
        File("build/limb-report.txt").writeText(report)
        assertTrue(report.isNotBlank())
    }

    private fun distance(a: Vec3, b: Vec3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * What a rung needs, if anything.
     *
     * A rung that only changes tempo or adds load is drawn correctly by the
     * base figure already, so counting it as missing would overstate the work
     * by about four to one and hide the rungs that genuinely need geometry.
     */
    private fun stateOf(
        animation: ExerciseAnimation,
        ladder: List<String>,
        rung: Int,
    ): String {
        val text = ladder.getOrNull(rung).orEmpty()
        return when {
            rung == animation.baseRung -> "BASE  "
            animation.variantFor(rung)?.sameAs != null -> "BORROW"
            animation.variantFor(rung) != null -> "DRAWN "
            Tempo.fromLadderStep(text) != Tempo.Default -> "TEMPO "
            text.contains("backpack", ignoreCase = true) -> "LOAD  "
            else -> "TODO  "
        }
    }
}
