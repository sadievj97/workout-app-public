package com.cutcoach.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cutcoach.rules.Recommendation
import com.cutcoach.ui.components.CutCard
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
) {
    if (recommendations.isEmpty()) return

    val stop = recommendations.firstOrNull { it.kind == Recommendation.Kind.PAIN }
    if (stop != null) {
        CutCard(background = SlateEdge, modifier = modifier) {
            SectionLabel("Stop")
            Text(stop.headline, style = MaterialTheme.typography.titleLarge, color = SunriseAmber)
            Text(stop.reason, style = MaterialTheme.typography.bodyMedium, color = Mist)
        }
        return
    }

    CutCard(modifier = modifier) {
        SectionLabel("What this suggests")
        recommendations.forEachIndexed { index, recommendation ->
            if (index > 0) Box(Modifier.height(10.dp))
            AdviceRow(recommendation)
        }
        Box(Modifier.height(4.dp))
        Text(
            "Suggestions, not instructions. Nothing here changes your session on its own.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
    }
}

@Composable
private fun AdviceRow(recommendation: Recommendation) {
    val accent = when (recommendation.tone) {
        Recommendation.Tone.STOP -> SunriseAmber
        Recommendation.Tone.ATTENTION -> SunriseAmber
        Recommendation.Tone.INFO -> CoolSky
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent),
        )
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
        }
    }
}
