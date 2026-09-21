package com.cutcoach.ui.exercise

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.cutcoach.data.protocol.Exercise
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SlateEdge
import com.cutcoach.ui.theme.SunriseAmber

/**
 * How to do one exercise: setup, cues, what usually goes wrong, the easier
 * version, and where you are on its ladder.
 *
 * Opened from the player mid-set, so the useful part is at the top and nothing
 * needs scrolling to answer "am I doing this right".
 */
@Composable
fun CueCardScreen(
    exercise: Exercise,
    currentLadderStep: Int,
    /** The link to use: a Settings override if there is one, otherwise the library's. */
    videoUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasVideo = videoUrl.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("Back", style = MaterialTheme.typography.labelMedium, color = CoolSky)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.displaySmall, color = Mist)
            Text(
                "${exercise.pattern}  -  ${exercise.muscles.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
            )
        }

        // Safety first where the setup itself can hurt you.
        exercise.safety?.let {
            CutCard(background = SlateEdge) {
                SectionLabel("Before your first set")
                Text(it, style = MaterialTheme.typography.bodyLarge, color = SunriseAmber)
            }
        }

        CutCard {
            SectionLabel("Setup")
            Text(exercise.setup, style = MaterialTheme.typography.bodyLarge, color = Mist)
        }

        CutCard {
            SectionLabel("Cues")
            exercise.cues.forEach { Bullet(it, SunriseAmber) }
        }

        CutCard {
            SectionLabel("Common mistakes")
            exercise.commonMistakes.forEach { Bullet(it, MistDim) }
        }

        CutCard {
            SectionLabel("If it is too hard today")
            Text(exercise.easier, style = MaterialTheme.typography.bodyLarge, color = Mist)
        }

        CutCard {
            SectionLabel("The ladder")
            exercise.ladder.forEachIndexed { index, rung ->
                LadderRung(
                    text = rung,
                    number = index + 1,
                    isCurrent = index == currentLadderStep,
                    isDone = index < currentLadderStep,
                )
            }
            exercise.ladderEndNote?.let {
                Box(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MistDim)
            }
        }

        if (hasVideo) {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, videoUrl.toUri())
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoolSky,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Text("Watch the demo", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            // No link yet. Said plainly rather than shown as a dead button.
            Text(
                "No demo video linked yet. Add one in Settings when you find a clip " +
                    "you trust.",
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
            )
        }
    }
}

@Composable
private fun Bullet(text: String, dotColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .padding(top = 9.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Mist)
    }
}

@Composable
private fun LadderRung(text: String, number: Int, isCurrent: Boolean, isDone: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) SunriseAmber else MistDim,
        )
        Column {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isCurrent -> SunriseAmber
                    isDone -> MistDim
                    else -> Mist
                },
            )
            if (isCurrent) {
                Text(
                    "You are here",
                    style = MaterialTheme.typography.labelSmall,
                    color = SunriseAmber,
                )
            }
        }
    }
}
