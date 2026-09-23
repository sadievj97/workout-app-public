package com.cutcoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * There is one colour scheme and it is dark. The app is not built to be read in
 * daylight, and a light variant would be a second thing to keep correct for no
 * benefit to the one person using it.
 *
 * Dynamic colour is deliberately off. Warm means work and cool means rest
 * during a session, and the system wallpaper must not be allowed to repaint
 * that.
 *
 * Material's own surface roles are set to opaque values here even though most
 * cards in the app use a translucent fill instead. Anything Material draws for
 * itself — a menu, a dialog, a sheet — has no artwork behind it to pick up, and
 * translucency there just looks like a rendering fault.
 */
private val CutCoachColors = darkColorScheme(
    primary = Accent,
    onPrimary = Ink,
    secondary = Sky,
    onSecondary = Ink,
    tertiary = Ember,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextHigh,
    surface = InkLift,
    onSurface = TextHigh,
    surfaceVariant = InkLift,
    onSurfaceVariant = TextMid,
    outline = SurfaceEdge,
    outlineVariant = SurfaceEdge,
    error = Amber,
    onError = Ink,
)

@Composable
fun CutCoachTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CutCoachColors,
        typography = CutCoachTypography,
        content = content,
    )
}
