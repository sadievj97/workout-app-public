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
 * Dynamic colour is deliberately off: amber and sky carry meaning during a
 * session, so the system wallpaper must not be allowed to repaint them.
 */
private val CutCoachColors = darkColorScheme(
    primary = SunriseAmber,
    onPrimary = PreDawnNavy,
    secondary = CoolSky,
    onSecondary = PreDawnNavy,
    background = PreDawnNavy,
    onBackground = Mist,
    surface = Slate,
    onSurface = Mist,
    surfaceVariant = Slate,
    onSurfaceVariant = MistDim,
    outline = SlateEdge,
    error = SunriseAmber,
    onError = PreDawnNavy,
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
