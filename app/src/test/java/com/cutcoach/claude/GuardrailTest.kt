package com.cutcoach.claude

import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * What happens to an answer the model should not have given.
 *
 * These are written as the adversarial half of Phase 4: not "does a good answer
 * pass", but "does a plausible bad one get through". A model asked for a
 * substitution will happily add a set to make up for an easier movement, and
 * that is exactly the answer that has to bounce.
 */
class GuardrailTest {

    private lateinit var repo: ProtocolRepository
    private val rehab = listOf("Shoulder external rotation", "Scapular retraction")

    private fun asset(name: String) = File("src/main/assets/$name").readText()

    @Before
    fun setUp() {
        repo = (ProtocolRepository.parse(asset("protocol.json"), asset("exercises.json"))
            as ProtocolLoad.Success).repository
    }

    private fun <T> rejectionsOf(checked: Checked<T>): List<Rejection> =
        (checked as? Checked.Rejected)?.reasons ?: emptyList()

    // ---- The example: a substitution that adds volume ---------------------

    @Test
    fun `a substitution that adds a set is rejected, and says which rule`() {
        // The protocol gives the push-up 4 sets. This answer is otherwise
        // sensible, which is what makes it the dangerous kind.
        val answer = SubstituteAnswer(
            replaces = "push_up",
            withExerciseId = "pike_push_up",
            sets = 5,
            reps = 10,
            reason = "No floor space for push-ups, so a pike push-up at the wall.",
        )

        val checked = validate(repo, answer, rehab)
        val reasons = rejectionsOf(checked)

        assertTrue("A 5-set substitution was accepted", checked is Checked.Rejected)
        assertEquals(1, reasons.size)
        assertEquals("no_extra_volume", reasons.single().rule)
        assertTrue(
            reasons.single().because,
            reasons.single().because.contains("5 sets") &&
                reasons.single().because.contains("4"),
        )
    }

    @Test
    fun `the same substitution at the same set count is accepted`() {
        // The control. Without it the test above only proves the validator
        // says no to everything.
        val answer = SubstituteAnswer(
            replaces = "push_up",
            withExerciseId = "pike_push_up",
            sets = 4,
            reps = 10,
            reason = "No floor space for push-ups, so a pike push-up at the wall.",
        )
        assertTrue(validate(repo, answer, rehab) is Checked.Accepted)
    }

    // ---- Rehab, in both directions ----------------------------------------

    @Test
    fun `substituting rehab work is rejected`() {
        val answer = SubstituteAnswer(
            replaces = "Shoulder external rotation",
            withExerciseId = "pike_push_up",
            sets = 3,
            reps = 10,
            reason = "A pike push-up hits the shoulder too.",
        )
        assertTrue(rejectionsOf(validate(repo, answer, rehab)).any { it.rule == "rehab_untouched" })
    }

    @Test
    fun `suggesting rehab work as a replacement is rejected`() {
        val answer = SubstituteAnswer(
            replaces = "push_up",
            withDescription = "Try Scapular retraction instead, it is gentler.",
            sets = 4,
            reps = 10,
            reason = "Shoulder feels tight.",
        )
        assertTrue(rejectionsOf(validate(repo, answer, rehab)).any { it.rule == "rehab_untouched" })
    }

    // ---- Legs -------------------------------------------------------------

    @Test
    fun `swapping leg work for upper body is rejected`() {
        // How a cut quietly becomes pull-ups and push-ups only.
        val answer = SubstituteAnswer(
            replaces = "bulgarian_split_squat",
            withExerciseId = "push_up",
            sets = 4,
            reps = 12,
            reason = "No chair available, so push-ups instead.",
        )
        val reasons = rejectionsOf(validate(repo, answer, rehab))
        assertTrue(reasons.toString(), reasons.any { it.rule == "keep_leg_work" })
    }

    // ---- Invented exercises -----------------------------------------------

    @Test
    fun `an exercise id that does not exist is rejected`() {
        val answer = SubstituteAnswer(
            replaces = "push_up",
            withExerciseId = "archer_push_up",
            sets = 4,
            reps = 8,
            reason = "A harder push-up variation.",
        )
        val reasons = rejectionsOf(validate(repo, answer, rehab))
        assertTrue(reasons.any { it.rule == "unknown_exercise" })
    }

    // ---- The wording guardrails -------------------------------------------

    @Test
    fun `a review suggesting a lie-in is rejected`() {
        val answer = review(sleep = "You are short on sleep. Try to sleep in on Sunday.")
        val reasons = rejectionsOf(validate(repo, answer, rehab))
        assertTrue(reasons.toString(), reasons.any { it.rule == "no_weekend_sleep_in" })
    }

    @Test
    fun `a review offering to eat back watch calories is rejected`() {
        val answer = review(
            steps = "You walked a lot. Your watch says 600 calories burned, so you " +
                "can eat back some of that.",
        )
        val reasons = rejectionsOf(validate(repo, answer, rehab))
        assertTrue(reasons.toString(), reasons.any { it.rule == "no_eating_back_watch_calories" })
    }

    // ---- The calorie floor -------------------------------------------------

    @Test
    fun `a review proposing a calorie cut is rejected against the floor`() {
        val answer = review(
            change = ProposedChange(
                kind = "calories",
                caloriesKcalChange = -200,
                reason = "Progress has stalled, so take a little off.",
            ),
        )
        val reasons = rejectionsOf(validate(repo, answer, rehab))
        val floor = reasons.single { it.rule == "calorie_floor" }

        // The daily target already sits on the floor, so any cut breaks it.
        assertTrue(floor.because, floor.because.contains("1700") && floor.because.contains("1900"))
    }

    @Test
    fun `a further cut while losing too fast is rejected twice over`() {
        val answer = review(
            change = ProposedChange(
                kind = "calories",
                caloriesKcalChange = -150,
                reason = "Keep the deficit up.",
            ),
        )
        val reasons = rejectionsOf(validate(repo, answer, rehab, losingTooFast = true))

        assertTrue(reasons.any { it.rule == "calorie_floor" })
        assertTrue(reasons.any { it.rule == "fast_loss_flag" })
    }

    // ---- Volume in a review ------------------------------------------------

    @Test
    fun `a review adding sets or sessions is rejected`() {
        val answer = review(
            change = ProposedChange(
                kind = "exercise",
                setsChange = 2,
                sessionsChange = 1,
                reason = "You have capacity for more.",
            ),
        )
        val reasons = rejectionsOf(validate(repo, answer, rehab))
        assertTrue(reasons.any { it.rule == "no_extra_volume" })
    }

    @Test
    fun `a review adding steps is accepted, because steps are the one lever`() {
        val answer = review(
            change = ProposedChange(
                kind = "steps",
                stepsChange = 1500,
                reason = "Three flat weeks, and the calorie lever is on the floor.",
            ),
        )
        assertTrue(validate(repo, answer, rehab) is Checked.Accepted)
    }

    @Test
    fun `a review with no change at all is accepted`() {
        assertTrue(validate(repo, review(), rehab) is Checked.Accepted)
    }

    // ---- More than one thing wrong ----------------------------------------

    @Test
    fun `an answer that breaks two rules reports both`() {
        // Fixing one would still leave it invalid, so both are named.
        val answer = SubstituteAnswer(
            replaces = "bulgarian_split_squat",
            withExerciseId = "push_up",
            sets = 6,
            reps = 12,
            reason = "Do more push-ups instead.",
        )
        val reasons = rejectionsOf(validate(repo, answer, rehab))

        assertTrue(reasons.any { it.rule == "no_extra_volume" })
        assertTrue(reasons.any { it.rule == "keep_leg_work" })
    }

    /**
     * Writes a worked rejection to build/rejected-example.txt.
     *
     * Not an assertion so much as a way to read what the owner would actually
     * be shown, in the same way the pose contact sheet is a test that draws.
     */
    @Test
    fun `write a worked example of a rejected suggestion`() {
        val answer = SubstituteAnswer(
            replaces = "bulgarian_split_squat",
            withExerciseId = "push_up",
            sets = 6,
            reps = 12,
            reason = "Knee is a bit tired, so do more push-ups instead and " +
                "sleep in tomorrow to recover.",
        )
        val reasons = rejectionsOf(validate(repo, answer, rehab))

        val text = buildString {
            appendLine("WHAT CLAUDE RETURNED")
            appendLine("  replaces:        ${answer.replaces}")
            appendLine("  withExerciseId:  ${answer.withExerciseId}")
            appendLine("  sets:            ${answer.sets}")
            appendLine("  reps:            ${answer.reps}")
            appendLine("  reason:          ${answer.reason}")
            appendLine()
            appendLine("WHAT THE RULES ENGINE DID: rejected, ${reasons.size} rules broken")
            reasons.forEach { appendLine("  [${it.rule}] ${it.because}") }
            appendLine()
            appendLine("The owner sees the rejection, not the suggestion.")
        }

        File("build").mkdirs()
        File("build/rejected-example.txt").writeText(text)
        assertTrue("Nothing was rejected", reasons.isNotEmpty())
    }

    // ---- Helpers ----------------------------------------------------------

    private fun review(
        sleep: String = "Lights out averaged 22:50 against a 22:45 target.",
        heart: String = "Sleeping heart rate averaged 60, inside the goal.",
        steps: String = "You averaged 9,100 steps against a 9,500 target.",
        weight: String = "The 7-day average fell 0.5 kg.",
        strength: String = "Push-ups reached the top of the range on every set.",
        change: ProposedChange? = null,
    ) = WeeklyReviewAnswer(
        sleep = sleep,
        sleepingHeartRate = heart,
        steps = steps,
        weight = weight,
        strength = strength,
        oneChange = change,
    )
}
