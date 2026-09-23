package com.cutcoach.rules

import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.health.DailyHealth
import com.cutcoach.health.SleepNight
import com.cutcoach.health.WorkoutSession
import com.cutcoach.health.ZoneVerdict
import com.cutcoach.health.describeRampGap
import com.cutcoach.health.minutesAfterTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The twelve rule tests the brief names, in its order.
 *
 * Written before the rules they check. Each one is a case where being quietly
 * wrong would cost the owner something real: a progression that never fires, a
 * scale verdict in the week the protocol says to ignore the scale, or a
 * suggestion to eat back calories a watch guessed at.
 */
class RuleTest {

    private lateinit var repo: ProtocolRepository
    private val zone: ZoneId = ZoneId.of("Europe/Amsterdam")

    private fun asset(name: String) = File("src/main/assets/$name").readText()

    @Before
    fun setUp() {
        repo = (ProtocolRepository.parse(asset("protocol.json"), asset("exercises.json"))
            as ProtocolLoad.Success).repository
    }

    private fun reps(vararg counts: Int) = counts.map { SetResult(reps = it) }

    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private fun input(
        today: LocalDate = LocalDate.parse("2026-10-19"),
        programWeek: Int? = 5,
        weights: List<Double?> = emptyList(),
        waists: List<Double?> = emptyList(),
        health: DailyHealth? = null,
        lastNight: DailyHealth? = null,
        baseline: List<Int> = emptyList(),
        pain: PainReport? = null,
        lastChange: LocalDate? = null,
        rehab: List<String> = emptyList(),
    ) = RuleInput(
        today = today,
        programWeek = programWeek,
        weeklyWeightKg = weights,
        weeklyWaistCm = waists,
        today_ = health,
        lastNight = lastNight,
        sleepingHrBaseline = baseline,
        pain = pain,
        lastPlateauChange = lastChange,
        rehabExercises = rehab,
    )

    // ---- 1 ---------------------------------------------------------------

    @Test
    fun `push-up sets 15 15 15 15 advance one ladder step and restart at 8`() {
        // The push-up's target is 8 to 15 reps over 4 sets. Every set at the
        // top of the range is the whole condition for double progression.
        val result = progressionFor(repo, "push_up", reps(15, 15, 15, 15), currentRung = 0)

        assertEquals(Progression.Advance(toRung = 1, restartAt = 8), result)
    }

    // ---- 2 ---------------------------------------------------------------

    @Test
    fun `push-up sets 15 15 14 12 stay on the step and add reps`() {
        // One short set is enough to hold the rung. Advancing on an average
        // would move someone up before they had earned it.
        val result = progressionFor(repo, "push_up", reps(15, 15, 14, 12), currentRung = 0)

        assertEquals(Progression.AddReps, result)
    }

    // ---- 3 ---------------------------------------------------------------

    @Test
    fun `in program week 4 every exercise's sets are cut by a third`() {
        val everyExercise = repo.protocol.sessions.values.flatMap { it.exercises }
        assertTrue("No exercises to check", everyExercise.isNotEmpty())

        everyExercise.forEach { exercise ->
            val normal = repo.setsFor(exercise.sets, programWeek = 3)
            val deload = repo.setsFor(exercise.sets, programWeek = 4)

            assertEquals(
                "${exercise.exerciseId} is not reduced in a deload week",
                Math.round(exercise.sets * repo.protocol.progressionRules.deload.setMultiplier),
                deload.toLong(),
            )
            assertTrue("${exercise.exerciseId} got harder in a deload", deload <= normal)
            assertTrue("${exercise.exerciseId} fell below one set", deload >= 1)
        }
    }

    // ---- 4 ---------------------------------------------------------------

    @Test
    fun `in week 1 no weight verdict is shown`() {
        // The protocol's own words: week 1 is water and cortisol. The numbers
        // are still displayed; only the judgement is withheld.
        val falling = listOf(74.8, 76.0)
        val week1 = recommendationsFor(repo, input(programWeek = 1, weights = falling))

        assertTrue(
            "A weight verdict appeared in week 1: $week1",
            week1.none { it.kind == Recommendation.Kind.WEIGHT },
        )

        // And by week 2 the same data does produce one, so the rule is the week
        // and not a silently broken code path.
        val week2 = recommendationsFor(repo, input(programWeek = 2, weights = falling))
        assertTrue(
            "No weight verdict in week 2 either, so week 1 proves nothing",
            week2.any { it.kind == Recommendation.Kind.WEIGHT },
        )
    }

    // ---- 5 ---------------------------------------------------------------

    @Test
    fun `three flat weeks suggest exactly one change, and none for three weeks after`() {
        // flat_definition: under 0.3 kg AND under 0.5 cm across three weeks.
        val flatWeights = listOf(75.9, 76.0, 76.1, 76.1)
        val flatWaists = listOf(86.8, 86.9, 87.0, 87.0)

        val flat = input(weights = flatWeights, waists = flatWaists)
        assertTrue("Three flat weeks were not recognised", isFlat(repo, flat))

        val suggestions = recommendationsFor(repo, flat)
            .filter { it.kind == Recommendation.Kind.PLATEAU }

        assertEquals("The plateau rule must change exactly one thing", 1, suggestions.size)
        // Never both: minus 150 kcal OR plus 1,500 steps.
        val text = suggestions.single().headline.lowercase()
        assertFalse(
            "The plateau rule suggested both levers: $text",
            text.contains("kcal") && text.contains("step"),
        )

        // A change was made a week ago, so the rule waits.
        val waiting = input(
            weights = flatWeights,
            waists = flatWaists,
            lastChange = LocalDate.parse("2026-10-19").minusWeeks(1),
        )
        assertTrue(
            "The plateau rule fired again inside its waiting period",
            recommendationsFor(repo, waiting).none { it.kind == Recommendation.Kind.PLATEAU },
        )

        // And after the full wait it may fire again.
        val waited = input(
            weights = flatWeights,
            waists = flatWaists,
            lastChange = LocalDate.parse("2026-10-19").minusWeeks(3).minusDays(1),
        )
        assertTrue(
            "The plateau rule never fires again after waiting",
            recommendationsFor(repo, waited).any { it.kind == Recommendation.Kind.PLATEAU },
        )
    }

    // ---- 6 ---------------------------------------------------------------

    @Test
    fun `losing 0 point 9 kg a week for two weeks flags fast loss and suggests no further cut`() {
        // max_loss_kg_per_week is 0.7.
        val fast = input(weights = listOf(74.2, 75.1, 76.0))
        assertTrue("0.9 kg a week was not flagged", isLosingTooFast(repo, fast))

        val all = recommendationsFor(repo, fast)
        val flag = all.firstOrNull { it.kind == Recommendation.Kind.WEIGHT && it.tone == Recommendation.Tone.STOP }
        assertTrue("No fast-loss flag: $all", flag != null)

        // The guardrail: never suggest further cuts while this is true.
        assertTrue(
            "A cut was suggested while losing too fast: $all",
            all.none { it.kind == Recommendation.Kind.PLATEAU },
        )
    }

    // ---- 7 ---------------------------------------------------------------

    @Test
    fun `a Zone 2 day averaging 150 bpm reads as above Zone 2`() {
        val zone2 = repo.protocol.heartRate.zone2Bpm
        val session = WorkoutSession(
            start = at("2026-10-19", "06:00"),
            end = at("2026-10-19", "06:45"),
            title = "Hill walk",
            total = Duration.ofMinutes(45),
            meanHeartRate = 150,
            inZone = Duration.ofMinutes(4),
            zoneName = "Zone 2",
            zoneLow = zone2.minInt,
            zoneHigh = zone2.maxInt,
            verdict = ZoneVerdict.ABOVE,
        )
        val health = DailyHealth(date = LocalDate.parse("2026-10-19"), sessions = listOf(session))

        val hr = recommendationsFor(repo, input(health = health))
            .filter { it.kind == Recommendation.Kind.HEART_RATE }

        assertTrue("No heart-rate feedback for a Zone 2 day", hr.isNotEmpty())
        val said = hr.joinToString(" ") { "${it.headline} ${it.reason}" }.lowercase()
        assertTrue("Did not say it was above Zone 2: $said", said.contains("above"))
        assertTrue(
            "Did not name the zone it was measured against: $said",
            said.contains("zone 2") && said.contains("${zone2.maxInt}"),
        )
    }

    // ---- 8 ---------------------------------------------------------------

    @Test
    fun `sleep starting at 23-30 on ramp night 2 reads as 45 minutes late`() {
        // Ramp night 1 is 2026-09-21 and nights 1 to 3 target 22:45.
        val night2 = LocalDate.parse("2026-09-22")
        assertEquals(2, repo.rampNight(night2))
        assertEquals(LocalTime.of(22, 45), repo.lightsOutOn(night2))

        val late = minutesAfterTarget(
            sleepStart = at("2026-09-22", "23:30"),
            target = LocalTime.of(22, 45),
            night = night2,
            zone = zone,
        )
        assertEquals(45L, late)
        assertEquals("45 minutes late", describeRampGap(late))

        val sleep = SleepNight(
            start = at("2026-09-22", "23:30"),
            end = at("2026-09-23", "05:00"),
            duration = Duration.ofMinutes(330),
            minutesAfterTarget = 45L,
        )
        val lastNight = DailyHealth(date = night2, night = sleep)

        val said = recommendationsFor(repo, input(today = night2.plusDays(1), lastNight = lastNight))
            .filter { it.kind == Recommendation.Kind.SLEEP }
            .joinToString(" ") { "${it.headline} ${it.reason}" }

        assertTrue("The ramp miss was not reported: $said", said.contains("45 minutes late"))
    }

    // ---- 9 ---------------------------------------------------------------

    @Test
    fun `watch calories are never shown anywhere as food to eat back`() {
        // The strongest form of this guardrail is that the number never enters
        // the app at all: no calorie record type is read from Health Connect,
        // so there is nothing to display by accident.
        val readTypes = com.cutcoach.health.HealthConnectManager.RECORD_TYPES
            .map { it.type.simpleName.orEmpty() }
        assertTrue(
            "A calories-burned record type is being read: $readTypes",
            readTypes.none { it.contains("Calories", ignoreCase = true) },
        )

        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(
            "The manifest asks for a calories-burned permission",
            manifest.contains("READ_ACTIVE_CALORIES_BURNED") ||
                manifest.contains("READ_TOTAL_CALORIES_BURNED"),
        )

        // And nothing the engine can say mentions eating anything back.
        val everything = broadSweep().flatten()
            .joinToString(" ") { "${it.headline} ${it.reason}" }
            .lowercase()
        assertFalse(
            "A recommendation mentioned eating calories back: $everything",
            everything.contains("eat back") || everything.contains("burned"),
        )
    }

    // ---- 10 --------------------------------------------------------------

    @Test
    fun `rehab exercises are never changed by any recommendation`() {
        val rehab = listOf("Shoulder external rotation", "Scapular retraction")
        val sweep = broadSweep(rehab)

        sweep.flatten().forEach { recommendation ->
            assertTrue(
                "A recommendation targeted rehab: $recommendation",
                recommendation.exerciseId !in rehab,
            )
            val text = "${recommendation.headline} ${recommendation.reason}".lowercase()
            rehab.forEach { item ->
                assertFalse(
                    "A recommendation named rehab work: $recommendation",
                    text.contains(item.lowercase()),
                )
            }
        }

        // And no progression exists for something that has no ladder.
        assertEquals(
            Progression.StayPut,
            progressionFor(repo, "Shoulder external rotation", reps(15, 15, 15), currentRung = 0),
        )
    }

    // ---- 11 --------------------------------------------------------------

    @Test
    fun `a pain report suggests no workout and recommends a clinician`() {
        val hurt = input(pain = PainReport(PainKind.SERIOUS, "sharp in the left shoulder"))
        val all = recommendationsFor(repo, hurt)

        val stop = all.firstOrNull { it.kind == Recommendation.Kind.PAIN }
        assertTrue("Pain produced no recommendation at all", stop != null)
        assertEquals(Recommendation.Tone.STOP, stop!!.tone)
        assertTrue(
            "Pain advice did not mention a clinician: ${stop.headline} ${stop.reason}",
            "${stop.headline} ${stop.reason}".lowercase().contains("clinician"),
        )

        // Nothing else may suggest training while this is true.
        assertTrue(
            "A workout was still suggested alongside pain: $all",
            all.none {
                it.kind == Recommendation.Kind.PROGRESSION ||
                    it.kind == Recommendation.Kind.READINESS ||
                    it.kind == Recommendation.Kind.PLATEAU
            },
        )
    }

    // ---- 12 --------------------------------------------------------------

    @Test
    fun `missing sleep data asks a check-in question rather than guessing`() {
        val nothing = input(lastNight = DailyHealth(date = LocalDate.parse("2026-10-18")))
        val all = recommendationsFor(repo, nothing)

        val question = all.firstOrNull { it.kind == Recommendation.Kind.CHECK_IN }
        assertTrue("Missing sleep produced no check-in: $all", question != null)
        assertTrue(
            "The check-in is not a question: ${question!!.headline}",
            question.headline.trimEnd().endsWith("?"),
        )

        // The readiness rule must not fire on absent data, in either direction.
        assertTrue(
            "Readiness fired without sleep data: $all",
            all.none { it.kind == Recommendation.Kind.READINESS },
        )
        assertTrue(
            "A sleep verdict was invented from no data: $all",
            all.none { it.kind == Recommendation.Kind.SLEEP },
        )
    }

    // ---- Shared ----------------------------------------------------------

    /**
     * A wide spread of inputs, for the guardrails that must hold for every
     * answer the engine can give rather than for one case.
     */
    private fun broadSweep(rehab: List<String> = emptyList()): List<List<Recommendation>> {
        val sleep = SleepNight(
            start = at("2026-10-18", "23:30"),
            end = at("2026-10-19", "05:00"),
            duration = Duration.ofMinutes(330),
            sleepingHeartRate = 70,
            minutesAfterTarget = 45L,
        )
        val session = WorkoutSession(
            start = at("2026-10-19", "06:00"),
            end = at("2026-10-19", "06:45"),
            title = "Hill walk",
            total = Duration.ofMinutes(45),
            meanHeartRate = 150,
            inZone = Duration.ofMinutes(4),
            zoneName = "Zone 2",
            zoneLow = 115,
            zoneHigh = 140,
            verdict = ZoneVerdict.ABOVE,
        )
        val rich = DailyHealth(
            date = LocalDate.parse("2026-10-19"),
            steps = 4200,
            night = sleep,
            sessions = listOf(session),
        )

        return listOf(
            input(rehab = rehab),
            input(programWeek = 1, weights = listOf(74.8, 76.0), rehab = rehab),
            input(weights = listOf(74.2, 75.1, 76.0), rehab = rehab),
            input(
                weights = listOf(75.9, 76.0, 76.1, 76.1),
                waists = listOf(86.8, 86.9, 87.0, 87.0),
                rehab = rehab,
            ),
            input(health = rich, lastNight = DailyHealth(LocalDate.parse("2026-10-18"), night = sleep), baseline = List(14) { 60 }, rehab = rehab),
            input(pain = PainReport(PainKind.SERIOUS), rehab = rehab),
            input(lastNight = DailyHealth(LocalDate.parse("2026-10-18")), rehab = rehab),
        ).map { recommendationsFor(repo, it) }
    }

    // ---- The Energy Score fallback, approved 2026-09-22 -------------------

    @Test
    fun `energy score only speaks when there is no sleep at all`() {
        // The approved rule is untouched. A night that was recorded is judged
        // by sleep and heart rate, whatever Samsung thinks of the day.
        val sleep = SleepNight(
            start = at("2026-10-18", "22:40"),
            end = at("2026-10-19", "06:10"),
            duration = Duration.ofMinutes(450),
            sleepingHeartRate = 58,
        )
        val slept = input(
            lastNight = DailyHealth(LocalDate.parse("2026-10-18"), night = sleep),
            baseline = List(14) { 60 },
        ).copy(energyScores = List(14) { if (it == 0) 60f else 85f })

        val readiness = recommendationsFor(repo, slept)
            .filter { it.kind == Recommendation.Kind.READINESS }

        assertTrue(
            "A dismal Energy Score overrode a good night: $readiness",
            readiness.isEmpty(),
        )
    }

    @Test
    fun `with no sleep, a low energy score gives a yellow day`() {
        val noSleep = input(lastNight = DailyHealth(LocalDate.parse("2026-10-18")))
            // 14 days averaging about 85, today well under it.
            .copy(energyScores = listOf(76f) + List(13) { 86f })

        val readiness = recommendationsFor(repo, noSleep)
            .single { it.kind == Recommendation.Kind.READINESS }

        assertEquals(Recommendation.Tone.ATTENTION, readiness.tone)
        assertTrue(readiness.headline, readiness.headline.contains("Yellow"))
        assertTrue(
            "It did not say the verdict came from Energy Score: ${readiness.reason}",
            readiness.reason.contains("Energy Score"),
        )
        // no_extra_volume: it may only ever take a set away.
        assertTrue(readiness.headline.contains("dropping"))
    }

    @Test
    fun `the fallback never reaches red`() {
        // A red day means a short night AND an elevated heart rate. One number
        // cannot tell those apart, so a stand-in must be more cautious than the
        // rule it stands in for.
        val dire = input(lastNight = DailyHealth(LocalDate.parse("2026-10-18")))
            .copy(energyScores = listOf(20f) + List(13) { 90f })

        val readiness = recommendationsFor(repo, dire)
            .single { it.kind == Recommendation.Kind.READINESS }

        assertFalse(readiness.headline.lowercase(), readiness.headline.lowercase().contains("red"))
        assertFalse(
            "The fallback suggested replacing the session",
            readiness.headline.lowercase().contains("walk instead"),
        )
    }

    @Test
    fun `a normal energy score says nothing`() {
        val fine = input(lastNight = DailyHealth(LocalDate.parse("2026-10-18")))
            .copy(energyScores = listOf(84f) + List(13) { 85f })

        assertTrue(
            recommendationsFor(repo, fine).none { it.kind == Recommendation.Kind.READINESS },
        )
    }

    @Test
    fun `no sleep and no energy score is still a question, not a guess`() {
        // The whole point of guardrail no_guessing. A fallback that fired on an
        // empty list would be inventing a verdict out of nothing at all.
        val nothing = input(lastNight = DailyHealth(LocalDate.parse("2026-10-18")))
        val all = recommendationsFor(repo, nothing)

        assertTrue(all.none { it.kind == Recommendation.Kind.READINESS })
        assertTrue(all.any { it.kind == Recommendation.Kind.CHECK_IN })
    }

    @Test
    fun `a short run of energy scores is not a baseline`() {
        // Same bar the sleeping heart rate rule sets: with three days of
        // history, "five below average" is noise.
        val thin = input(lastNight = DailyHealth(LocalDate.parse("2026-10-18")))
            .copy(energyScores = listOf(60f, 90f, 90f))

        assertTrue(
            recommendationsFor(repo, thin).none { it.kind == Recommendation.Kind.READINESS },
        )
    }

    @Test
    fun `the fallback's numbers come from the file`() {
        val fallback = repo.protocol.readinessAdjustment.energyScoreFallback
        assertTrue("energy_score_fallback is missing from protocol.json", fallback != null)
        assertTrue(fallback!!.isApproved)
        assertEquals(5, fallback.belowBaseline)
        assertEquals(14, fallback.baselineWindowDays)
    }
}
