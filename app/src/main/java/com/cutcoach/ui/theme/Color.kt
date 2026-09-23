package com.cutcoach.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * Still dark, because the app is still opened half-awake at 05:35 in a dim
 * room and that constraint has not changed. What changed is everything else:
 * the old navy-and-amber scheme read as a utility, and a utility is not what
 * gets opened at 05:35 on a morning you would rather not.
 *
 * The base is near-black with a violet cast rather than a blue one. Violet
 * sits under the whole accent range used here — magenta, orange, teal — so
 * those colours look like they belong on the surface instead of sitting on
 * top of it. A blue-navy base fights orange, which is why the old amber cards
 * always looked slightly pasted on.
 *
 * Meaning is preserved even though the values changed: warm still means work,
 * cool still means rest. That mapping is load-bearing during a session, when
 * the phase has to be readable from across the floor without reading a word.
 */

// ---- Base surfaces ------------------------------------------------------

/** The background. Deep enough for OLED to switch pixels off, not flat black. */
val Ink = Color(0xFF0B0A12)

/** One step up: the bar behind navigation, sheets, pressed states. */
val InkLift = Color(0xFF13111F)

/**
 * Card fills are translucent white over [Ink] rather than their own opaque
 * colour, so a card laid over artwork picks the artwork up instead of punching
 * a hole in it. This is the plain-card value.
 */
val Surface = Color(0x0FFFFFFF)

/** A card that needs to separate more: a warning, a sheet, a pressed row. */
val SurfaceStrong = Color(0x1AFFFFFF)

/** Hairline borders. Present, never loud. */
val SurfaceEdge = Color(0x1FFFFFFF)

// ---- Text ---------------------------------------------------------------

/** Primary text. Warm white, not pure white, which glares in a dark room. */
val TextHigh = Color(0xFFF4F1FB)

/** Secondary: labels, units, anything that must not compete with a number. */
val TextMid = Color(0xFFA9A2C4)

/** Tertiary: captions, the small print under a value, disabled states. */
val TextLow = Color(0xFF6E6789)

// ---- Meaning ------------------------------------------------------------

/**
 * Work phases: a set or a hold running. Warmer and hotter than the old amber,
 * which had gone grey-gold against a navy background.
 */
val Ember = Color(0xFFFF6B35)

/** Rest phases: the rest timer and the switch-sides countdown. */
val Sky = Color(0xFF38BDF8)

/** A target met, a streak held, a number inside its range. */
val Mint = Color(0xFF34D399)

/** Attention without alarm: off target, late to bed, missing data. */
val Amber = Color(0xFFFBBF24)

/** The primary action, and the one colour allowed to shout. */
val Accent = Color(0xFFA855F7)

// ---- Session identity ---------------------------------------------------

/**
 * Each session gets a gradient of its own, so the Today card is recognisable
 * before it is read. See [com.cutcoach.ui.theme.SessionArt] for how one is
 * chosen; these are the ends it picks between.
 *
 * They are ordered warm to cool on purpose, roughly matching effort: pushing
 * is hot, pulling is electric, steady cardio is cool, and the recovery day is
 * barely coloured at all.
 */
object Identity {
    val PushA = Color(0xFF7C3AED)
    val PushB = Color(0xFFDB2777)
    val PushC = Color(0xFFF97316)

    val PullA = Color(0xFF1D4ED8)
    val PullB = Color(0xFF7C3AED)
    val PullC = Color(0xFFC026D3)

    val LegsA = Color(0xFFB91C1C)
    val LegsB = Color(0xFFEA580C)
    val LegsC = Color(0xFFF59E0B)

    val CardioA = Color(0xFF0E7490)
    val CardioB = Color(0xFF0D9488)
    val CardioC = Color(0xFF22C55E)

    val IntervalA = Color(0xFF9F1239)
    val IntervalB = Color(0xFFDC2626)
    val IntervalC = Color(0xFFF97316)

    val RecoverA = Color(0xFF1E293B)
    val RecoverB = Color(0xFF115E59)
    val RecoverC = Color(0xFF0E7490)
}
