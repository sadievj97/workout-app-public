package com.cutcoach.data.poses

import org.junit.Test
import java.io.File

/**
 * Writes a contact sheet of every pose, from several camera angles, to
 * `app/build/pose-sheet.svg`.
 *
 * This is a development tool rather than a check: it asserts almost nothing.
 * Its value is that it uses the real [project] function and the real pose
 * file, so what it draws is what the app draws. Authoring a figure blind and
 * then looking at a sheet catches a wrong coordinate in seconds, which is how
 * the push-up's shrinking torso was found.
 *
 * Run it with:
 *   gradlew :app:testDebugUnitTest --tests "*PoseSheetTest*"
 */
class PoseSheetTest {

    private val side = 260

    /**
     * Extra camera angles per pose. With fifteen exercises the full spread is
     * too tall to take in, so the default is the chosen angle only, laid out as
     * a grid. Put angles back here when checking one exercise closely.
     */
    private val extraAngles = emptyList<Float>()

    /** How many panels across before wrapping. */
    private val columns = 4

    @Test
    fun `write a contact sheet of every pose`() {
        val text = File("src/main/assets/poses.json").readText()
        val library = (PoseRepository.parse(text) as PoseLoad.Success).library
        val exercises = com.cutcoach.data.protocol.ProtocolRepository.parse(
            File("src/main/assets/protocol.json").readText(),
            File("src/main/assets/exercises.json").readText(),
        ).let { it as com.cutcoach.data.protocol.ProtocolLoad.Success }.repository


        // Variants are hand-authored exactly like base poses and go wrong the
        // same ways, so they belong on the sheet. A borrowed one is skipped: it
        // already appears under the exercise it borrows from.
        val animations = library.exercises.flatMap { (id, animation) ->
            listOf(id to animation) +
                animation.variants
                    .filter { it.sameAs == null }
                    .map { "$id r${it.rung}" to animation.atRung(it.rung) }
        }
        val rows = animations.flatMap { (id, animation) ->
            animation.poses.keys.map { Triple(id, it, animation) }
        }
        val perPose = 1 + extraAngles.size
        val panels = rows.size * perPose
        val width = columns * side
        val height = ((panels + columns - 1) / columns) * side

        val svg = buildString {
            append("<svg xmlns='http://www.w3.org/2000/svg' width='$width' height='$height' ")
            append("viewBox='0 0 $width $height'>")
            append("<rect width='$width' height='$height' fill='#101B2D'/>")

            rows.forEachIndexed { row, (id, poseName, animation) ->
                val pose = animation.pose(poseName)!!
                val cameras = buildList {
                    add(animation.camera to "$id / $poseName")
                    extraAngles.forEach {
                        add(animation.camera.turnedBy(it) to "turned ${it.toInt()} more")
                    }
                }
                val worked = exercises.exercise(id)?.muscles.orEmpty()
                cameras.forEachIndexed { index, (camera, label) ->
                    val panelNumber = row * perPose + index
                    append(
                        panel(
                            pose = pose,
                            animation = animation,
                            style = library.figure,
                            camera = camera,
                            worked = worked,
                            anatomy = library.anatomy,
                            originX = (panelNumber % columns) * side,
                            originY = (panelNumber / columns) * side,
                            label = label,
                        ),
                    )
                }
            }
            append("</svg>")
        }

        val out = File("build/pose-sheet.svg")
        out.parentFile?.mkdirs()
        out.writeText(svg)
        println("Pose sheet written to ${out.absolutePath}")
    }

    private fun panel(
        pose: Pose,
        animation: ExerciseAnimation,
        style: FigureStyle,
        camera: Camera,
        worked: List<String>,
        anatomy: Anatomy,
        originX: Int,
        originY: Int,
        label: String,
    ): String {
        val symmetry = animation.symmetryMode
        val stroke = style.strokeWidth * side

        fun sx(p: Projected) = originX + p.x * side
        fun sy(p: Projected) = originY + p.y * side

        val sb = StringBuilder()

        // Props behind everything.
        animation.props.forEach { prop ->
            val zs = if (prop.depth > 0f) {
                listOf(prop.z - prop.depth / 2f, prop.z + prop.depth / 2f)
            } else {
                listOf(prop.z)
            }
            fun line(from: Vec3, to: Vec3) {
                val a = from.project(camera)
                val b = to.project(camera)
                sb.append(
                    "<line x1='${sx(a)}' y1='${sy(a)}' x2='${sx(b)}' y2='${sy(b)}' " +
                        "stroke='#2C3E5C' stroke-width='${stroke * 0.7}' " +
                        "stroke-linecap='round'/>",
                )
            }

            zs.forEach { z ->
                line(Vec3(prop.fromX, prop.y, z), Vec3(prop.toX, prop.y, z))
            }

            // A chair, step or bar stands on something. Drawing only its top
            // edge here while the app drew the uprights meant the sheet was
            // quietly hiding the furniture, which is how a bar with no posts
            // and a bench with no legs both passed review.
            if (prop.type == "chair" || prop.type == "step") {
                listOf(prop.fromX, prop.toX).forEach { x ->
                    line(Vec3(x, prop.y, prop.z), Vec3(x, prop.y + prop.height, prop.z))
                }
            }
        }

        // The solid figure, built and depth-sorted exactly as the app does it.
        val body = pose.body(symmetry, style.body) +
            pose.anatomy(symmetry, style.body, animation.facingAt, anatomy, worked)
        val withDepth = body.map { part ->
            part to part.points.map { it.project(camera).depth }.average().toFloat()
        }
        val nearest = withDepth.maxOf { it.second }
        val furthest = withDepth.minOf { it.second }
        val spread = nearest - furthest

        fun fade(depth: Float): Float = if (spread < 0.01f) {
            1f
        } else {
            style.body.farFade + (1f - style.body.farFade) * ((depth - furthest) / spread)
        }

        val outline = style.body.outline * side
        val muscleOutline = style.body.muscleOutline * side

        withDepth.sortedBy { it.second }.forEach { (part, depth) ->
            val isWorked = part is Capsule && part.worked
            val shade = mixToward(if (isWorked) 0xF2A541 else 0xD5DDE8, 0x101B2D, 1f - fade(depth))
            val rim = "#101B2D"
            val edge = if (part.group == "muscle") muscleOutline else outline
            when (part) {
                is Capsule -> {
                    val a = part.from.project(camera)
                    val b = part.to.project(camera)
                    val ra = part.radiusFrom * side
                    val rb = part.radiusTo * side
                    sb.append(capsuleSvg(sx(a), sy(a), sx(b), sy(b), ra + edge, rb + edge, rim))
                    sb.append(capsuleSvg(sx(a), sy(a), sx(b), sy(b), ra, rb, shade))
                }
                is Slab -> {
                    val pts = part.corners.map { it.project(camera) }
                    val poly = pts.joinToString(" ") { "${sx(it)},${sy(it)}" }
                    val r = part.radius * side
                    sb.append(
                        "<polygon points='$poly' fill='$rim' stroke='$rim' " +
                            "stroke-width='${(r + edge) * 2}' stroke-linejoin='round'/>",
                    )
                    sb.append(
                        "<polygon points='$poly' fill='$shade' stroke='$shade' " +
                            "stroke-width='${r * 2}' stroke-linejoin='round'/>",
                    )
                }
                is Ball -> {
                    val c = part.centre.project(camera)
                    val r = part.radius * side
                    sb.append(
                        "<circle cx='${sx(c)}' cy='${sy(c)}' r='${r + edge}' fill='$rim'/>",
                    )
                    sb.append("<circle cx='${sx(c)}' cy='${sy(c)}' r='$r' fill='$shade'/>")
                }
            }
        }


        sb.append(
            "<text x='${originX + side / 2}' y='${originY + side - 6}' fill='#7FB2E5' " +
                "font-size='12' text-anchor='middle' font-family='sans-serif'>$label</text>",
        )
        return sb.toString()
    }

    /** A capsule as its quad plus its two end circles, same as the app draws it. */
    private fun capsuleSvg(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        ra: Float,
        rb: Float,
        fill: String,
    ): String {
        val dx = bx - ax
        val dy = by - ay
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        val body = if (length < 0.001f) {
            ""
        } else {
            val px = -dy / length
            val py = dx / length
            val points = listOf(
                (ax + px * ra) to (ay + py * ra),
                (bx + px * rb) to (by + py * rb),
                (bx - px * rb) to (by - py * rb),
                (ax - px * ra) to (ay - py * ra),
            ).joinToString(" ") { "${it.first},${it.second}" }
            "<polygon points='$points' fill='$fill'/>"
        }
        return body +
            "<circle cx='$ax' cy='$ay' r='$ra' fill='$fill'/>" +
            "<circle cx='$bx' cy='$by' r='$rb' fill='$fill'/>"
    }

    /** Blends two packed colours, returning a packed colour. */
    private fun blend(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * amount).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** Fades a colour toward the background, matching the renderer's depth fade. */
    private fun mixToward(from: Int, to: Int, amount: Float): String {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * amount).toInt().coerceIn(0, 255)
        }
        return "#%02X%02X%02X".format(channel(16), channel(8), channel(0))
    }
}
