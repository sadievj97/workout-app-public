package com.cutcoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cutcoach.ui.theme.SlateEdge

/**
 * A run of numbers as a line, for the shape rather than the values.
 *
 * A column of weights says what each morning was; this says which way the
 * fortnight went, which is the only thing the protocol actually judges. The
 * numbers stay on the screen underneath, because the line cannot be read to a
 * tenth of a kilogram and should not pretend to be.
 *
 * Days with nothing recorded leave a gap rather than a straight line drawn
 * through them. Joining across a missing day would invent a measurement, and
 * the whole app is built not to do that.
 */
@Composable
fun Sparkline(
    /** Oldest first. Nulls are days with nothing recorded. */
    values: List<Double?>,
    colour: Color,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
) {
    val present = values.filterNotNull()
    if (present.size < 2) return

    val low = present.min()
    val high = present.max()
    // A flat fortnight would otherwise divide by zero and draw off the top.
    val span = (high - low).takeIf { it > 0.0001 } ?: 1.0

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
        val inset = 6f
        val usable = size.height - inset * 2

        fun pointAt(index: Int, value: Double) = Offset(
            x = index * stepX,
            // Lighter is higher on the screen, so the scale is inverted.
            y = inset + ((high - value) / span * usable).toFloat(),
        )

        // A faint baseline, so a nearly flat line still reads as nearly flat
        // rather than as a line wandering in empty space.
        drawLine(
            color = SlateEdge,
            start = Offset(0f, size.height - inset),
            end = Offset(size.width, size.height - inset),
            strokeWidth = 1.5f,
        )

        val stroke = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        var path: Path? = null

        values.forEachIndexed { index, value ->
            if (value == null) {
                // Close the run. The next reading starts a new line rather than
                // being joined across a day that was never weighed.
                path?.let { drawPath(it, colour, style = stroke) }
                path = null
                return@forEachIndexed
            }
            val point = pointAt(index, value)
            if (path == null) {
                path = Path().apply { moveTo(point.x, point.y) }
            } else {
                path!!.lineTo(point.x, point.y)
            }
        }
        path?.let { drawPath(it, colour, style = stroke) }

        // The most recent reading, marked, because that is the one being read.
        values.indexOfLast { it != null }.takeIf { it >= 0 }?.let { last ->
            drawCircle(colour, radius = 4.5f, center = pointAt(last, values[last]!!))
        }
    }
}
