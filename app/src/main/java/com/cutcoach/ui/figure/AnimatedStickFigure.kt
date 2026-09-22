package com.cutcoach.ui.figure

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import com.cutcoach.data.poses.Camera
import com.cutcoach.data.poses.ExerciseAnimation
import com.cutcoach.data.poses.FigureStyle
import com.cutcoach.data.poses.Tempo
import com.cutcoach.data.poses.timeline
import com.cutcoach.ui.theme.MistDim

/**
 * A stick figure that moves.
 *
 * It walks the timeline built from poses.json and the tempo of the ladder rung
 * you are currently on. If the phone has animations turned off, it shows the
 * two ends of the movement side by side instead, because a figure that cannot
 * move should still say what the movement is.
 */
@Composable
fun AnimatedStickFigure(
    animation: ExerciseAnimation,
    style: FigureStyle,
    modifier: Modifier = Modifier,
    tempo: Tempo = Tempo.Default,
    mirrored: Boolean = false,
    highlightWorking: Boolean = true,
    render: FigureRender = FigureRender.SOLID,
    /** Extra turn on top of the exercise's own camera, from the viewer dragging. */
    extraAzimuth: Float = 0f,
    extraElevation: Float = 0f,
    /** 0 to 1 for a timed hold's countdown ring. Null draws no ring. */
    holdProgress: Float? = null,
) {
    val camera = remember(animation, extraAzimuth, extraElevation) {
        animation.camera.turnedBy(extraAzimuth, extraElevation)
    }
    val context = LocalContext.current
    val reduceMotion = remember { context.prefersReducedMotion() } || LocalInspectionMode.current
    val timeline = remember(animation, tempo) { animation.timeline(tempo) }
    val working = remember(animation) { animation.workingLimbs.toSet() }

    if (timeline.segments.isEmpty()) return

    if (reduceMotion && !timeline.isStatic) {
        ReducedMotionPair(
            animation = animation,
            style = style,
            camera = camera,
            working = working,
            highlightWorking = highlightWorking,
            mirrored = mirrored,
            render = render,
            modifier = modifier,
        )
        return
    }

    val elapsed = remember(timeline) { mutableFloatStateOf(0f) }

    if (!timeline.isStatic) {
        LaunchedEffect(timeline) {
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    if (previous != 0L) {
                        elapsed.floatValue += (now - previous) / 1_000_000_000f
                    }
                    previous = now
                }
            }
        }
    }

    StickFigure(
        pose = timeline.poseAt(elapsed.floatValue),
        style = style,
        camera = camera,
        symmetry = animation.symmetryMode,
        modifier = modifier,
        props = animation.props,
        workingLimbs = working,
        highlightWorking = highlightWorking,
        mirrored = mirrored,
        render = render,
        holdProgress = holdProgress,
    )
}

/**
 * The fallback when the phone's animations are off: the two ends of the
 * movement, side by side and labelled.
 */
@Composable
private fun ReducedMotionPair(
    animation: ExerciseAnimation,
    style: FigureStyle,
    camera: Camera,
    working: Set<String>,
    highlightWorking: Boolean,
    mirrored: Boolean,
    render: FigureRender,
    modifier: Modifier,
) {
    val startName = animation.sequence.first().pose
    val endName = animation.hardestPose
        ?: animation.sequence.getOrNull(1)?.pose
        ?: startName

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("Start" to startName, "End" to endName).forEach { (label, poseName) ->
            val pose = animation.pose(poseName)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (pose != null) {
                    StickFigure(
                        pose = pose,
                        style = style,
                        camera = camera,
                        symmetry = animation.symmetryMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        props = animation.props,
                        workingLimbs = working,
                        highlightWorking = highlightWorking && label == "End",
                        mirrored = mirrored,
                        render = render,
                    )
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MistDim)
            }
        }
    }
}

/**
 * True when the phone's "remove animations" accessibility setting is on.
 * Android reports it as an animation duration scale of zero.
 */
private fun Context.prefersReducedMotion(): Boolean = try {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (_: Exception) {
    false
}
