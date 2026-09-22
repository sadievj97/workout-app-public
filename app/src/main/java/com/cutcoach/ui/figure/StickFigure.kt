package com.cutcoach.ui.figure

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cutcoach.data.poses.Camera
import com.cutcoach.data.poses.FigureStyle
import com.cutcoach.data.poses.MuscleRegion
import com.cutcoach.data.poses.Pose
import com.cutcoach.data.poses.musclePatches
import com.cutcoach.data.poses.Projected
import com.cutcoach.data.poses.Prop
import com.cutcoach.data.poses.Symmetry
import com.cutcoach.data.poses.Vec3
import com.cutcoach.data.poses.body
import com.cutcoach.data.poses.project
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.SlateEdge
import com.cutcoach.ui.theme.SunriseAmber

/** Which limb groups an exercise works, for the amber highlight. */
private const val ARMS = "arms"
private const val LEGS = "legs"
private const val TORSO = "torso"

/**
 * How much of a body to draw.
 *
 * [SOLID] is the real figure: a shaded mannequin with volume, which is what a
 * cue card wants. [LINES] is the bare skeleton, which stays legible at the
 * small size the session player shows it at, where a shaded body would just be
 * a smudge.
 */
enum class FigureRender { SOLID, LINES }

/**
 * One stick figure, projected from its three-dimensional pose.
 *
 * The figure is mist; the limbs doing the work go amber during a work phase, so
 * a glance says both what the movement is and which part of you should be
 * feeling it. Parts further from the viewer are drawn first and faded, which is
 * what stops a turned figure reading as a flat tangle of lines.
 */
@Composable
fun StickFigure(
    pose: Pose,
    style: FigureStyle,
    camera: Camera,
    symmetry: Symmetry,
    modifier: Modifier = Modifier,
    props: List<Prop> = emptyList(),
    workingLimbs: Set<String> = emptySet(),
    highlightWorking: Boolean = false,
    mirrored: Boolean = false,
    render: FigureRender = FigureRender.SOLID,
    /** Where the worked muscles sit, for the amber patches on the solid figure. */
    muscleRegions: List<MuscleRegion> = emptyList(),
    /** Which way the front of the body points. Needed before "front of the arm" means anything. */
    facing: Vec3 = Vec3(0f, 0f, 1f),
    /** 0 to 1 around the figure, for a timed hold. Null draws no ring. */
    holdProgress: Float? = null,
) {
    val shown = if (mirrored) pose.mirrored(symmetry) else pose
    val shownProps = if (mirrored) props.map { it.mirrored(symmetry) } else props

    Canvas(modifier = modifier) {
        val box = squareBox(size)

        shownProps.forEach { drawProp(it, box, camera) }

        holdProgress?.let { drawHoldRing(it, box, style) }

        when (render) {
            FigureRender.SOLID -> drawMannequin(
                parts = shown.body(symmetry, style.body),
                patches = shown.musclePatches(symmetry, style.body, facing, muscleRegions),
                style = style.body,
                camera = camera,
                box = box,
                workingLimbs = workingLimbs,
                highlight = highlightWorking,
            )

            FigureRender.LINES -> drawFigure(
                pose = shown,
                style = style,
                camera = camera,
                symmetry = symmetry,
                box = box,
                workingLimbs = workingLimbs,
                highlight = highlightWorking,
            )
        }
    }
}

/**
 * Poses project into a square, so the drawing is squared and centred rather
 * than stretched to whatever shape the caller gives it.
 */
private fun squareBox(size: Size): FigureBox {
    val side = minOf(size.width, size.height)
    return FigureBox(
        left = (size.width - side) / 2f,
        top = (size.height - side) / 2f,
        side = side,
    )
}

/** One limb segment, ready to draw, with the depth it ended up at. */
private data class Bone(
    val from: Projected,
    val to: Projected,
    val group: String,
) {
    val depth: Float get() = (from.depth + to.depth) / 2f
}

private fun DrawScope.drawFigure(
    pose: Pose,
    style: FigureStyle,
    camera: Camera,
    symmetry: Symmetry,
    box: FigureBox,
    workingLimbs: Set<String>,
    highlight: Boolean,
) {
    fun at(point: Vec3) = point.project(camera)

    val neck = at(pose.neckAt)
    val hip = at(pose.hipAt)
    val head = at(pose.headAt)

    val armA = pose.armA().map(::at)
    val armB = pose.armB(symmetry).map(::at)
    val legA = pose.legA().map(::at)
    val legB = pose.legB(symmetry).map(::at)

    val bones = buildList {
        add(Bone(neck, hip, TORSO))
        listOf(armA, armB).forEach { arm ->
            add(Bone(neck, arm[0], ARMS))
            add(Bone(arm[0], arm[1], ARMS))
        }
        listOf(legA, legB).forEach { leg ->
            add(Bone(hip, leg[0], LEGS))
            add(Bone(leg[0], leg[1], LEGS))
            add(Bone(leg[1], leg[2], LEGS))
        }
    }

    // Fade is worked out from the figure's own depth range, so it holds up at
    // any camera angle instead of relying on a tuned constant.
    val depths = bones.map { it.depth } + head.depth
    val nearest = depths.max()
    val furthest = depths.min()
    val spread = nearest - furthest

    fun fadeAt(depth: Float): Float {
        if (spread < 0.01f) return 1f
        val howNear = (depth - furthest) / spread
        return style.farFade + (1f - style.farFade) * howNear
    }

    fun colourFor(group: String, fade: Float): Color {
        val base = if (highlight && group in workingLimbs) SunriseAmber else Mist
        return if (fade >= 0.999f) base else base.copy(alpha = fade)
    }

    val width = box.scale(style.strokeWidth)

    // Furthest first, so nearer limbs sit on top.
    bones.sortedBy { it.depth }.forEach { bone ->
        drawLine(
            color = colourFor(bone.group, fadeAt(bone.depth)),
            start = box.at(bone.from),
            end = box.at(bone.to),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }

    drawCircle(
        color = colourFor(TORSO, fadeAt(head.depth)),
        radius = box.scale(style.headRadius),
        center = box.at(head),
        style = Stroke(width = width),
    )
}

private fun DrawScope.drawProp(prop: Prop, box: FigureBox, camera: Camera) {
    val width = box.scale(0.012f)

    fun line(fromX: Float, toX: Float, y: Float, z: Float, stroke: Float = width) {
        drawLine(
            color = SlateEdge,
            start = box.at(Vec3(fromX, y, z).project(camera)),
            end = box.at(Vec3(toX, y, z).project(camera)),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }

    when (prop.type) {
        // A floor with any depth is drawn as its two edges, so it reads as a
        // plane once the camera turns off dead-on.
        "floor", "table" -> if (prop.depth > 0f) {
            line(prop.fromX, prop.toX, prop.y, prop.z - prop.depth / 2f)
            line(prop.fromX, prop.toX, prop.y, prop.z + prop.depth / 2f)
        } else {
            line(prop.fromX, prop.toX, prop.y, prop.z)
        }

        "bar" -> line(prop.fromX, prop.toX, prop.y, prop.z)

        "towel" -> line(prop.fromX, prop.toX, prop.y, prop.z, box.scale(0.02f))

        // A chair or step is its top edge with two short legs dropping from it.
        "chair", "step" -> {
            line(prop.fromX, prop.toX, prop.y, prop.z)
            listOf(prop.fromX, prop.toX).forEach { x ->
                drawLine(
                    color = SlateEdge,
                    start = box.at(Vec3(x, prop.y, prop.z).project(camera)),
                    end = box.at(Vec3(x, prop.y + prop.height, prop.z).project(camera)),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** The ring around a timed hold, filling as the countdown runs out. */
private fun DrawScope.drawHoldRing(progress: Float, box: FigureBox, style: FigureStyle) {
    val inset = box.scale(0.03f)
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
