package com.cutcoach.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A session's own artwork, derived rather than declared.
 *
 * Every session gets a three-stop gradient that is always the same for that
 * session, so a card is recognisable before a word of it is read.
 *
 * What it reads matters. The first version matched on the session's name and
 * focus, which put "Wall tennis or hill walk" — a Zone 2 cardio day — into the
 * recovery bucket, because the word "walk" is in it. Prose is the least
 * reliable thing about a session. The id and `hr_zone` are structural: a Zone 2
 * day says `zone2` in both, whatever its name happens to describe that week.
 * So the id and zone are matched first and the prose is a fallback.
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
 * Order is the whole design here, because these overlap on purpose. "Easy
 * walk, rehab only" and "hill walk" both say walk; rehab is tested first, so
 * the rehab day takes it and the cardio day falls through to its own zone.
 * "Hill repeats" is tested before anything cardio, so the hard day does not
 * end up the same colour as the steady one.
 */
private val BY_KEYWORD: List<Pair<List<String>, SessionIdentity>> = listOf(
    listOf("rehab", "recovery", "mobility") to RECOVER,
    listOf("interval", "sprint", "hiit", "repeat") to INTERVAL,
    listOf("zone2", "zone 2", "cardio", "walk", "tennis", "hill", "run", "cycle", "rowing")
        to CARDIO,
    listOf("pull", "back", "chin", "lat") to PULL,
    listOf("push", "press", "chest", "shoulder") to PUSH,
    listOf("full body", "full-body", "total body") to LEGS,
    listOf("leg", "squat", "hinge", "glute", "lower") to LEGS,
)

/**
 * The identity for one session.
 *
 * [id] and [hrZone] are structural and are matched first. [name] and [focus]
 * are prose and are matched only if those say nothing. If none of it is
 * recognised, the id picks consistently — the same id always lands on the same
 * colours, across launches and devices, because it hashes the string itself
 * rather than relying on Object.hashCode.
 */
fun identityFor(
    id: String,
    name: String? = null,
    focus: String? = null,
    hrZone: String? = null,
): SessionIdentity {
    val structural = listOfNotNull(id, hrZone).joinToString(" ").lowercase()
    BY_KEYWORD.forEach { (words, identity) ->
        if (words.any { structural.contains(it) }) return identity
    }

    val prose = listOfNotNull(focus, name).joinToString(" ").lowercase()
    BY_KEYWORD.forEach { (words, identity) ->
        if (words.any { prose.contains(it) }) return identity
    }

    // A stable, content-derived index. String.hashCode is specified by the
    // language, unlike Object.hashCode, so this does not drift between runs.
    val index = (id.hashCode().toLong() and 0xFFFFFFFFL) % WHEEL.size
    return WHEEL[index.toInt()]
}
