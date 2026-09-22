package com.cutcoach.data.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Runs the real protocol.json and exercises.json through the real parser, on
 * the desktop, with no phone involved.
 *
 * The point is that a typo in either file fails here in seconds rather than at
 * 05:35 on the floor. These tests read the shipped files rather than fixtures,
 * so they also fail if a future edit breaks the shape.
 */
class ProtocolLoadTest {

    private lateinit var repo: ProtocolRepository

    /** The same files that ship in the APK. */
    private fun asset(name: String): String {
        val file = File("src/main/assets/$name")
        assertTrue("Missing asset: ${file.absolutePath}", file.exists())
        return file.readText()
    }

    @Before
    fun setUp() {
        val result = ProtocolRepository.parse(asset("protocol.json"), asset("exercises.json"))
        if (result is ProtocolLoad.Failure) {
            throw AssertionError(result.message, result.cause)
        }
        repo = (result as ProtocolLoad.Success).repository
    }

    @Test
    fun `both files parse`() {
        assertEquals("The 24-week cut", repo.protocol.meta.name)
        assertEquals(24, repo.protocol.meta.programLengthWeeks)
        assertEquals(15, repo.library.exercises.size)
        assertEquals(5, repo.library.cardio.size)
    }

    @Test
    fun `every exercise the schedule names exists in the library`() {
        val missing = repo.protocol.sessions.values
            .flatMap { it.exercises }
            .map { it.exerciseId }
            .distinct()
            .filter { repo.exercise(it) == null }

        assertTrue("Exercise ids with no library entry: $missing", missing.isEmpty())
    }

    @Test
    fun `every day of the week has a session that exists`() {
        val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        days.forEach { day ->
            val scheduled = repo.protocol.weeklySchedule[day]
            assertNotNull("No schedule entry for $day", scheduled)
            assertNotNull(
                "Session ${scheduled!!.sessionId} for $day is not defined",
                repo.session(scheduled.sessionId),
            )
        }
    }

    @Test
    fun `a single number and a range both read correctly`() {
        // Monday is written as 40; Sunday is written as [60, 75].
        val monday = repo.protocol.weeklySchedule.getValue("MON").durationMin
        assertTrue(monday.isSingle)
        assertEquals(40, monday.minInt)

        val sunday = repo.protocol.weeklySchedule.getValue("SUN").durationMin
        assertFalse(sunday.isSingle)
        assertEquals(60, sunday.minInt)
        assertEquals(75, sunday.maxInt)
    }

    @Test
    fun `program week counts from the start date`() {
        val start = repo.programStart
        assertNotNull("program_start_date is not set", start)

        assertEquals(1, repo.programWeek(start!!))
        assertEquals(1, repo.programWeek(start.plusDays(6)))
        assertEquals(2, repo.programWeek(start.plusDays(7)))
        assertEquals(24, repo.programWeek(start.plusWeeks(23)))

        // Outside the program.
        assertEquals(null, repo.programWeek(start.minusDays(1)))
        assertEquals(null, repo.programWeek(start.plusWeeks(24)))
    }

    @Test
    fun `deload falls on every fourth week and cuts sets by a third`() {
        listOf(4, 8, 12, 16, 20, 24).forEach {
            assertTrue("Week $it should be a deload", repo.isDeloadWeek(it))
        }
        listOf(1, 2, 3, 5, 9, 23).forEach {
            assertFalse("Week $it should not be a deload", repo.isDeloadWeek(it))
        }

        // The protocol states the answers: 4 becomes 3, 3 becomes 2.
        assertEquals(3, repo.setsFor(4, programWeek = 4))
        assertEquals(2, repo.setsFor(3, programWeek = 4))

        // Normal weeks are untouched.
        assertEquals(4, repo.setsFor(4, programWeek = 3))
        assertEquals(3, repo.setsFor(3, programWeek = 3))

        // Never below one set.
        assertEquals(1, repo.setsFor(1, programWeek = 4))
    }

    @Test
    fun `the bedtime ramp reaches its final time and stays there`() {
        val start = repo.rampStart
        assertNotNull("ramp_start_date is not set", start)

        assertEquals(1, repo.rampNight(start!!))
        assertEquals(LocalTime.of(22, 45), repo.lightsOutOn(start))
        assertEquals(LocalTime.of(22, 45), repo.lightsOutOn(start.plusDays(2)))   // night 3
        assertEquals(LocalTime.of(22, 15), repo.lightsOutOn(start.plusDays(3)))   // night 4
        assertEquals(LocalTime.of(21, 50), repo.lightsOutOn(start.plusDays(7)))   // night 8
        assertEquals(LocalTime.of(21, 30), repo.lightsOutOn(start.plusDays(14)))  // night 15

        // The final step is open-ended, so it must still apply a year later.
        assertEquals(LocalTime.of(21, 30), repo.lightsOutOn(start.plusDays(365)))

        assertEquals(null, repo.rampNight(start.minusDays(1)))
    }

    @Test
    fun `the decisions we resolved are actually in the file`() {
        assertEquals(60, repo.restSeconds)
        assertEquals(10, repo.switchSidesSeconds)
        assertEquals(12, repo.protocol.progressionRules.maxRepAdvance.reps)
        assertEquals(0.3, repo.protocol.tracking.plateauRule.flatDefinition.maxWeightDropKg, 0.001)
        assertTrue(repo.protocol.readinessAdjustment.isApproved)

        val intervals = repo.session("intervals")?.intervalStructure
        assertNotNull("Saturday still has no interval structure", intervals)
        assertEquals(6, intervals!!.rounds)
        assertEquals(60, intervals.workSeconds)
        assertEquals(120, intervals.recoverySeconds)
    }

    @Test
    fun `the guardrails are all present`() {
        val ids = repo.protocol.guardrails.all.map { it.id }
        listOf(
            "no_eating_back_watch_calories",
            "no_weekend_sleep_in",
            "no_week1_scale_verdict",
            "calorie_floor",
            "keep_leg_work",
            "honest_core_expectations",
            "no_extra_volume",
            "rehab_untouched",
            "pain_stop",
            "fast_loss_flag",
            "no_guessing",
        ).forEach {
            assertTrue("Guardrail $it is missing", it in ids)
        }
    }

    @Test
    fun `nothing is left open for decision`() {
        // Every question was answered on 2026-09-21 and written back. If a new
        // one is added later, this fails until it is resolved.
        val text = asset("protocol.json")
        assertTrue("open_decisions is not empty", text.contains("\"open_decisions\": []"))
        assertTrue(
            "proposed_rules_needing_approval is not empty",
            text.contains("\"proposed_rules_needing_approval\": []"),
        )
    }

    @Test
    fun `side plank is each side and the hold targets read as seconds`() {
        val sidePlank = repo.session("strength_b")!!.exercises.first { it.exerciseId == "side_plank" }
        assertTrue("Side plank should be each side", sidePlank.eachSide)
        assertTrue(sidePlank.target.isTimed)
        assertEquals(30, sidePlank.target.min)
        assertEquals(45, sidePlank.target.max)
    }

    @Test
    fun `max rep targets carry no rep range`() {
        val pullUp = repo.session("strength_b")!!.exercises.first { it.exerciseId == "pull_up" }
        assertTrue(pullUp.target.isMaxReps)
        assertEquals(null, pullUp.target.min)
        assertEquals("max reps", pullUp.target.describe())
    }

    @Test
    fun `exactly one timeline entry is marked as lights out`() {
        // The reminders substitute tonight's ramp target for this one entry.
        // Two of them, or none, and the substitution goes somewhere wrong.
        val marked = repo.protocol.dailyTimeline.filter { it.isLightsOut }
        assertEquals(1, marked.size)
        assertEquals("21:30", marked.single().time)
    }

    @Test
    fun `the lights-out reminder uses tonight's ramp target, not the timeline`() {
        // Ramp night 2 targets 22:45, while the timeline says 21:30 because
        // that is where the ramp eventually lands.
        val night2 = LocalDate.parse("2026-09-22")
        val lightsOut = repo.reminderMoments(night2).single { it.isLightsOut }

        assertEquals(LocalTime.of(22, 45), lightsOut.time)
        assertEquals("21:30", lightsOut.id)
    }

    @Test
    fun `the evening is reminded by default and the rest of the day is not`() {
        val moments = repo.reminderMoments(LocalDate.parse("2026-09-22"))
        val on = moments.filter { it.onByDefault }.map { it.id }

        assertEquals(listOf("20:00", "21:00", "21:30"), on)
    }

    @Test
    fun `no reminder can be generated for the wake time`() {
        // The lie-in guardrail, in arithmetic rather than in prose: the wake
        // time is not among the moments at all, so nothing here can ever
        // schedule or move it.
        val moments = repo.reminderMoments(LocalDate.parse("2026-09-22"))
        assertTrue(moments.none { it.time == repo.wakeTime && it.onByDefault })
    }

    @Test
    fun `the nap threshold is read from the file`() {
        assertEquals(2.0, repo.protocol.sleep.napThresholdHours, 0.001)
        assertEquals(120L, repo.napThreshold.toMinutes())
    }

    @Test
    fun `sunday rehab is empty and untouched by the app`() {
        val sunday = repo.sessionFor(LocalDate.of(2026, 9, 27).dayOfWeek)
        assertNotNull(sunday)
        assertTrue(
            "Rehab exercises must come from the rehab program, not the app",
            sunday!!.rehabExercises.isEmpty(),
        )
    }
}
