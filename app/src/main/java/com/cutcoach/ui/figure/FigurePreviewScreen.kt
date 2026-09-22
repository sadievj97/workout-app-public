package com.cutcoach.ui.figure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cutcoach.data.poses.PoseLoad
import com.cutcoach.data.poses.PoseRepository
import com.cutcoach.data.poses.Tempo
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SunriseAmber

/**
 * A temporary screen for reviewing the first three figures before the other
 * twelve are drawn.
 *
 * It is replaced by the Form check screen once all fifteen exist, which adds
 * the "Looks right" and "Needs fixing" verdicts and the note field.
 */
@Composable
fun FigurePreviewScreen(
    repo: ProtocolRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val load = remember { PoseRepository.load(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("Back", style = MaterialTheme.typography.labelMedium, color = CoolSky)
        }

        Text("Figure preview", style = MaterialTheme.typography.displaySmall, color = Mist)

        when (load) {
            is PoseLoad.Failure -> Text(
                load.message,
                style = MaterialTheme.typography.bodyMedium,
                color = SunriseAmber,
            )

            is PoseLoad.Success -> {
                val library = load.library
                val missing = library.missingFrom(repo.library.exercises.map { it.id })

                Text(
                    "The first three, for approval before the other twelve are drawn. " +
                        "Tap a tempo to see how a ladder step changes the movement.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MistDim,
                )

                library.exercises.forEach { (id, animation) ->
                    var tempo by remember(id) { mutableStateOf(Tempo.Default) }
                    var turn by remember(id) { mutableStateOf(0f) }
                    var tilt by remember(id) { mutableStateOf(0f) }

                    CutCard {
                        SectionLabel(repo.exercise(id)?.name ?: id)
                        Text(
                            repo.exercise(id)?.muscles?.joinToString(", ").orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = SunriseAmber,
                        )

                        AnimatedStickFigure(
                            animation = animation,
                            style = library.figure,
                            tempo = tempo,
                            muscles = repo.exercise(id)?.muscles.orEmpty(),
                            library = library,
                            extraAzimuth = turn,
                            extraElevation = tilt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                // Drag to look at the movement from elsewhere. The
                                // poses are three-dimensional, so this is a camera
                                // move rather than a different set of drawings.
                                .pointerInput(id) {
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
                                    color = CoolSky,
                                )
                            }
                        }

                        if (!animation.isStaticHold) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TempoChip("Normal", tempo == Tempo.Default) {
                                    tempo = Tempo.Default
                                }
                                TempoChip(
                                    "3s lowering",
                                    tempo.lowerSeconds != null,
                                ) { tempo = Tempo.fromLadderStep("3-second lowering") }
                                TempoChip("Pause", tempo.pauseSeconds > 0f) {
                                    tempo = Tempo.fromLadderStep("Pause 1 second at the top")
                                }
                            }
                        } else {
                            Text(
                                "A timed hold: one position, with a countdown ring in the player.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MistDim,
                            )
                        }

                        Text(
                            "Camera at ${animation.camera.azimuth.toInt()} degrees round " +
                                "and ${animation.camera.elevation.toInt()} up. Drag the " +
                                "figure to look from somewhere else.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MistDim,
                        )
                    }
                }

                CutCard {
                    SectionLabel("Still to draw")
                    Text(
                        missing.joinToString(", ") { repo.exercise(it)?.name ?: it },
                        style = MaterialTheme.typography.bodySmall,
                        color = MistDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun TempoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) SunriseAmber else MistDim,
        )
    }
}
