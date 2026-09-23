package com.cutcoach.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * The launcher icon must survive being masked to a circle.
 *
 * An adaptive icon is drawn on a 108 canvas and then cropped to whatever shape
 * the launcher likes — circle, squircle, rounded square. Only a radius of 33
 * from the centre is guaranteed to survive all of them. Anything outside that
 * looks perfect in Android Studio's preview, which draws the full square, and
 * arrives on the phone with its ends sliced off.
 *
 * The first version of this icon did exactly that: both ends of the curve sat
 * around radius 38 to 42, and the round mask this phone's launcher uses would
 * have cut them. Nothing would have failed, and the icon would simply have
 * looked wrong on the home screen.
 *
 * A cubic Bézier never leaves the convex hull of its four control points, so
 * checking the points and adding half the stroke width is a sound bound rather
 * than a sample.
 */
class IconSafeZoneTest {

    private val centre = 54.0

    /** Guaranteed visible on every launcher mask. */
    private val safeRadius = 33.0

    private val icons = listOf(
        "src/main/res/drawable/ic_launcher_foreground.xml",
        "src/main/res/drawable/ic_launcher_monochrome.xml",
    )

    private data class Stroke(val points: List<Pair<Double, Double>>, val halfWidth: Double)

    /** Pull every `<path>`'s coordinates and its stroke width out of the XML. */
    private fun strokesIn(xml: String): List<Stroke> =
        Regex("""<path\b[\s\S]*?(?:/>|</path>)""").findAll(xml).map { block ->
            val text = block.value

            val width = Regex("""android:strokeWidth="([\d.]+)"""")
                .find(text)?.groupValues?.get(1)?.toDouble() ?: 0.0

            val data = Regex("""android:pathData="([^"]+)"""")
                .find(text)?.groupValues?.get(1).orEmpty()

            // Every number pair in the path data. Commands are letters, so the
            // numbers left over are coordinates, in order.
            val numbers = Regex("""-?[\d.]+""").findAll(data)
                .map { it.value.toDouble() }
                .toList()
            val points = numbers.chunked(2)
                .filter { it.size == 2 }
                .map { it[0] to it[1] }

            Stroke(points, width / 2)
        }.toList()

    @Test
    fun `every icon stays inside the mask's safe radius`() {
        val offenders = mutableListOf<String>()

        icons.forEach { path ->
            val file = File(path)
            assertTrue("Missing icon: $path", file.exists())

            strokesIn(file.readText()).forEach { stroke ->
                stroke.points.forEach { (x, y) ->
                    val reach = hypot(x - centre, y - centre) + stroke.halfWidth
                    if (reach > safeRadius) {
                        offenders += "${file.name}: (%.0f,%.0f) reaches %.1f, past %.0f"
                            .format(x, y, reach, safeRadius)
                    }
                }
            }
        }

        assertTrue(
            "Launcher icon artwork would be cropped by a round mask. Android " +
                "Studio's preview draws the full square and will not show this.\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the icon actually has artwork in it`() {
        // Guards the parser above: a regex that silently matched nothing would
        // make the safe-zone test pass for an empty file.
        icons.forEach { path ->
            val strokes = strokesIn(File(path).readText())
            assertTrue("No paths parsed out of $path", strokes.isNotEmpty())
            assertTrue(
                "No coordinates parsed out of $path",
                strokes.any { it.points.isNotEmpty() },
            )
        }
    }
}
