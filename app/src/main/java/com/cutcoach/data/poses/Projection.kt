package com.cutcoach.data.poses

import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

/**
 * A point in the figure's world, centred on the figure.
 *
 * x runs left to right, y runs up to down, z runs away from the viewer to
 * toward the viewer. All three are roughly minus a half to plus a half, so the
 * figure sits around the origin and the camera can swing around it.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {

    fun negateX() = copy(x = -x)
    fun negateZ() = copy(z = -z)

    companion object {
        /** Reads a [x, y, z] triple from the pose file. A missing z is zero. */
        fun of(values: List<Float>): Vec3 = Vec3(
            x = values.getOrElse(0) { 0f },
            y = values.getOrElse(1) { 0f },
            z = values.getOrElse(2) { 0f },
        )
    }
}

/**
 * Where the viewer stands, in degrees.
 *
 * Azimuth swings around the figure: 0 looks straight down the z axis at the
 * x-y plane, 90 looks along the x axis instead. Elevation lifts the viewer
 * above eye level.
 *
 * Because every exercise names its own camera, one set of poses can be shown
 * from whatever angle reads best, instead of each pose being authored for a
 * single fixed view.
 */
@Serializable
data class Camera(
    val azimuth: Float = 0f,
    val elevation: Float = 0f,
) {
    /** Turned by the viewer dragging the figure. */
    fun turnedBy(extraAzimuth: Float, extraElevation: Float = 0f) = Camera(
        azimuth = azimuth + extraAzimuth,
        elevation = (elevation + extraElevation).coerceIn(-60f, 60f),
    )
}

/**
 * A projected point: where to draw it, plus how near the viewer it ended up.
 *
 * [depth] is what makes the figure read as solid: the parts further away are
 * drawn first and slightly faded.
 */
data class Projected(val x: Float, val y: Float, val depth: Float)

/**
 * Flattens a world point onto the square the figure is drawn in.
 *
 * The projection is orthographic. A stick figure has no surfaces for
 * perspective to act on, and orthographic keeps limb lengths honest as the
 * camera turns, which matters more here than the illusion of distance.
 */
fun Vec3.project(camera: Camera): Projected {
    val azimuth = camera.azimuth.toRadians()
    val elevation = camera.elevation.toRadians()

    val cosA = cos(azimuth)
    val sinA = sin(azimuth)
    // Swing around the vertical axis.
    val screenX = x * cosA + z * sinA
    val afterAzimuth = -x * sinA + z * cosA

    val cosE = cos(elevation)
    val sinE = sin(elevation)
    // Lift the viewer. Nearer parts drop down the screen, which is what looking
    // down on something does.
    val screenY = y * cosE + afterAzimuth * sinE
    val depth = -y * sinE + afterAzimuth * cosE

    return Projected(x = 0.5f + screenX, y = 0.5f + screenY, depth = depth)
}

/**
 * How much a direction points toward the viewer under this camera.
 *
 * Positive means it faces you. Used to decide whether a muscle highlight is
 * actually visible from here, or whether the whole segment should be tinted
 * faintly instead.
 */
fun Vec3.facesViewer(camera: Camera): Float {
    val azimuth = camera.azimuth.toRadians()
    val elevation = camera.elevation.toRadians()
    val afterAzimuth = -x * sin(azimuth) + z * cos(azimuth)
    return -y * sin(elevation) + afterAzimuth * cos(elevation)
}

private fun Float.toRadians(): Float = (this * Math.PI / 180.0).toFloat()
