package com.cutcoach.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A session's own artwork, derived rather than declared.
 *
 * Every session gets a three-stop gradient that is always the same for that
 * session, so the Today card is recognisable before a word of it is read —
 * pushing is hot, pulling is electric, steady cardio is cool, the recovery day
 * is barely coloured.
 *
 * The mapping reads the session's focus and name, which come from
 * protocol.json, rather than listing session ids here. A session id belongs to
 * the protocol file; if the owner renames `strength_a` or adds a fourth
 * strength day, no Kotlin should need editing for it to look right. Anything
 * the keywords do not recognise falls through to a stable hash, so a brand new
 * session still gets a consistent identity instead of a default grey.
 *
 * Nothing here is an image file. The artwork is three colours and a direction,
 * which is what keeps the repository free of licensed assets.
 */
data class SessionIdentity(
    val start: Color,
    val middle: Color,
    val end: Color,
) {
    /** Corner to corner, the way the hero card wants it. */
    fun sweep(): Brush = Brush.linearGradient(
        colors = listOf(start, middle, end),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

    /** A dimmer version, for a row in a list rather than a hero. */
    fun muted(): Brush = Brush.linearGradient(
        listOf(start.copy(alpha = 0.55f), end.copy(alpha = 0.35f)),
    )

    /** One colour to tint an icon or a border with. */
    val accent: Color get() = middle
}

private val PUSH = SessionIdentity(Identity.PushA, Identity.PushB, Identity.PushC)
private val PULL = SessionIdentity(Identity.PullA, Identity.PullB, Identity.PullC)
private val LEGS = SessionIdentity(Identity.LegsA, Identity.LegsB, Identity.LegsC)
private val CARDIO = SessionIdentity(Identity.CardioA, Identity.CardioB, Identity.CardioC)
private val INTERVAL = SessionIdentity(Identity.IntervalA, Identity.IntervalB, Identity.IntervalC)
private val RECOVER = SessionIdentity(Identity.RecoverA, Identity.RecoverB, Identity.RecoverC)

/** The fallback ring, in the order a hash will walk it. */
private val WHEEL = listOf(PUSH, PULL, CARDIO, LEGS, INTERVAL, RECOVER)

/**
 * Keywords in the order they are tested, most specific first.
 *
 * "Interval" is checked before "cardio" because an interval session's focus
 * usually says both, and the harder of the two should win the colour.
 */
private val BY_KEYWORD: List<Pair<List<String>, SessionIdentity>> = listOf(
    listOf("interval", "sprint", "hiit") to INTERVAL,
    listOf("walk", "rehab", "recovery", "mobility", "easy") to RECOVER,
    listOf("zone 2", "zone2", "cardio", "run", "cycle", "row") to CARDIO,
    listOf("pull", "back", "row", "chin", "lat") to PULL,
    listOf("leg", "squat", "hinge", "glute", "lower") to LEGS,
    listOf("push", "press", "chest", "upper", "shoulder") to PUSH,
)

/**
 * The identity for one session.
 *
 * [id] is only used when nothing in [focus] or [name] is recognised, and then
 * only to pick consistently — the same id always lands on the same colours,
 * across launches and across devices, because it hashes the string itself
 * rather than relying on Object.hashCode.
 */
fun identityFor(id: String, name: String? = null, focus: String? = null): SessionIdentity {
    val haystack = listOfNotNull(focus, name).joinToString(" ").lowercase()

    BY_KEYWORD.forEach { (words, identity) ->
        if (words.any { haystack.contains(it) }) return identity
    }

    // A stable, content-derived index. String.hashCode is specified by the
    // language, unlike Object.hashCode, so this does not drift between runs.
    val index = (id.hashCode().toLong() and 0xFFFFFFFFL) % WHEEL.size
    return WHEEL[index.toInt()]
}
