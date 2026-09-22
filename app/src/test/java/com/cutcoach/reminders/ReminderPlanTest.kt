package com.cutcoach.reminders

import com.cutcoach.data.protocol.ReminderMoment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When a reminder is due and what it says.
 *
 * The cases that matter are the ones a notification gets wrong quietly: firing
 * at yesterday's bedtime target, firing again for a moment that has already
 * passed, or firing at all for something switched off.
 */
class ReminderPlanTest {

    private fun moment(
        time: String,
        label: String,
        lightsOut: Boolean = false,
        onByDefault: Boolean = true,
        id: String = time,
    ) = ReminderMoment(
        id = id,
        label = label,
        time = LocalTime.parse(time),
        isLightsOut = lightsOut,
        onByDefault = onByDefault,
    )

    private val evening = listOf(
        moment("20:00", "Lamps only, screens dim"),
        moment("21:00", "Phone out of the bedroom"),
        moment("21:30", "Lights out", lightsOut = true),
    )

    private fun fixed(moments: List<ReminderMoment>): (LocalDate) -> List<ReminderMoment> =
        { moments }

    @Test
    fun `the next reminder is the next one still to come today`() {
        val now = LocalDateTime.parse("2026-09-22T19:30")
        val next = plan(fixed(evening), emptyMap(), now).first()

        assertEquals(LocalDateTime.parse("2026-09-22T20:00"), next.at)
        assertEquals("Lamps only, screens dim", next.title)
    }

    @Test
    fun `a moment already past today is booked for tomorrow, not skipped`() {
        val now = LocalDateTime.parse("2026-09-22T20:30")
        val all = plan(fixed(evening), emptyMap(), now)

        assertEquals(LocalDateTime.parse("2026-09-22T21:00"), all[0].at)
        assertEquals(LocalDateTime.parse("2026-09-22T21:30"), all[1].at)
        // The 20:00 one has gone for today; it comes back tomorrow.
        assertEquals(LocalDateTime.parse("2026-09-23T20:00"), all[2].at)
    }

    @Test
    fun `tomorrow's bedtime target is looked up for tomorrow`() {
        // The ramp moves: tonight is 22:45, tomorrow is 22:15. A plan built
        // after tonight's has passed must use tomorrow's, not reuse today's.
        val movingTarget: (LocalDate) -> List<ReminderMoment> = { date ->
            val time = if (date == LocalDate.parse("2026-09-22")) "22:45" else "22:15"
            listOf(moment(time, "Lights out", lightsOut = true, id = "21:30"))
        }
        val now = LocalDateTime.parse("2026-09-22T23:10")

        val next = plan(movingTarget, emptyMap(), now).first()
        assertEquals(LocalDateTime.parse("2026-09-23T22:15"), next.at)
    }

    @Test
    fun `a moment switched off is not planned`() {
        val now = LocalDateTime.parse("2026-09-22T19:30")
        val all = plan(fixed(evening), mapOf("20:00" to false), now)

        assertTrue(all.none { it.momentId == "20:00" })
        assertEquals("21:00", all.first().momentId)
    }

    @Test
    fun `a moment switched on overrides an off-by-default protocol entry`() {
        val lunch = listOf(moment("12:00", "Lunch, first food", onByDefault = false))
        val now = LocalDateTime.parse("2026-09-22T08:00")

        assertTrue(plan(fixed(lunch), emptyMap(), now).isEmpty())
        assertEquals(1, plan(fixed(lunch), mapOf("12:00" to true), now).size)
    }

    @Test
    fun `the lights-out reminder carries tonight's time, since the ramp moves it`() {
        val tonight = moment("22:45", "Lights out", lightsOut = true, id = "21:30")
        val at = LocalDateTime.parse("2026-09-22T22:45")

        val reminder = reminderFor(tonight, at)
        assertEquals("Lights out", reminder.title)
        assertTrue(reminder.body, reminder.body.contains("22:45"))
    }

    @Test
    fun `a missed moment fires at once rather than at a negative delay`() {
        val now = LocalDateTime.parse("2026-09-22T21:05")
        val past = LocalDateTime.parse("2026-09-22T21:00")

        assertEquals(Duration.ZERO, delayUntil(past, now))
    }

    @Test
    fun `nothing switched on plans nothing at all`() {
        val now = LocalDateTime.parse("2026-09-22T19:30")
        val allOff = evening.associate { it.id to false }

        assertTrue(plan(fixed(evening), allOff, now).isEmpty())
    }
}
