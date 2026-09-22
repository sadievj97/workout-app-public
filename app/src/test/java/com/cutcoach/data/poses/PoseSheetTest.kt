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
    private val extraAngles = listOf(45f, 90f, 135f)

    @Test
    fun `write a contact sheet of every pose`() {
        val text = File("src/main/assets/poses.json").readText()
        val library = (PoseRepository.parse(text) as PoseLoad.Success).library

        val rows = library.exercises.flatMap { (id, animation) ->
            animation.poses.keys.map { Triple(id, it, animation) }
        }
        val columns = 1 + extraAngles.size
        val width = columns * side
        val height = rows.size * side

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
                cameras.forEachIndexed { column, (camera, label) ->
                    append(
                        panel(
                            pose = pose,
                            animation = animation,
                            style = library.figure,
                            camera = camera,
                            originX = column * side,
                            originY = row * side,
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
            val heights = if (prop.type == "chair" || prop.type == "step") {
                listOf(prop.y)
            } else {
                listOf(prop.y)
            }
            zs.forEach { z ->
                heights.forEach { y ->
                    val a = Vec3(prop.fromX, y, z).project(camera)
                    val b = Vec3(prop.toX, y, z).project(camera)
                    sb.append(
                        "<line x1='${sx(a)}' y1='${sy(a)}' x2='${sx(b)}' y2='${sy(b)}' " +
                            "stroke='#2C3E5C' stroke-width='${stroke * 0.7}' " +
                            "stroke-linecap='round'/>",
                    )
                }
            }
        }

        val neck = pose.neckAt.project(camera)
        val hip = pose.hipAt.project(camera)
        val head = pose.headAt.project(camera)
        val armA = pose.armA().map { it.project(camera) }
        val armB = pose.armB(symmetry).map { it.project(camera) }
        val legA = pose.legA().map { it.project(camera) }
        val legB = pose.legB(symmetry).map { it.project(camera) }

        val bones = buildList {
            add(Triple(neck, hip, "torso"))
            listOf(armA, armB).forEach { arm ->
                add(Triple(neck, arm[0], "arms"))
                add(Triple(arm[0], arm[1], "arms"))
            }
            listOf(legA, legB).forEach { leg ->
                add(Triple(hip, leg[0], "legs"))
                add(Triple(leg[0], leg[1], "legs"))
                add(Triple(leg[1], leg[2], "legs"))
            }
        }

        val depths = bones.map { (it.first.depth + it.second.depth) / 2f } + head.depth
        val nearest = depths.max()
        val furthest = depths.min()
        val spread = nearest - furthest

        fun fade(depth: Float): Float =
            if (spread < 0.01f) 1f else style.farFade + (1f - style.farFade) * ((depth - furthest) / spread)

        bones.sortedBy { (it.first.depth + it.second.depth) / 2f }.forEach { (from, to, _) ->
            val opacity = fade((from.depth + to.depth) / 2f)
            sb.append(
                "<line x1='${sx(from)}' y1='${sy(from)}' x2='${sx(to)}' y2='${sy(to)}' " +
                    "stroke='#D5DDE8' stroke-opacity='$opacity' stroke-width='$stroke' " +
                    "stroke-linecap='round'/>",
            )
        }

        sb.append(
            "<circle cx='${sx(head)}' cy='${sy(head)}' r='${style.headRadius * side}' " +
                "fill='none' stroke='#D5DDE8' stroke-opacity='${fade(head.depth)}' " +
                "stroke-width='$stroke'/>",
        )
        sb.append(
            "<text x='${originX + side / 2}' y='${originY + side - 6}' fill='#7FB2E5' " +
                "font-size='12' text-anchor='middle' font-family='sans-serif'>$label</text>",
        )
        return sb.toString()
    }
}
