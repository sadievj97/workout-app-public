package com.cutcoach.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cutcoach.rules.Recommendation
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.CutIcon
import com.cutcoach.ui.components.CutIconView
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SlateEdge
import com.cutcoach.ui.theme.SunriseAmber

/**
 * What the rules engine makes of today, on the Today screen.
 *
 * Every line carries its reason underneath, because the protocol asks for one:
 * a suggestion without its evidence is an instruction, and the owner decides,
 * not the app.
 *
 * A pain report replaces the card entirely rather than being listed among other
 * advice. The `pain_stop` guardrail is not one item in a list.
 */
@Composable
fun AdviceCard(
    recommendations: List<Recommendation>,
    modifier: Modifier = Modifier,
    /** Tapping a suggestion about one exercise opens its cue card. */
    onOpenExercise: (String) -> Unit = {},
) {
    if (recommendations.isEmpty()) return

    val stop = recommendations.firstOrNull { it.kind == Recommendation.Kind.PAIN }
    if (stop != null) {
        CutCard(background = SlateEdge, modifier = modifier) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CutIconView(CutIcon.ALERT, SunriseAmber, size = 26.dp)
                Text(
                    stop.headline,
                    style = MaterialTheme.typography.titleLarge,
                    color = SunriseAmber,
                )
            }
            Text(stop.reason, style = MaterialTheme.typography.bodyMedium, color = Mist)
        }
        return
    }

    // Long days produce long lists. The first few carry the day; the rest are
    // there when wanted rather than filling the screen at 05:35.
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) recommendations else recommendations.take(SHOWN_COLLAPSED)
    val hidden = recommendations.size - shown.size

    CutCard(modifier = modifier) {
        SectionLabel("What this suggests")
        shown.forEachIndexed { index, recommendation ->
            if (index > 0) Box(Modifier.height(10.dp))
            AdviceRow(recommendation, onOpenExercise)
        }

        if (hidden > 0 || expanded) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) "Show fewer" else "$hidden more",
                    style = MaterialTheme.typography.labelMedium,
                    color = CoolSky,
                )
            }
        }

        Text(
            "Suggestions, not instructions. Nothing here changes your session on its own.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
    }
}

/** Enough to carry the morning without becoming the screen. */
private const val SHOWN_COLLAPSED = 4

/** Which icon says what this suggestion is about. */
private fun iconFor(kind: Recommendation.Kind): CutIcon = when (kind) {
    Recommendation.Kind.PROGRESSION -> CutIcon.LADDER
    Recommendation.Kind.DELOAD -> CutIcon.BARBELL
    Recommendation.Kind.PLATEAU -> CutIcon.FOOTSTEPS
    Recommendation.Kind.WEIGHT -> CutIcon.SCALE
    Recommendation.Kind.SLEEP -> CutIcon.MOON
    Recommendation.Kind.HEART_RATE -> CutIcon.HEART
    Recommendation.Kind.STEPS -> CutIcon.FOOTSTEPS
    Recommendation.Kind.READINESS -> CutIcon.SUNRISE
    Recommendation.Kind.PAIN -> CutIcon.ALERT
    Recommendation.Kind.CHECK_IN -> CutIcon.QUESTION
}

@Composable
private fun AdviceRow(
    recommendation: Recommendation,
    onOpenExercise: (String) -> Unit,
) {
    val accent = when (recommendation.tone) {
        Recommendation.Tone.STOP, Recommendation.Tone.ATTENTION -> SunriseAmber
        Recommendation.Tone.INFO -> CoolSky
    }
    val opensSomething = recommendation.exerciseId != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (opensSomething) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenExercise(recommendation.exerciseId!!) }
                        .heightIn(min = 56.dp)
                        .padding(vertical = 4.dp)
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CutIconView(iconFor(recommendation.kind), accent, size = 22.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                recommendation.headline,
                style = MaterialTheme.typography.bodyLarge,
                color = if (recommendation.tone == Recommendation.Tone.INFO) Mist else accent,
            )
            // The one-line reason the protocol requires.
            Text(
                recommendation.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
            )
            if (opensSomething) {
                Text(
                    "Tap for the cue card",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoolSky,
                )
            }
        }
    }
}
