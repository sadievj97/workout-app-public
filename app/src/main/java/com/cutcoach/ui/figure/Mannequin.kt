package com.cutcoach.ui.figure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.cutcoach.data.poses.Ball
import com.cutcoach.data.poses.BodyPart
import com.cutcoach.data.poses.BodyStyle
import com.cutcoach.data.poses.Camera
import com.cutcoach.data.poses.Capsule
import com.cutcoach.data.poses.Projected
import com.cutcoach.data.poses.Slab
import com.cutcoach.data.poses.project
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.PreDawnNavy
import com.cutcoach.ui.theme.SunriseAmber
import kotlin.math.sqrt

/**
 * Draws the solid figure: the body as rounded volumes rather than lines.
 *
 * Three things together make it read as three-dimensional. Parts are drawn
 * furthest first, so nearer limbs cover the ones behind. Each part is shaded
 * across its width from a fixed light, so a limb looks round instead of flat.
 * And each part carries a dark rim, so an arm crossing the chest stays legible
 * rather than melting into it.
 */
internal fun DrawScope.drawMannequin(
    parts: List<BodyPart>,
    style: BodyStyle,
    camera: Camera,
    box: FigureBox,
    workingLimbs: Set<String>,
    highlight: Boolean,
) {
    if (parts.isEmpty()) return

    // Depth is measured across the whole figure, so the fade holds up at any
    // camera angle rather than relying on a tuned constant.
    val withDepth = parts.map { part ->
        part to part.points.map { it.project(camera).depth }.average().toFloat()
    }
    val nearest = withDepth.maxOf { it.second }
    val furthest = withDepth.minOf { it.second }
    val spread = nearest - furthest

    fun fadeAt(depth: Float): Float {
        if (spread < 0.01f) return 1f
        val howNear = (depth - furthest) / spread
        return style.farFade + (1f - style.farFade) * howNear
    }

    val light = lightDirection(style)
    val outline = box.scale(style.outline)
    val muscleOutline = box.scale(style.muscleOutline)

    withDepth.sortedBy { it.second }.forEach { (part, depth) ->
        val fade = fadeAt(depth)
        val base = baseColour(part, workingLimbs, highlight)
        // Fading toward the background rather than toward transparent keeps the
        // rim between overlapping limbs from showing through.
        val faded = lerp(PreDawnNavy, base, fade)

        // Muscles get a far slimmer rim than the frame, so neighbouring masses
        // flow into each other instead of reading as separate pills.
        val rim = if (part.group == "muscle") muscleOutline else outline

        when (part) {
            is Capsule -> drawCapsule(part, camera, box, faded, light, rim)
            is Slab -> drawSlab(part, camera, box, faded, light, rim)
            is Ball -> drawBall(part, camera, box, faded, light, rim)
        }
    }
}

/**
 * A worked muscle is amber; everything else is mist.
 *
 * `workingLimbs` remains as a coarse fallback for a pose with no anatomy yet,
 * so a figure is never left with nothing marked.
 */
private fun baseColour(part: BodyPart, working: Set<String>, highlight: Boolean): Color = when {
    !highlight -> Mist
    part is Capsule && part.worked -> SunriseAmber
    part is Capsule && part.group == "muscle" -> Mist
    part.group in working -> SunriseAmber
    else -> Mist
}

private fun lightDirection(style: BodyStyle): Offset {
    val x = style.lightFrom.getOrElse(0) { -0.45f }
    val y = style.lightFrom.getOrElse(1) { -0.80f }
    val length = sqrt(x * x + y * y)
    return if (length < 1e-4f) Offset(0f, -1f) else Offset(x / length, y / length)
}

/** Lit on the side facing the light, darker on the other. */
private fun shading(
    centre: Offset,
    radius: Float,
    colour: Color,
    light: Offset,
): Brush {
    val lit = lerp(colour, Color.White, 0.22f)
    val shadow = lerp(colour, PreDawnNavy, 0.55f)
    return Brush.linearGradient(
        colors = listOf(lit, colour, shadow),
        start = Offset(centre.x + light.x * radius, centre.y + light.y * radius),
        end = Offset(centre.x - light.x * radius * 1.2f, centre.y - light.y * radius * 1.2f),
    )
}

private fun DrawScope.drawCapsule(
    capsule: Capsule,
    camera: Camera,
    box: FigureBox,
    colour: Color,
    light: Offset,
    outline: Float,
) {
    val a = box.at(capsule.from.project(camera))
    val b = box.at(capsule.to.project(camera))
    val radiusA = box.scale(capsule.radiusFrom)
    val radiusB = box.scale(capsule.radiusTo)

    val centre = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val reach = maxOf(radiusA, radiusB)
    val brush = shading(centre, reach, colour, light)

    // The rim first, slightly fatter, in the background colour.
    fill(a, b, radiusA + outline, radiusB + outline, PreDawnNavy)
    fillBrush(a, b, radiusA, radiusB, brush)
}

/**
 * A capsule is drawn as the quad between its two circles plus the circles
 * themselves. Sharing one brush across all three means the seams do not show,
 * and it avoids any arc arithmetic.
 */
private fun DrawScope.fill(
    a: Offset,
    b: Offset,
    radiusA: Float,
    radiusB: Float,
    colour: Color,
) {
    drawPath(capsuleQuad(a, b, radiusA, radiusB), colour)
    drawCircle(colour, radiusA, a)
    drawCircle(colour, radiusB, b)
}

private fun DrawScope.fillBrush(
    a: Offset,
    b: Offset,
    radiusA: Float,
    radiusB: Float,
    brush: Brush,
) {
    drawPath(capsuleQuad(a, b, radiusA, radiusB), brush)
    drawCircle(brush, radiusA, a)
    drawCircle(brush, radiusB, b)
}

private fun capsuleQuad(a: Offset, b: Offset, radiusA: Float, radiusB: Float): Path {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1e-4f) return Path()

    // Perpendicular to the limb, so the quad spans its width.
    val px = -dy / length
    val py = dx / length

    return Path().apply {
        moveTo(a.x + px * radiusA, a.y + py * radiusA)
        lineTo(b.x + px * radiusB, b.y + py * radiusB)
        lineTo(b.x - px * radiusB, b.y - py * radiusB)
        lineTo(a.x - px * radiusA, a.y - py * radiusA)
        close()
    }
}

private fun DrawScope.drawSlab(
    slab: Slab,
    camera: Camera,
    box: FigureBox,
    colour: Color,
    light: Offset,
    outline: Float,
) {
    val corners = slab.corners.map { box.at(it.project(camera)) }
    if (corners.size < 3) return

    val radius = box.scale(slab.radius)
    val centre = Offset(
        corners.sumOf { it.x.toDouble() }.toFloat() / corners.size,
        corners.sumOf { it.y.toDouble() }.toFloat() / corners.size,
    )
    val brush = shading(centre, radius * 1.6f, colour, light)

    val path = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }

    // Filling the quad and stroking its outline with a round join gives a
    // rounded slab without building the rounded outline by hand.
    drawPath(path, PreDawnNavy)
    drawPath(
        path,
        PreDawnNavy,
        style = Stroke(width = (radius + outline) * 2f, join = StrokeJoin.Round),
    )
    drawPath(path, brush)
    drawPath(path, brush, style = Stroke(width = radius * 2f, join = StrokeJoin.Round))
}

private fun DrawScope.drawBall(
    ball: Ball,
    camera: Camera,
    box: FigureBox,
    colour: Color,
    light: Offset,
    outline: Float,
) {
    val centre = box.at(ball.centre.project(camera))
    val radius = box.scale(ball.radius)

    drawCircle(PreDawnNavy, radius + outline, centre)
    // A radial highlight where the light lands reads as a sphere rather than a
    // disc, which matters most on the head.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                lerp(colour, Color.White, 0.28f),
                colour,
                lerp(colour, PreDawnNavy, 0.5f),
            ),
            center = Offset(centre.x + light.x * radius * 0.45f, centre.y + light.y * radius * 0.45f),
            radius = radius * 1.5f,
        ),
        radius = radius,
        center = centre,
    )
}

/** Shared with the line renderer: the square the figure is projected into. */
internal data class FigureBox(val left: Float, val top: Float, val side: Float) {
    fun at(point: Projected): Offset = Offset(left + point.x * side, top + point.y * side)
    fun scale(value: Float): Float = value * side
}
