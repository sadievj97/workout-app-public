package com.cutcoach.data.protocol

import com.cutcoach.data.poses.Tempo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Where an exercise sits on its ladder, and what that changes.
 *
 * The rung is the app's only difficulty control, so the things that can go
 * wrong quietly are worth pinning: a rung past the end of its ladder, a rung
 * that does not change the tempo it claims to, and rehab work acquiring a
 * ladder it must never have.
 */
class LadderTest {

    private lateinit var repo: ProtocolRepository

    private fun asset(name: String): String = File("src/main/assets/$name").readText()

    @Before
    fun setUp() {
        repo = (ProtocolRepository.parse(asset("protocol.json"), asset("exercises.json"))
            as ProtocolLoad.Success).repository
    }

    /** The clamp [TrainingRepository.setLadderStep] applies, without a database. */
    private fun clamp(step: Int, rungs: Int) = step.coerceIn(0, (rungs - 1).coerceAtLeast(0))

    @Test
    fun `every scheduled exercise has a ladder to stand on`() {
        val scheduled = repo.protocol.sessions.values
            .flatMap { it.exercises }
            .map { it.exerciseId }
            .distinct()

        scheduled.forEach { id ->
            val exercise = repo.exercise(id)
            assertTrue("$id has no library entry", exercise != null)
            assertTrue("$id has an empty ladder", exercise!!.ladder.isNotEmpty())
        }
    }

    @Test
    fun `a rung past the end of a ladder is pulled back to the last one`() {
        // Out of range would leave the cue card showing no rung as current and
        // the figure silently back on the default tempo.
        val pushUp = repo.exercise("push_up")!!
        val rungs = pushUp.ladder.size

        assertEquals(rungs - 1, clamp(99, rungs))
        assertEquals(0, clamp(-3, rungs))
        assertEquals(2, clamp(2, rungs))
    }

    @Test
    fun `a single-rung ladder still has a valid rung`() {
        assertEquals(0, clamp(0, 1))
        assertEquals(0, clamp(5, 1))
        // And a ladder with nothing in it cannot produce a negative index.
        assertEquals(0, clamp(3, 0))
    }

    @Test
    fun `the tempo rungs actually change the tempo`() {
        // If these stopped parsing, the rung would still be selectable and the
        // figure would keep moving at the default speed with nothing to show
        // for it.
        val pushUp = repo.exercise("push_up")!!

        val lowering = pushUp.ladder.first { it.contains("lowering", ignoreCase = true) }
        assertEquals(3f, Tempo.fromLadderStep(lowering).lowerSeconds)

        val pause = pushUp.ladder.first { it.startsWith("Pause") }
        assertNotEquals(0f, Tempo.fromLadderStep(pause).pauseSeconds)
    }

    @Test
    fun `every ladder that names a lowering or a pause is read by the tempo`() {
        repo.library.exercises.forEach { exercise ->
            exercise.ladder.forEach { rung ->
                val lower = rung.lowercase()
                val tempo = Tempo.fromLadderStep(rung)
                if (lower.contains("lowering") || lower.contains("slide-out")) {
                    // A slide-out rung reads as a lowering only if it says so.
                    if (lower.contains("lowering")) {
                        assertTrue(
                            "${exercise.id}: '$rung' sets no lowering time",
                            tempo.lowerSeconds != null,
                        )
                    }
                }
                if (lower.startsWith("pause") || lower.contains("-second pause")) {
                    assertTrue(
                        "${exercise.id}: '$rung' sets no pause",
                        tempo.pauseSeconds > 0f,
                    )
                }
            }
        }
    }

    @Test
    fun `the first rung is the default tempo everywhere`() {
        // Starting on rung one must never quietly slow the figure down.
        repo.library.exercises.forEach { exercise ->
            val first = exercise.ladder.firstOrNull() ?: return@forEach
            assertEquals(
                "${exercise.id}: the first rung '$first' is not the default tempo",
                Tempo.Default,
                Tempo.fromLadderStep(first),
            )
        }
    }

    @Test
    fun `rehab work has no ladder for anything to set`() {
        // The guardrail in arithmetic rather than prose: rehab exercises are
        // typed in by the owner and are not in exercises.json at all, so no
        // rung exists for the app to choose, raise or lower.
        val sunday = repo.sessionFor(java.time.LocalDate.of(2026, 9, 27).dayOfWeek)!!
        assertTrue(sunday.rehabExercises.isEmpty())

        val libraryIds = repo.library.exercises.map { it.id }.toSet()
        sunday.rehabExercises.forEach { rehab ->
            assertTrue("Rehab '$rehab' must not be a library exercise", rehab !in libraryIds)
        }
    }
}
