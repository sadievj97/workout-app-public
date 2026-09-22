package com.cutcoach.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The derived metrics, against the awkward nights rather than the tidy ones.
 *
 * Every one of these is a case the watch will actually produce: a fragmented
 * night, a night with no heart rate at all, a walk that started before
 * midnight, a watch that stopped recording mid-session.
 */
class HealthMetricsTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private fun samples(
        from: Instant,
        count: Int,
        everySeconds: Long,
        bpm: (Int) -> Long,
    ): List<HeartSample> = (0 until count).map {
        HeartSample(from.plusSeconds(it * everySeconds), bpm(it))
    }

    // ---- Which night a sleep belongs to ----------------------------------

    @Test
    fun `sleep starting in the evening belongs to that night`() {
        assertEquals(
            LocalDate.parse("2026-09-22"),
            nightOf(at("2026-09-22", "22:45"), zone),
        )
    }

    @Test
    fun `sleep starting after midnight belongs to the night before`() {
        // The ramp counts the evening you went to bed, so a 01:30 start on the
        // 23rd is still the night of the 22nd.
        assertEquals(
            LocalDate.parse("2026-09-22"),
            nightOf(at("2026-09-23", "01:30"), zone),
        )
    }

    // ---- A fragmented night ---------------------------------------------

    @Test
    fun `the main sleep is the longest stretch, not the first`() {
        // Watches split a night where you stirred. A twenty-minute doze at
        // 21:40 must not be treated as the night.
        val doze = SleepWindow(at("2026-09-22", "21:40"), at("2026-09-22", "22:00"))
        val real = SleepWindow(at("2026-09-22", "22:50"), at("2026-09-23", "05:00"))
        val later = SleepWindow(at("2026-09-23", "05:10"), at("2026-09-23", "05:40"))

        assertEquals(real, mainSleep(listOf(doze, real, later)))
    }

    @Test
    fun `no sleep at all gives nothing rather than a zero-length night`() {
        assertNull(mainSleep(emptyList()))
    }

    // ---- Lights out against the ramp ------------------------------------

    @Test
    fun `the brief's own example reads as forty-five minutes late`() {
        // Ramp night 2 targets 22:45; sleep began at 23:30.
        val late = minutesAfterTarget(
            sleepStart = at("2026-09-22", "23:30"),
            target = LocalTime.of(22, 45),
            night = LocalDate.parse("2026-09-22"),
            zone = zone,
        )
        assertEquals(45L, late)
    }

    @Test
    fun `going to bed early reads as negative, not as zero`() {
        val early = minutesAfterTarget(
            sleepStart = at("2026-09-22", "22:20"),
            target = LocalTime.of(22, 45),
            night = LocalDate.parse("2026-09-22"),
            zone = zone,
        )
        assertEquals(-25L, early)
    }

    @Test
    fun `sleep after midnight is still measured against that night's target`() {
        val late = minutesAfterTarget(
            sleepStart = at("2026-09-23", "00:15"),
            target = LocalTime.of(22, 45),
            night = LocalDate.parse("2026-09-22"),
            zone = zone,
        )
        assertEquals(90L, late)
    }

    // ---- Sleeping heart rate --------------------------------------------

    @Test
    fun `sleeping heart rate averages only the samples during the night`() {
        val sleep = SleepWindow(at("2026-09-22", "23:00"), at("2026-09-23", "05:00"))
        val duringSleep = samples(at("2026-09-22", "23:30"), 10, 600) { 58L }
        val awakeAfter = samples(at("2026-09-23", "06:00"), 10, 600) { 130L }

        assertEquals(58, sleepingHeartRate(duringSleep + awakeAfter, sleep))
    }

    @Test
    fun `a night with no heart rate gives nothing, not zero`() {
        // This is the guardrail in arithmetic form: missing data must not
        // become a number that later reads as a resting heart rate of zero.
        val sleep = SleepWindow(at("2026-09-22", "23:00"), at("2026-09-23", "05:00"))
        val daytimeOnly = samples(at("2026-09-23", "09:00"), 20, 600) { 90L }

        assertNull(sleepingHeartRate(daytimeOnly, sleep))
    }

    // ---- Time in zone ----------------------------------------------------

    @Test
    fun `time in zone counts only the stretches inside it`() {
        val start = at("2026-09-22", "06:00")
        // Thirty samples a minute apart: the first ten in zone, the rest above.
        val walk = samples(start, 31, 60) { if (it < 10) 125L else 165L }

        val inZone = timeInZone(walk, low = 115, high = 140)
        assertEquals(10L, inZone.toMinutes())
    }

    @Test
    fun `a watch that stopped recording does not earn the whole gap`() {
        // Two samples in zone twenty minutes apart means the watch stopped, not
        // that twenty minutes were held in Zone 2.
        val start = at("2026-09-22", "06:00")
        val sparse = listOf(
            HeartSample(start, 125L),
            HeartSample(start.plusSeconds(1200), 125L),
            HeartSample(start.plusSeconds(1260), 125L),
        )
        val inZone = timeInZone(sparse, low = 115, high = 140)

        // The long gap is capped at two minutes; the short one counts in full.
        assertEquals(3L, inZone.toMinutes())
    }

    @Test
    fun `a single sample is not a duration`() {
        val one = listOf(HeartSample(at("2026-09-22", "06:00"), 125L))
        assertEquals(Duration.ZERO, timeInZone(one, 115, 140))
        assertEquals(Duration.ZERO, timeInZone(emptyList(), 115, 140))
    }

    // ---- The zone verdict ------------------------------------------------

    @Test
    fun `the brief's own example reads as above Zone 2`() {
        // A Zone 2 day with an average of 150 against 115 to 140.
        assertEquals(ZoneVerdict.ABOVE, zoneVerdict(150, 115, 140))
    }

    @Test
    fun `the edges of the zone are inside it`() {
        assertEquals(ZoneVerdict.IN_ZONE, zoneVerdict(115, 115, 140))
        assertEquals(ZoneVerdict.IN_ZONE, zoneVerdict(140, 115, 140))
        assertEquals(ZoneVerdict.BELOW, zoneVerdict(114, 115, 140))
        assertEquals(ZoneVerdict.ABOVE, zoneVerdict(141, 115, 140))
    }

    // ---- Rolling averages ------------------------------------------------

    @Test
    fun `a rolling average skips the days with nothing rather than counting zero`() {
        val week = listOf(76.0, null, 75.8, null, 75.9, 76.1, null)
        assertEquals(75.95, rollingAverage(week, 7)!!, 0.001)
    }

    @Test
    fun `a run with nothing in it has no average`() {
        assertNull(rollingAverage(listOf(null, null, null), 7))
        assertNull(rollingAverage(emptyList(), 7))
    }

    @Test
    fun `a rolling average looks back no further than it is asked to`() {
        val values = listOf(70.0, 70.0, 70.0, 70.0, 70.0, 70.0, 70.0, 100.0)
        assertEquals(70.0, rollingAverage(values, 7)!!, 0.001)
    }

    // ---- The readiness comparison ---------------------------------------

    @Test
    fun `an elevated sleeping heart rate is only called once there is a baseline`() {
        // The approved rule needs fourteen nights. With three, five above
        // average is noise, so it declines to say.
        assertNull(
            isSleepingHeartRateElevated(
                tonight = 70,
                baseline = listOf(60, 61, 59),
                aboveBy = 5,
                minimumNights = 14,
            ),
        )
    }

    @Test
    fun `with a baseline it compares as the rule states`() {
        val baseline = List(14) { 60 }

        assertEquals(
            true,
            isSleepingHeartRateElevated(65, baseline, aboveBy = 5, minimumNights = 14),
        )
        assertEquals(
            false,
            isSleepingHeartRateElevated(64, baseline, aboveBy = 5, minimumNights = 14),
        )
    }

    @Test
    fun `no reading tonight means no verdict`() {
        assertNull(
            isSleepingHeartRateElevated(null, List(14) { 60 }, 5, 14),
        )
    }

    // ---- Sleep duration --------------------------------------------------

    @Test
    fun `a night's length survives crossing midnight`() {
        val night = SleepWindow(at("2026-09-22", "22:50"), at("2026-09-23", "05:05"))
        assertEquals(375L, night.duration.toMinutes())
        assertTrue(night.duration.toHours() == 6L)
    }
}
