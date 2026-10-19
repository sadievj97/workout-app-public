package com.cutcoach.ui.session

import com.cutcoach.data.session.SessionStep
import com.cutcoach.data.session.Side
import com.cutcoach.data.protocol.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Stepping back in the player, and the set it un-logs.
 *
 * Going back onto a set removes what was logged for it, so that redoing the
 * set writes one row rather than two. Picking the wrong set to remove is the
 * way this feature does damage: the owner presses Back to fix a mistyped rep
 * count and silently loses a different set instead.
 *
 * The view model needs a device, so the choice of which set to remove is a
 * plain function and this tests that. The rest of going back is an index
 * minus one.
 */
class PreviousStepTest {

    private fun reps(min: Int, max: Int) = Target(type = "reps", min = min, max = max)

    private fun repSet(
        exerciseId: String,
        setNumber: Int,
        side: Side = Side.NONE,
    ) = SessionStep.RepSet(
        exerciseId = exerciseId,
        setNumber = setNumber,
        totalSets = 4,
        side = side,
        target = reps(8, 15),
    )

    private fun logged(
        exerciseId: String,
        setNumber: Int,
        side: Side = Side.NONE,
        reps: Int,
    ) = LoggedSet(
        exerciseId = exerciseId,
        setNumber = setNumber,
        side = side,
        reps = reps,
    )

    @Test
    fun `the set for a step is the one with its exercise, number and side`() {
        val log = listOf(
            logged("push_up", 1, reps = 12),
            logged("push_up", 2, reps = 11),
            logged("push_up", 3, reps = 10),
        )

        val found = setLoggedFor(repSet("push_up", 2), log)

        assertEquals(11, found?.reps)
    }

    @Test
    fun `an each-side exercise does not confuse its two sides`() {
        // The case that would do real damage. Set 2 exists twice, once per
        // side, and matching on exercise and number alone would take the left
        // one whichever side you were actually on.
        val log = listOf(
            logged("single_leg_rdl", 2, Side.LEFT, reps = 10),
            logged("single_leg_rdl", 2, Side.RIGHT, reps = 9),
        )

        val left = setLoggedFor(repSet("single_leg_rdl", 2, Side.LEFT), log)
        val right = setLoggedFor(repSet("single_leg_rdl", 2, Side.RIGHT), log)

        assertEquals("Left should find the left set", 10, left?.reps)
        assertEquals("Right should find the right set", 9, right?.reps)
    }

    @Test
    fun `two exercises with the same set number stay apart`() {
        val log = listOf(
            logged("push_up", 1, reps = 15),
            logged("pike_push_up", 1, reps = 8),
        )

        assertEquals(15, setLoggedFor(repSet("push_up", 1), log)?.reps)
        assertEquals(8, setLoggedFor(repSet("pike_push_up", 1), log)?.reps)
    }

    @Test
    fun `a set that was never logged removes nothing`() {
        // Pressing Back onto a set you skipped rather than logged.
        val log = listOf(logged("push_up", 1, reps = 12))

        assertNull(setLoggedFor(repSet("push_up", 2), log))
    }

    @Test
    fun `steps that log nothing remove nothing`() {
        // Back onto a rest, a switch-sides or a warm-up block must not reach
        // into the log and take the last set that happens to be there.
        val log = listOf(logged("push_up", 1, reps = 12))

        assertNull(setLoggedFor(SessionStep.Rest(seconds = 60), log))
        assertNull(setLoggedFor(SessionStep.SwitchSides(seconds = 10), log))
        assertNull(
            setLoggedFor(
                SessionStep.Warmup(blockNumber = 1, totalBlocks = 3, what = "March", seconds = 120),
                log,
            ),
        )
        assertNull(setLoggedFor(SessionStep.Finished, log))
    }

    @Test
    fun `a timed hold is matched the same way as a rep set`() {
        val log = listOf(
            logged("side_plank", 1, Side.LEFT, reps = 0).copy(reps = null, seconds = 30),
            logged("side_plank", 1, Side.RIGHT, reps = 0).copy(reps = null, seconds = 45),
        )

        val hold = SessionStep.HoldSet(
            exerciseId = "side_plank",
            setNumber = 1,
            totalSets = 3,
            side = Side.RIGHT,
            seconds = 45,
            minimumSeconds = 30,
        )

        assertEquals(45, setLoggedFor(hold, log)?.seconds)
    }

    @Test
    fun `the most recent attempt is the one removed`() {
        // Back, redo, Back again. Only the latest row for that set should go,
        // or the earlier correction is the one lost.
        val log = listOf(
            logged("push_up", 1, reps = 12),
            logged("push_up", 1, reps = 11),
        )

        assertEquals(11, setLoggedFor(repSet("push_up", 1), log)?.reps)
    }
}
