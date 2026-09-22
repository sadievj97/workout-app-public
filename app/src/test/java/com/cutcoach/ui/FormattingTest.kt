package com.cutcoach.ui

import com.cutcoach.data.db.BodyMetricEntity
import com.cutcoach.data.db.WeightSource
import com.cutcoach.data.db.sevenDayAverageKg
import com.cutcoach.data.db.watchDaysInWindow
import com.cutcoach.ui.session.formatClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The two pieces of arithmetic the screens do for themselves. Both are read at
 * a glance while tired, so both are worth pinning down.
 */
class FormattingTest {

    @Test
    fun `the clock pads to two digits so the timer does not jump`() {
        assertEquals("00:00", formatClock(0))
        assertEquals("00:09", formatClock(9))
        assertEquals("01:00", formatClock(60))
        assertEquals("00:45", formatClock(45))
        assertEquals("40:00", formatClock(40 * 60))
    }

    @Test
    fun `past an hour the clock grows an hours field`() {
        assertEquals("1:00:00", formatClock(3600))
        assertEquals("1:05:30", formatClock(3600 + 5 * 60 + 30))
    }

    @Test
    fun `a negative clock reads as zero rather than as a minus sign`() {
        assertEquals("00:00", formatClock(-5))
    }

    @Test
    fun `the seven-day average uses only days that have a weight`() {
        val entries = listOf(
            body("2026-09-21", 76.0),
            body("2026-09-20", 76.4),
            body("2026-09-19", null),
            body("2026-09-18", 76.2),
        )
        assertEquals(76.2, entries.sevenDayAverageKg(today)!!, 0.001)
    }

    @Test
    fun `the average looks back no further than seven days`() {
        // Eight days, with the oldest far heavier. It must not be counted.
        val entries = (0..7).map { body("2026-09-${21 - it}", if (it == 7) 100.0 else 76.0) }
        assertEquals(76.0, entries.sevenDayAverageKg(today)!!, 0.001)
    }

    @Test
    fun `a week with missed days does not reach back for more rows`() {
        // Four weighed days in the window and one from a fortnight ago. Counting
        // seven *rows* would pull that old, far heavier day in and call the
        // result a 7-day average. Counting seven *days* leaves it out.
        val entries = listOf(
            body("2026-09-21", 76.0),
            body("2026-09-19", 76.2),
            body("2026-09-17", 76.0),
            body("2026-09-15", 75.8),
            body("2026-09-07", 100.0),
            body("2026-09-05", 100.0),
            body("2026-09-03", 100.0),
        )
        assertEquals(76.0, entries.sevenDayAverageKg(today)!!, 0.001)
    }

    @Test
    fun `a day later than the window is not counted either`() {
        // The 30-row query is ordered by date, but a stale row from tomorrow
        // must not creep into today's average.
        val entries = listOf(body("2026-09-22", 90.0), body("2026-09-21", 76.0))
        assertEquals(76.0, entries.sevenDayAverageKg(today)!!, 0.001)
    }

    @Test
    fun `with nothing weighed the average is absent rather than zero`() {
        assertNull(emptyList<BodyMetricEntity>().sevenDayAverageKg(today))
        assertNull(listOf(body("2026-09-21", null)).sevenDayAverageKg(today))
        // Weighed, but a month ago. That is not a 7-day average.
        assertNull(listOf(body("2026-08-21", 76.0)).sevenDayAverageKg(today))
    }

    @Test
    fun `the watch days in the window are counted, and only the weighed ones`() {
        val entries = listOf(
            body("2026-09-21", 76.0, WeightSource.WATCH),
            body("2026-09-20", 76.4),
            body("2026-09-19", null, WeightSource.WATCH),
            body("2026-09-18", 76.2, WeightSource.WATCH),
            body("2026-09-01", 76.2, WeightSource.WATCH),
        )
        assertEquals(2, entries.watchDaysInWindow(today))
    }

    private val today: LocalDate = LocalDate.parse("2026-09-21")

    private fun body(date: String, kg: Double?, source: String = WeightSource.MANUAL) =
        BodyMetricEntity(date = date, weightKg = kg, source = source)
}
