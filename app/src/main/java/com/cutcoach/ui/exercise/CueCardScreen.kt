package com.cutcoach.ui.exercise

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.cutcoach.data.poses.PoseLoad
import com.cutcoach.data.poses.PoseRepository
import com.cutcoach.data.poses.Tempo
import com.cutcoach.data.protocol.Exercise
import com.cutcoach.ui.claude.HarderCard
import com.cutcoach.ui.claude.SubstituteCard
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.HeroCard
import com.cutcoach.ui.components.Pill
import com.cutcoach.ui.figure.AnimatedStickFigure
import com.cutcoach.ui.figure.FigureRender
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.identityFor
import com.cutcoach.ui.theme.Sky
import com.cutcoach.ui.theme.TextHigh
import com.cutcoach.ui.theme.TextMid
import com.cutcoach.ui.theme.SurfaceEdge
import com.cutcoach.ui.theme.Ember

/**
 * How to do one exercise: setup, cues, what usually goes wrong, the easier
 * version, and where you are on its ladder.
 *
 * Opened from the player mid-set, so the useful part is at the top and nothing
 * needs scrolling to answer "am I doing this right".
 */
@Composable
fun CueCardScreen(
    repo: com.cutcoach.data.protocol.ProtocolRepository,
    exercise: Exercise,
    currentLadderStep: Int,
    /** Tapping a rung says where you actually are, which sets tempo and difficulty. */
    onLadderStepChange: (Int) -> Unit,
    /** The link to use: a Settings override if there is one, otherwise the library's. */
    videoUrl: String,
    /** The seconds of that clip worth looping. */
    videoStartS: Float,
    videoEndS: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasVideo = videoUrl.isNotBlank()
    val videoId = remember(videoUrl) { youTubeVideoId(videoUrl) }
    // Checked once, on opening. Everything else here works offline, so an
    // unreachable clip should cost nothing rather than leave a blank box.
    var videoPlayable by remember(videoUrl) {
        mutableStateOf(videoId != null && context.hasNetwork())
    }
    val poses = remember { PoseRepository.load(context) as? PoseLoad.Success }
    val animation = remember(exercise.id, currentLadderStep, poses) {
        poses?.library?.animationFor(exercise.id, currentLadderStep)
    }
    // Whether this rung is drawn as itself, or as the base movement.
    val drawnAtRung = poses?.library?.isDrawnAtRung(exercise.id, currentLadderStep) ?: true
    val tempo = remember(exercise.id, currentLadderStep) {
        Tempo.fromLadderStep(exercise.ladder.getOrNull(currentLadderStep))
    }

    // The same derivation the session cards use, fed the exercise's movement
    // pattern instead of a session's focus — so a pull-up here is the same
    // violet as the pull session it belongs to, without either knowing about
    // the other.
    val identity = remember(exercise.id) {
        identityFor(
            id = exercise.id,
            name = exercise.name,
            focus = exercise.pattern,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("Back", style = MaterialTheme.typography.labelMedium, color = Sky)
        }

        // The name over the movement's own colours rather than over the
        // background. Seven identical grey panels was the whole problem with
        // this screen: nothing led, so everything had to be read in order.
        HeroCard(brush = identity.sweep()) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                ),
                color = Color.White,
            )
            Text(
                exercise.pattern,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        // A real clip if there is one and it will play; the figure otherwise.
        // The figure is always there underneath, which is what makes the
        // fallback honest rather than a hole in the screen.
        if (hasVideo && videoPlayable && videoId != null) {
            CutCard {
                SectionLabel("Demo")
                VideoLoop(
                    videoId = videoId,
                    startSeconds = videoStartS,
                    endSeconds = videoEndS,
                    onUnavailable = { videoPlayable = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
                Text(
                    if (videoEndS > videoStartS) {
                        "Looping ${videoStartS.toInt()}s to ${videoEndS.toInt()}s, muted."
                    } else {
                        "Playing muted. Set a start and end in Settings to loop a segment."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                )
            }
        }

        // The movement, large, before any words about it.
        if (animation != null && poses != null) {
            CutCard {
                AnimatedStickFigure(
                    animation = animation,
                    style = poses.library.figure,
                    tempo = tempo,
                    library = poses.library,
                    render = FigureRender.LINES,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                )

                Text(
                    "A reminder of the shape and the tempo. Not a substitute for " +
                        "learning the form from a video or a coach.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                )
            }
        }

        // Safety first where the setup itself can hurt you.
        exercise.safety?.let {
            CutCard(background = SurfaceEdge) {
                SectionLabel("Before your first set")
                Text(it, style = MaterialTheme.typography.bodyLarge, color = Ember)
            }
        }

        CutCard {
            SectionLabel("Setup")
            Text(exercise.setup, style = MaterialTheme.typography.bodyLarge, color = TextHigh)
        }

        CutCard {
            SectionLabel("Cues")
            exercise.cues.forEach { Bullet(it, Ember) }
        }

        CutCard {
            SectionLabel("Common mistakes")
            exercise.commonMistakes.forEach { Bullet(it, TextMid) }
        }

        CutCard {
            SectionLabel("If it is too hard today")
            Text(exercise.easier, style = MaterialTheme.typography.bodyLarge, color = TextHigh)
        }

        CutCard {
            SectionLabel("The ladder")
            exercise.ladder.forEachIndexed { index, rung ->
                LadderRung(
                    text = rung,
                    number = index + 1,
                    isCurrent = index == currentLadderStep,
                    isDone = index < currentLadderStep,
                    onSelect = { onLadderStepChange(index) },
                )
            }
            Text(
                "Tap the rung you are actually on. It sets the difficulty and the " +
                    "tempo; it changes nothing about the sets or the schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
            // Only said when it is true. A rung that only changes tempo or adds
            // load is drawn correctly by the base figure; one that changes body
            // position needs its own drawing, and some are not authored yet.
            if (!drawnAtRung) {
                Text(
                    "This rung is not drawn yet, so the figure above shows the base " +
                        "movement. Its tempo is still applied.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ember,
                )
            }
            exercise.ladderEndNote?.let {
                Box(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextMid)
            }
        }

        // The Claude layer: a swap when today will not allow this, and a
        // harder version when the ladder has nothing left.
        SubstituteCard(repo = repo, exerciseId = exercise.id)
        HarderCard(repo = repo, exerciseId = exercise.id, currentRung = currentLadderStep)

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
                    containerColor = Sky,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Text(
                    if (videoPlayable) "Open in YouTube" else "Open the demo in YouTube",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!videoPlayable) {
                Text(
                    "The clip would not play here, so the figure above is standing in. " +
                        "Offline, or the video does not allow embedding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                )
            }
        } else {
            // No link yet. Said plainly rather than shown as a dead button.
            Text(
                "No demo video linked yet. Add one in Settings when you find a clip " +
                    "you trust.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
        }
    }
}

/**
 * Names what the amber is marking, so the shading is read rather than guessed
 * at. The names come straight from the exercise library.
 */

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
        Text(text, style = MaterialTheme.typography.bodyLarge, color = TextHigh)
    }
}

@Composable
private fun LadderRung(
    text: String,
    number: Int,
    isCurrent: Boolean,
    isDone: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            // 56 dp, as the brief asks for anything tapped with one hand.
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) Ember else TextMid,
        )
        Column {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isCurrent -> Ember
                    isDone -> TextMid
                    else -> TextHigh
                },
            )
            if (isCurrent) {
                Text(
                    "You are here",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ember,
                )
            }
        }
    }
}
