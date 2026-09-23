package com.cutcoach.ui.figure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.cutcoach.data.poses.Camera
import com.cutcoach.data.poses.PoseLibrary
import com.cutcoach.data.poses.Symmetry
import com.cutcoach.data.poses.Vec3
import com.cutcoach.ui.theme.TextLow

/**
 * Which muscles an exercise works, drawn on a neutral body.
 *
 * The exercise's own figure is drawn from whatever angle shows the movement
 * best, and that is frequently the wrong angle for its muscles — a push-up is
 * drawn from the side, so the chest it works faces away from the viewer and
 * cannot be seen going amber. This stands beside it facing forward, doing
 * nothing, with the worked muscles lit.
 *
 * It is one still pose. Animating it would compete with the movement next to
 * it, and there is nothing to animate: it is an anatomy plate.
 *
 * Draws nothing at all if poses.json carries no reference body, rather than
 * leaving a gap where a figure should be.
 */
@Composable
fun MuscleMap(
    library: PoseLibrary,
    muscles: List<String>,
    modifier: Modifier = Modifier,
    showCaption: Boolean = true,
) {
    val body = library.anatomy.referenceBody ?: return
    if (muscles.isEmpty()) return

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StickFigure(
            pose = body.pose,
            style = library.figure,
            camera = Camera(body.camera.azimuth, body.camera.elevation),
            symmetry = Symmetry.parse(body.symmetry),
            modifier = Modifier.fillMaxWidth(),
            // Always on: the whole purpose of this figure is the highlight.
            highlightWorking = true,
            render = FigureRender.SOLID,
            workedMuscles = muscles,
            anatomy = library.anatomy,
            facing = Vec3.of(body.facing),
        )

        if (showCaption) {
            Text(
                "Worked",
                style = MaterialTheme.typography.labelSmall,
                color = TextLow,
                textAlign = TextAlign.Center,
            )
        }
    }
}

