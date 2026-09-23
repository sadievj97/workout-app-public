package com.cutcoach.ui.figure

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cutcoach.data.poses.PoseLoad
import com.cutcoach.data.poses.PoseRepository
import com.cutcoach.data.poses.Tempo
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.settings.FormReview
import com.cutcoach.data.settings.FormVerdict
import com.cutcoach.data.settings.SettingsRepository
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.Sky
import com.cutcoach.ui.theme.TextHigh
import com.cutcoach.ui.theme.TextMid
import com.cutcoach.ui.theme.SurfaceEdge
import com.cutcoach.ui.theme.Ember
import kotlinx.coroutines.launch

/**
 * Every figure in one place, for the owner to say whether it looks right.
 *
 * A figure is a drawing of a movement made by someone who has never watched
 * the owner do it, so each one stays labelled unreviewed until they say
 * otherwise, and "needs fixing" takes a note explaining what is wrong.
 */
@Composable
fun FormCheckScreen(
    repo: ProtocolRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    val scope = rememberCoroutineScope()
    val load = remember { PoseRepository.load(context) }
    val reviews by settings.formReviews.collectAsStateWithLifecycle(emptyMap())

    val library = (load as? PoseLoad.Success)?.library

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onBack) {
                    Text("Back", style = MaterialTheme.typography.labelMedium, color = Sky)
                }
                Text("Form check", style = MaterialTheme.typography.displaySmall, color = TextHigh)

                val reviewed = repo.library.exercises.count {
                    reviews[it.id]?.verdict != null &&
                        reviews[it.id]?.verdict != FormVerdict.UNREVIEWED
                }
                Text(
                    "$reviewed of ${repo.library.exercises.size} reviewed. Drag a figure to " +
                        "turn it. These are schematic reminders of shape and tempo, not a " +
                        "substitute for learning form from a video or a coach.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                )
            }
        }

        if (library == null) {
            item {
                Text(
                    (load as? PoseLoad.Failure)?.message ?: "No figures loaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ember,
                )
            }
            return@LazyColumn
        }

        items(repo.library.exercises, key = { it.id }) { exercise ->
            FigureReviewCard(
                name = exercise.name,
                muscles = exercise.muscles,
                animation = library.animationFor(exercise.id),
                library = library,
                review = reviews[exercise.id] ?: FormReview(),
                onVerdict = { scope.launch { settings.setFormVerdict(exercise.id, it) } },
                onNote = { scope.launch { settings.setFormNote(exercise.id, it) } },
            )
        }
    }
}

@Composable
private fun FigureReviewCard(
    name: String,
    muscles: List<String>,
    animation: com.cutcoach.data.poses.ExerciseAnimation?,
    library: com.cutcoach.data.poses.PoseLibrary,
    review: FormReview,
    onVerdict: (FormVerdict) -> Unit,
    onNote: (String) -> Unit,
) {
    var turn by remember(name) { mutableStateOf(0f) }
    var tilt by remember(name) { mutableStateOf(0f) }
    var note by remember(review.note) { mutableStateOf(review.note) }

    CutCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(name)
            VerdictLabel(review.verdict)
        }

        Text(
            muscles.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = Ember,
        )

        if (animation == null) {
            Text(
                "No figure for this exercise yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
            )
        } else {
            AnimatedStickFigure(
                animation = animation,
                style = library.figure,
                tempo = Tempo.Default,
                library = library,
                render = FigureRender.LINES,
                extraAzimuth = turn,
                extraElevation = tilt,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .pointerInput(name) {
                        detectDragGestures { _, drag ->
                            turn -= drag.x * 0.4f
                            tilt -= drag.y * 0.3f
                        }
                    },
            )
            if (turn != 0f || tilt != 0f) {
                TextButton(onClick = { turn = 0f; tilt = 0f }) {
                    Text(
                        "Back to the chosen angle",
                        style = MaterialTheme.typography.labelMedium,
                        color = Sky,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VerdictButton(
                label = "Looks right",
                selected = review.verdict == FormVerdict.LOOKS_RIGHT,
                colour = Sky,
                modifier = Modifier.weight(1f),
            ) {
                onVerdict(
                    if (review.verdict == FormVerdict.LOOKS_RIGHT) {
                        FormVerdict.UNREVIEWED
                    } else {
                        FormVerdict.LOOKS_RIGHT
                    },
                )
            }
            VerdictButton(
                label = "Needs fixing",
                selected = review.verdict == FormVerdict.NEEDS_FIXING,
                colour = Ember,
                modifier = Modifier.weight(1f),
            ) {
                onVerdict(
                    if (review.verdict == FormVerdict.NEEDS_FIXING) {
                        FormVerdict.UNREVIEWED
                    } else {
                        FormVerdict.NEEDS_FIXING
                    },
                )
            }
        }

        // The note matters most on a rejection, so it opens with one.
        if (review.verdict == FormVerdict.NEEDS_FIXING || note.isNotBlank()) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("What is wrong", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextHigh,
                    unfocusedTextColor = TextHigh,
                    focusedBorderColor = Ember,
                    unfocusedBorderColor = SurfaceEdge,
                    focusedLabelColor = Ember,
                    unfocusedLabelColor = TextMid,
                    cursorColor = Ember,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (note != review.note) {
                TextButton(onClick = { onNote(note) }) {
                    Text(
                        "Save note",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ember,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerdictLabel(verdict: FormVerdict) {
    val (text, colour) = when (verdict) {
        FormVerdict.UNREVIEWED -> "Unreviewed" to TextMid
        FormVerdict.LOOKS_RIGHT -> "Looks right" to Sky
        FormVerdict.NEEDS_FIXING -> "Needs fixing" to Ember
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = colour)
}

@Composable
private fun VerdictButton(
    label: String,
    selected: Boolean,
    colour: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) colour else SurfaceEdge,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else TextHigh,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

