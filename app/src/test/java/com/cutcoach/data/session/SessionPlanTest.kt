package com.cutcoach.data.session

import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The plan is the session: if it is wrong, the player walks you through the
 * wrong workout. These tests check the shape of it against the real protocol.
 */
class SessionPlanTest {

    private lateinit var repo: ProtocolRepository

    @Before
    fun setUp() {
        val result = ProtocolRepository.parse(
            File("src/main/assets/protocol.json").readText(),
            File("src/main/assets/exercises.json").readText(),
        )
        repo = (result as ProtocolLoad.Success).repository
    }

    private fun plan(sessionId: String, week: Int?) =
        buildSessionPlan(repo, repo.session(sessionId)!!, week)

    @Test
    fun `the warm-up comes first, once, and lasts eight minutes`() {
        val steps = plan("strength_a", week = 1)
        val warmups = steps.filterIsInstance<SessionStep.Warmup>()

        assertEquals(3, warmups.size)
        assertEquals(steps.take(3), warmups)
        assertEquals(8 * 60, warmups.sumOf { it.seconds })
    }

    @Test
    fun `each-side exercises run left, switch, right`() {
        val steps = plan("strength_a", week = 1)

        // Bulgarian split squat is each side.
        val first = steps.indexOfFirst {
            it is SessionStep.RepSet && it.exerciseId == "bulgarian_split_squat"
        }
        val left = steps[first] as SessionStep.RepSet
        val switch = steps[first + 1]
        val right = steps[first + 2] as SessionStep.RepSet

        assertEquals(Side.LEFT, left.side)
        assertTrue("Expected a switch-sides gap", switch is SessionStep.SwitchSides)
        assertEquals(repo.switchSidesSeconds, (switch as SessionStep.SwitchSides).seconds)
        assertEquals(Side.RIGHT, right.side)
        assertEquals(left.setNumber, right.setNumber)
    }

    @Test
    fun `a single-sided exercise has no switch step`() {
        val steps = plan("strength_a", week = 1)
        val pushUpIndex = steps.indexOfFirst {
            it is SessionStep.RepSet && it.exerciseId == "push_up"
        }
        assertFalse(steps[pushUpIndex + 1] is SessionStep.SwitchSides)
    }

    @Test
    fun `rest follows every set except the last`() {
        val steps = plan("strength_a", week = 1)

        assertTrue("Session should end with Finished", steps.last() is SessionStep.Finished)
        assertFalse(
            "There should be no rest at the very end",
            steps[steps.lastIndex - 1] is SessionStep.Rest,
        )
        assertTrue(steps.filterIsInstance<SessionStep.Rest>().all { it.seconds == repo.restSeconds })
    }

    @Test
    fun `a deload week produces fewer sets`() {
        val normal = plan("strength_a", week = 1)
        val deload = plan("strength_a", week = 4)

        val normalPushUps = normal.filterIsInstance<SessionStep.RepSet>()
            .count { it.exerciseId == "push_up" }
        val deloadPushUps = deload.filterIsInstance<SessionStep.RepSet>()
            .count { it.exerciseId == "push_up" }

        // Push-ups are 4 sets, so a deload gives 3.
        assertEquals(4, normalPushUps)
        assertEquals(3, deloadPushUps)
        assertTrue("A deload session should be shorter", deload.size < normal.size)
    }

    @Test
    fun `timed holds count down from the top of the range`() {
        // Side plank in Strength B is 30 to 45 seconds, each side.
        val steps = plan("strength_b", week = 1)
        val holds = steps.filterIsInstance<SessionStep.HoldSet>()
            .filter { it.exerciseId == "side_plank" }

        assertTrue(holds.isNotEmpty())
        holds.forEach {
            assertEquals(45, it.seconds)
            assertEquals(30, it.minimumSeconds)
        }

        // Three sets per side means six holds.
        assertEquals(6, holds.size)
    }

    @Test
    fun `a fixed hold has the same top and floor`() {
        // Hollow hold in Strength A is 30 to 30.
        val hold = plan("strength_a", week = 1)
            .filterIsInstance<SessionStep.HoldSet>()
            .first { it.exerciseId == "hollow_hold" }

        assertEquals(30, hold.seconds)
        assertEquals(30, hold.minimumSeconds)
    }

    @Test
    fun `every set is numbered within its exercise`() {
        val sets = plan("strength_a", week = 1)
            .filterIsInstance<SessionStep.RepSet>()
            .filter { it.exerciseId == "push_up" }

        assertEquals(listOf(1, 2, 3, 4), sets.map { it.setNumber })
        assertTrue(sets.all { it.totalSets == 4 })
    }

    @Test
    fun `cardio days have no step plan`() {
        assertTrue(plan("zone2_cardio", week = 1).isEmpty())
        assertTrue(plan("intervals", week = 1).isEmpty())
        assertTrue(plan("easy_walk_rehab", week = 1).isEmpty())
    }

    @Test
    fun `every exercise in a plan can be looked up for its cue card`() {
        listOf("strength_a", "strength_b", "strength_c").forEach { id ->
            plan(id, week = 1).forEach { step ->
                val exerciseId = when (step) {
                    is SessionStep.RepSet -> step.exerciseId
                    is SessionStep.HoldSet -> step.exerciseId
                    else -> null
                }
                if (exerciseId != null) {
                    assertTrue(
                        "No library entry for $exerciseId in $id",
                        repo.exercise(exerciseId) != null,
                    )
                }
            }
        }
    }
}
