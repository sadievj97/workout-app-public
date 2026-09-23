package com.cutcoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's icons, drawn in code.
 *
 * No icon library and no image files, for the same reason the figures are drawn
 * rather than bundled: nothing here needs a licence, and the repo can be public
 * in Phase 6 without an audit.
 *
 * These are meant to be read at arm's length in a dim room, so they are simple
 * line shapes on a 24-unit grid with a heavy stroke. An icon that needs looking
 * at twice is worse than the word it replaced.
 *
 * Every icon here labels something. The brief asks for no decoration, so an
 * icon that carries no information does not belong in this file.
 */
enum class CutIcon {
    /** Last night, the bedtime ramp. */
    MOON,

    /** Heart rate, sleeping or in a session. */
    HEART,

    /** Steps against the daily target. */
    FOOTSTEPS,

    /** The scale, and the 7-day average. */
    SCALE,

    /** A rung of a ladder: progression. */
    LADDER,

    /** Something to stop for. */
    ALERT,

    /** A question the app is asking rather than answering. */
    QUESTION,

    /** Waking, and the first light. */
    SUNRISE,

    /** Food and drink. */
    PLATE,

    /** Training. */
    BARBELL,

    /** A walk, or cardio. */
    SHOE,

    /** Screens down, lamps only. */
    LAMP,

    /** A session that was finished. */
    CHECK,

    /** A figure: the form-check drawings. */
    PERSON,

    /** A rest day, with nothing scheduled. */
    REST,

    /** The week. */
    CALENDAR,

    /** What has already happened. */
    CLOCK,

    /** Settings. */
    GEAR,

    /** A plain point on the timeline. */
    DOT,

    /** Pause a running timer. */
    PAUSE,

    /** Resume a paused timer. */
    PLAY,

    /** Move past the current step without logging it. */
    SKIP,

    /** Leave the session. */
    STOP,
}

/**
 * One icon, at [size], in [tint].
 *
 * Drawn on a 24-unit grid and scaled, so the same paths work at 16 dp on a
 * timeline row and at 28 dp on a card heading.
 */
@Composable
fun CutIconView(
    icon: CutIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(
            width = 2f * unit,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawIcon(icon, tint, unit, stroke)
    }
}

private fun DrawScope.drawIcon(icon: CutIcon, tint: Color, u: Float, stroke: Stroke) {
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(tint, p(x1, y1), p(x2, y2), stroke.width, stroke.cap)

    when (icon) {
        CutIcon.MOON -> {
            // A crescent, cut from one circle by another rather than drawn as a
            // path: the subtraction keeps the horns sharp at any size.
            val full = Path().apply { addOval(Rect(p(3f, 3f), Size(16f * u, 16f * u))) }
            val bite = Path().apply { addOval(Rect(p(8f, 0.5f), Size(16f * u, 16f * u))) }
            drawPath(Path().apply { op(full, bite, PathOperation.Difference) }, tint)
        }

        CutIcon.HEART -> {
            // A pulse line, not a heart shape. It says "rate" rather than
            // "love", and reads at 16 dp where a heart turns into a blob.
            line(2f, 12f, 7f, 12f)
            line(7f, 12f, 9f, 5f)
            line(9f, 5f, 12f, 19f)
            line(12f, 19f, 15f, 10f)
            line(15f, 10f, 17f, 12f)
            line(17f, 12f, 22f, 12f)
        }

        CutIcon.FOOTSTEPS -> {
            drawOval(tint, p(3f, 4f), Size(6f * u, 8f * u))
            drawOval(tint, p(4f, 13f), Size(4f * u, 3f * u))
            drawOval(tint, p(14f, 9f), Size(6f * u, 8f * u))
            drawOval(tint, p(15f, 18f), Size(4f * u, 3f * u))
        }

        CutIcon.SCALE -> {
            drawRoundRect(
                color = tint,
                topLeft = p(3f, 5f),
                size = Size(18f * u, 14f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * u, 3f * u),
                style = stroke,
            )
            // The needle, at rest a little left of centre.
            line(12f, 16f, 9.5f, 10f)
            drawCircle(tint, radius = 1.2f * u, center = p(12f, 16f))
        }

        CutIcon.LADDER -> {
            line(7f, 3f, 7f, 21f)
            line(17f, 3f, 17f, 21f)
            line(7f, 8f, 17f, 8f)
            line(7f, 13f, 17f, 13f)
            line(7f, 18f, 17f, 18f)
        }

        CutIcon.ALERT -> {
            line(12f, 3f, 3f, 20f)
            line(3f, 20f, 21f, 20f)
            line(21f, 20f, 12f, 3f)
            line(12f, 9f, 12f, 14f)
            drawCircle(tint, radius = 1.1f * u, center = p(12f, 17f))
        }

        CutIcon.QUESTION -> {
            drawCircle(tint, radius = 9f * u, center = p(12f, 12f), style = stroke)
            // The hook of a question mark, and its point.
            drawArc(
                color = tint,
                startAngle = 160f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = p(9f, 6f),
                size = Size(6f * u, 6f * u),
                style = stroke,
            )
            line(12f, 12f, 12f, 14f)
            drawCircle(tint, radius = 1.0f * u, center = p(12f, 17f))
        }

        CutIcon.SUNRISE -> {
            drawArc(
                color = tint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = p(7f, 9f),
                size = Size(10f * u, 10f * u),
                style = stroke,
            )
            line(3f, 19f, 21f, 19f)
            line(12f, 3f, 12f, 6f)
            line(5f, 6f, 6.8f, 7.8f)
            line(19f, 6f, 17.2f, 7.8f)
        }

        CutIcon.PLATE -> {
            drawCircle(tint, radius = 8.5f * u, center = p(12f, 12f), style = stroke)
            drawCircle(tint, radius = 4f * u, center = p(12f, 12f), style = stroke)
        }

        CutIcon.BARBELL -> {
            line(4f, 12f, 20f, 12f)
            line(5f, 8f, 5f, 16f)
            line(19f, 8f, 19f, 16f)
            line(2.5f, 10f, 2.5f, 14f)
            line(21.5f, 10f, 21.5f, 14f)
        }

        CutIcon.SHOE -> {
            line(3f, 16f, 3f, 11f)
            line(3f, 11f, 8f, 11f)
            line(8f, 11f, 11f, 14f)
            line(11f, 14f, 18f, 15f)
            line(18f, 15f, 21f, 17f)
            line(21f, 17f, 3f, 17f)
        }

        CutIcon.LAMP -> {
            line(8f, 11f, 16f, 11f)
            line(8f, 11f, 10f, 4f)
            line(16f, 11f, 14f, 4f)
            line(10f, 4f, 14f, 4f)
            line(12f, 11f, 12f, 20f)
            line(9f, 20f, 15f, 20f)
        }

        CutIcon.CHECK -> {
            line(4f, 13f, 9.5f, 18f)
            line(9.5f, 18f, 20f, 6f)
        }

        CutIcon.PERSON -> {
            drawCircle(tint, radius = 3f * u, center = p(12f, 6f), style = stroke)
            line(12f, 9f, 12f, 16f)
            line(12f, 11f, 7f, 14f)
            line(12f, 11f, 17f, 14f)
            line(12f, 16f, 8.5f, 21f)
            line(12f, 16f, 15.5f, 21f)
        }

        // A dash: nothing scheduled, said without a symbol that implies failure.
        CutIcon.REST -> line(6f, 12f, 18f, 12f)

        CutIcon.CALENDAR -> {
            drawRoundRect(
                color = tint,
                topLeft = p(3f, 5f),
                size = Size(18f * u, 16f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * u, 2.5f * u),
                style = stroke,
            )
            line(3f, 10f, 21f, 10f)
            line(8f, 3f, 8f, 6f)
            line(16f, 3f, 16f, 6f)
        }

        CutIcon.CLOCK -> {
            drawCircle(tint, radius = 9f * u, center = p(12f, 12f), style = stroke)
            line(12f, 7f, 12f, 12f)
            line(12f, 12f, 16f, 14f)
        }

        // Sliders, not a cog. A cog small enough for a tab bar loses its teeth
        // and reads as a crosshair, which is what the first attempt did.
        CutIcon.GEAR -> {
            line(3f, 7f, 21f, 7f)
            line(3f, 12f, 21f, 12f)
            line(3f, 17f, 21f, 17f)
            drawCircle(tint, radius = 2.4f * u, center = p(9f, 7f))
            drawCircle(tint, radius = 2.4f * u, center = p(15f, 12f))
            drawCircle(tint, radius = 2.4f * u, center = p(8f, 17f))
        }

        CutIcon.DOT -> drawCircle(tint, radius = 3.2f * u, center = p(12f, 12f))

        // The transport controls are filled rather than stroked, unlike every
        // other icon here. They sit on a button that is pressed without looking,
        // mid-set, and a solid shape is findable by silhouette where an outline
        // is not.
        CutIcon.PAUSE -> {
            drawRect(tint, topLeft = p(7.5f, 5f), size = Size(3.2f * u, 14f * u))
            drawRect(tint, topLeft = p(13.3f, 5f), size = Size(3.2f * u, 14f * u))
        }

        CutIcon.PLAY -> drawPath(
            Path().apply {
                moveTo(p(8f, 5f).x, p(8f, 5f).y)
                lineTo(p(19f, 12f).x, p(19f, 12f).y)
                lineTo(p(8f, 19f).x, p(8f, 19f).y)
                close()
            },
            tint,
        )

        // A triangle against a bar: the same shape every player uses for
        // "next", which is what this does.
        CutIcon.SKIP -> {
            drawPath(
                Path().apply {
                    moveTo(p(6f, 5f).x, p(6f, 5f).y)
                    lineTo(p(16f, 12f).x, p(16f, 12f).y)
                    lineTo(p(6f, 19f).x, p(6f, 19f).y)
                    close()
                },
                tint,
            )
            drawRect(tint, topLeft = p(16.8f, 5f), size = Size(2.6f * u, 14f * u))
        }

        CutIcon.STOP -> drawRect(
            tint,
            topLeft = p(6.5f, 6.5f),
            size = Size(11f * u, 11f * u),
        )
    }
}
