package com.cutcoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cutcoach.R

/**
 * Atkinson Hyperlegible throughout, chosen because its letterforms stay apart
 * from each other when you are tired: the 1, l and I are distinct, as are the
 * 0 and O.
 */
val AtkinsonHyperlegible = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
    Font(R.font.atkinson_hyperlegible_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.atkinson_hyperlegible_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

/**
 * Tabular figures. Every digit takes the same width, so a counting timer does
 * not jitter as 1s and 8s swap places, and a column of weights lines up.
 *
 * The font ships the OpenType "tnum" feature, which is what makes this work;
 * without it the setting would be silently ignored.
 */
const val TABULAR_FIGURES = "tnum"

/** Any style carrying a number should be built through this. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR_FIGURES)

private val Base = TextStyle(fontFamily = AtkinsonHyperlegible)

val CutCoachTypography = Typography(
    // Reserved for the session timer. Big enough to read with the phone on the
    // floor and you standing over it.
    displayLarge = Base.copy(
        fontSize = 96.sp,
        lineHeight = 100.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    displayMedium = Base.copy(fontSize = 56.sp, lineHeight = 62.sp, fontWeight = FontWeight.Bold),
    displaySmall = Base.copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold),

    headlineMedium = Base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = Base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),

    titleLarge = Base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = Base.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),

    // Body text is a notch larger than the Material default, for the same
    // reason as the typeface choice.
    bodyLarge = Base.copy(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = Base.copy(fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = Base.copy(fontSize = 14.sp, lineHeight = 20.sp),

    labelLarge = Base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    labelMedium = Base.copy(fontSize = 14.sp, lineHeight = 19.sp),
    labelSmall = Base.copy(fontSize = 12.sp, lineHeight = 16.sp),
)
