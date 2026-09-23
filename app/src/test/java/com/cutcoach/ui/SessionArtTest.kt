package com.cutcoach.ui

import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.ui.theme.identityFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Every session gets its own colour, and no two of them collide.
 *
 * This exists because the first version got it wrong in a way that only showed
 * up on the phone: the Zone 2 day is called "Wall tennis or hill walk", the
 * matcher saw "walk", and a cardio session was painted in the recovery day's
 * colours. Nothing failed. It just looked wrong, and looking wrong is not
 * something the other tests can see.
 *
 * So this asserts on the thing a screenshot would have shown.
 */
class SessionArtTest {

    private lateinit var repo: ProtocolRepository

    private fun asset(name: String) = File("src/main/assets/$name").readText()

    @Before
    fun setUp() {
        repo = (ProtocolRepository.parse(asset("protocol.json"), asset("exercises.json"))
            as ProtocolLoad.Success).repository
    }

    private fun identityOf(id: String) = repo.session(id).let { session ->
        identityFor(
            id = id,
            name = session?.name,
            focus = session?.focus,
            hrZone = session?.hrZone,
        )
    }

    @Test
    fun `every session in the protocol gets a distinct identity`() {
        val ids = repo.protocol.sessions.keys.toList()
        assertTrue("No sessions to check", ids.isNotEmpty())

        val identities = ids.associateWith { identityOf(it) }
        val collisions = identities.entries
            .groupBy { it.value }
            .filterValues { it.size > 1 }
            .map { (_, sharing) -> sharing.joinToString(" and ") { it.key } }

        assertTrue(
            "Sessions share a gradient, so the list is not scannable by colour: $collisions",
            collisions.isEmpty(),
        )
    }

    @Test
    fun `a cardio day named after a walk is still a cardio day`() {
        // The actual bug. "Wall tennis or hill walk" contains "walk", and the
        // Sunday recovery session is "Easy walk, rehab only". Prose cannot tell
        // them apart; hr_zone and the id can.
        assertNotEquals(
            "The Zone 2 day was painted as the recovery day",
            identityOf("easy_walk_rehab"),
            identityOf("zone2_cardio"),
        )
    }

    @Test
    fun `the interval day does not look like the steady day`() {
        assertNotEquals(
            "Intervals and Zone 2 share a colour, so the hard day is not visible",
            identityOf("zone2_cardio"),
            identityOf("intervals"),
        )
    }

    @Test
    fun `an identity is stable across calls`() {
        // The hash fallback must land on the same colours every time, or a
        // session would change appearance between launches.
        val unknown = "a_session_the_keywords_do_not_know"
        assertEquals(identityFor(unknown), identityFor(unknown))
    }

    @Test
    fun `an unknown session still gets a real identity rather than a default grey`() {
        val wheel = (1..12).map { identityFor("unknown_session_$it") }.toSet()
        assertTrue(
            "Unknown sessions all landed on one colour, so the hash is not spreading",
            wheel.size > 1,
        )
    }
}
