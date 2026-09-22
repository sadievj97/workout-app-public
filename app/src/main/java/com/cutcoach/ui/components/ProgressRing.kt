package com.cutcoach.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cutcoach.ui.theme.SlateEdge
import com.cutcoach.ui.theme.tabular

/**
 * A fraction of a target, as a ring.
 *
 * "2124 of 9500" takes reading; a quarter-filled ring takes a glance, which is
 * the whole design brief for this app. The number stays inside it, because the
 * ring answers "roughly where am I" and the number answers "exactly where".
 *
 * The sweep animates once on arrival rather than pulsing: movement draws the
 * eye, and the eye should land here once and then move on.
 */
@Composable
fun ProgressRing(
    progress: Float,
    colour: Color,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    thickness: Dp = 8.dp,
    label: String? = null,
) {
    val settled by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "ring",
    )

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = thickness.toPx(), cap = StrokeCap.Round)
            val inset = thickness.toPx() / 2f
            val box = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val corner = androidx.compose.ui.geometry.Offset(inset, inset)

            drawArc(
                color = SlateEdge,
                startAngle = START_ANGLE,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = corner,
                size = box,
                style = stroke,
            )
            if (settled > 0f) {
                drawArc(
                    color = colour,
                    startAngle = START_ANGLE,
                    sweepAngle = 360f * settled,
                    useCenter = false,
                    topLeft = corner,
                    size = box,
                    style = stroke,
                )
            }
        }
        label?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = colour,
            )
        }
    }
}

/** Twelve o'clock, so a part-filled ring reads like a clock face rather than a dial. */
private const val START_ANGLE = -90f
