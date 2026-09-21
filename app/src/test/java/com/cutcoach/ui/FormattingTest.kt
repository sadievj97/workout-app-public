package com.cutcoach.ui

import com.cutcoach.data.db.BodyMetricEntity
import com.cutcoach.data.db.sevenDayAverageKg
import com.cutcoach.ui.session.formatClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        assertEquals(76.2, entries.sevenDayAverageKg()!!, 0.001)
    }

    @Test
    fun `the average looks back no further than seven days`() {
        // Eight days, with the oldest far heavier. It must not be counted.
        val entries = (0..7).map { body("2026-09-${21 - it}", if (it == 7) 100.0 else 76.0) }
        assertEquals(76.0, entries.sevenDayAverageKg()!!, 0.001)
    }

    @Test
    fun `with nothing weighed the average is absent rather than zero`() {
        assertNull(emptyList<BodyMetricEntity>().sevenDayAverageKg())
        assertNull(listOf(body("2026-09-21", null)).sevenDayAverageKg())
    }

    private fun body(date: String, kg: Double?) = BodyMetricEntity(date = date, weightKg = kg)
}
