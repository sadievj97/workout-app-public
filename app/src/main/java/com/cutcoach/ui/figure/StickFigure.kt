package com.cutcoach.ui.figure

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import com.cutcoach.data.poses.FigureStyle
import com.cutcoach.data.poses.Pose
import com.cutcoach.data.poses.Prop
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.SlateEdge
import com.cutcoach.ui.theme.SunriseAmber

/** Which limb groups an exercise works, for the amber highlight. */
private const val ARMS = "arms"
private const val LEGS = "legs"
private const val TORSO = "torso"

/**
 * One stick figure, drawn from a pose.
 *
 * The figure is mist; the limbs doing the work go amber during a work phase, so
 * a glance says both what the movement is and which part of you should be
 * feeling it. Props are drawn behind in a dimmer colour so they read as
 * furniture rather than as part of the body.
 */
@Composable
fun StickFigure(
    pose: Pose,
    style: FigureStyle,
    modifier: Modifier = Modifier,
    props: List<Prop> = emptyList(),
    workingLimbs: Set<String> = emptySet(),
    highlightWorking: Boolean = false,
    mirrored: Boolean = false,
    /** "side" or "front". A front view draws both limbs equally; a side view dims the far one. */
    view: String = "side",
    /** 0 to 1 around the figure, for a timed hold. Null draws no ring. */
    holdProgress: Float? = null,
) {
    val shown = if (mirrored) pose.mirrored() else pose
    val shownProps = if (mirrored) props.map { it.mirrored() } else props

    Canvas(modifier = modifier) {
        val box = squareBox(size)

        shownProps.forEach { drawProp(it, box) }

        holdProgress?.let { drawHoldRing(it, box, style) }

        drawFigure(
            pose = shown,
            style = style,
            box = box,
            workingLimbs = workingLimbs,
            highlight = highlightWorking,
            view = view,
        )
    }
}

/**
 * Poses are authored inside a square, so the drawing is squared and centred
 * rather than stretched to whatever shape the caller gives it.
 */
private data class Box(val left: Float, val top: Float, val side: Float) {
    fun at(point: List<Float>): Offset =
        Offset(left + point[0] * side, top + point[1] * side)

    fun scale(value: Float): Float = value * side
}

private fun squareBox(size: Size): Box {
    val side = minOf(size.width, size.height)
    return Box(
        left = (size.width - side) / 2f,
        top = (size.height - side) / 2f,
        side = side,
    )
}

private fun DrawScope.drawFigure(
    pose: Pose,
    style: FigureStyle,
    box: Box,
    workingLimbs: Set<String>,
    highlight: Boolean,
    view: String,
) {
    val width = box.scale(style.strokeWidth)

    // In a side view the far arm and leg sit behind the body, so they are
    // drawn faintly to give depth. In a front view both sides are equally in
    // front of you, so they are drawn the same.
    val farAlpha = if (view == "front") 1f else 0.45f

    fun colourFor(group: String, alpha: Float): Color {
        val base = if (highlight && group in workingLimbs) SunriseAmber else Mist
        return if (alpha >= 1f) base else base.copy(alpha = alpha)
    }

    fun bone(a: List<Float>, b: List<Float>, group: String, alpha: Float = 1f) {
        drawLine(
            color = colourFor(group, alpha),
            start = box.at(a),
            end = box.at(b),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }

    // Far side first, so the near limbs read as being in front of it.
    if (pose.hasFarLeg) {
        bone(pose.hip, pose.kneeFar!!, LEGS, farAlpha)
        bone(pose.kneeFar, pose.ankleFar!!, LEGS, farAlpha)
        bone(pose.ankleFar, pose.toeFar!!, LEGS, farAlpha)
    }
    if (pose.hasFarArm) {
        bone(pose.neck, pose.elbowFar!!, ARMS, farAlpha)
        bone(pose.elbowFar, pose.wristFar!!, ARMS, farAlpha)
    }

    bone(pose.hip, pose.knee, LEGS)
    bone(pose.knee, pose.ankle, LEGS)
    bone(pose.ankle, pose.toe, LEGS)

    bone(pose.neck, pose.hip, TORSO)

    bone(pose.neck, pose.elbow, ARMS)
    bone(pose.elbow, pose.wrist, ARMS)

    drawCircle(
        color = colourFor(TORSO, 1f),
        radius = box.scale(style.headRadius),
        center = box.at(pose.head),
        style = Stroke(width = width),
    )
}

private fun DrawScope.drawProp(prop: Prop, box: Box) {
    val width = box.scale(0.012f)

    when (prop.type) {
        "floor", "bar", "table" -> drawLine(
            color = SlateEdge,
            start = box.at(listOf(prop.fromX, prop.y)),
            end = box.at(listOf(prop.toX, prop.y)),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )

        // A chair or step is a line with two short legs dropping from it.
        "chair", "step" -> {
            drawLine(
                color = SlateEdge,
                start = box.at(listOf(prop.fromX, prop.y)),
                end = box.at(listOf(prop.toX, prop.y)),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
            val bottom = prop.y + prop.height
            listOf(prop.fromX, prop.toX).forEach { x ->
                drawLine(
                    color = SlateEdge,
                    start = box.at(listOf(x, prop.y)),
                    end = box.at(listOf(x, bottom)),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }
        }

        "towel" -> drawLine(
            color = SlateEdge,
            start = box.at(listOf(prop.fromX, prop.y)),
            end = box.at(listOf(prop.toX, prop.y)),
            strokeWidth = box.scale(0.02f),
            cap = StrokeCap.Round,
        )
    }
}

/** The ring around a timed hold, filling as the countdown runs out. */
private fun DrawScope.drawHoldRing(progress: Float, box: Box, style: FigureStyle) {
    val inset = box.scale(0.04f)
    val ringWidth = box.scale(style.strokeWidth * 0.8f)
    val topLeft = Offset(box.left + inset, box.top + inset)
    val ringSize = Size(box.side - inset * 2, box.side - inset * 2)

    drawArc(
        color = SlateEdge,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = ringSize,
        style = Stroke(width = ringWidth, cap = StrokeCap.Round),
    )
    drawArc(
        color = CoolSky,
        startAngle = -90f,
        sweepAngle = 360f * progress.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = topLeft,
        size = ringSize,
        style = Stroke(width = ringWidth, cap = StrokeCap.Round),
    )
}
