package com.cutcoach.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette from the design direction.
 *
 * The app is used half-awake at 05:35 in a dim room, so it is dark by default
 * and there is no light variant. Amber and sky are not decoration: during a
 * session they carry meaning, amber for work and sky for rest, so the phase is
 * readable from across the floor without reading a word.
 */

/** Background. Dark enough not to hurt at 05:35, not so dark it smears on OLED. */
val PreDawnNavy = Color(0xFF101B2D)

/** Cards and raised surfaces, one step up from the background. */
val Slate = Color(0xFF1D2B42)

/** Primary text. Deliberately not pure white, which glares in a dark room. */
val Mist = Color(0xFFD5DDE8)

/** Work phases: the timer while a set or hold is running. */
val SunriseAmber = Color(0xFFF2A541)

/** Rest phases: the rest timer and the switch-sides countdown. */
val CoolSky = Color(0xFF7FB2E5)

/** Secondary text: labels, units, and anything that must not compete. */
val MistDim = Color(0xB3D5DDE8)

/** Borders and dividers. Visible, never loud. */
val SlateEdge = Color(0xFF2C3E5C)
