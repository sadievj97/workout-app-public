package com.cutcoach.data.poses

/**
 * How fast the figure moves, which follows the ladder step you are currently
 * on rather than being fixed.
 *
 * The protocol progresses by reps, then leverage, then load. Two of the
 * leverage options change tempo rather than position: a 3-second lowering, and
 * a pause at the hardest point. Those are the ones read here.
 */
data class Tempo(
    /** Overrides the lowering phase, in seconds. Null keeps the pose file's own timing. */
    val lowerSeconds: Float? = null,
    /** Extra time held at the hardest point. */
    val pauseSeconds: Float = 0f,
) {
    companion object {
        /** About a second up and a second down, which is the protocol's default. */
        val Default = Tempo()

        private val FIRST_NUMBER = Regex("(\\d+(?:\\.\\d+)?)")

        /**
         * Reads a ladder rung such as "3-second lowering" or "Pause 1 to 2
         * seconds just above the floor".
         *
         * Lowering is checked before pause, because "3-second lowering" also
         * contains a number and would otherwise be read as a pause.
         *
         * "Slide-out" is here because the hamstring curl's ladder says
         * "3-second slide-out" rather than "lowering". Without it that rung
         * asked for three seconds and the figure moved at normal speed, which
         * is worse than having no rung at all: the picture contradicts the
         * words beside it.
         */
        fun fromLadderStep(text: String?): Tempo {
            val lower = text?.lowercase() ?: return Default
            val number = FIRST_NUMBER.find(lower)?.groupValues?.get(1)?.toFloatOrNull()
            return when {
                lower.contains("lowering") -> Tempo(lowerSeconds = number ?: DEFAULT_SLOW_LOWER)
                // A stated number is required here. "3-second slide-out" is a
                // tempo rung, but "Slow slide-outs only" is the bottom rung of
                // the hamstring curl's ladder and a different movement
                // altogether, so the word alone must not slow the figure down.
                number != null && SLIDE_PHRASES.any { it in lower } ->
                    Tempo(lowerSeconds = number)
                lower.contains("pause") -> Tempo(pauseSeconds = number ?: DEFAULT_PAUSE)
                else -> Default
            }
        }

        /** The other word a ladder uses for the same slow eccentric. */
        private val SLIDE_PHRASES = listOf("slide-out", "slide out")

        private const val DEFAULT_SLOW_LOWER = 3f
        /** The brief's fallback when a pause rung states no time. */
        private const val DEFAULT_PAUSE = 1f
    }
}

/** One leg of the animation: blend from one pose to another over a duration. */
data class TimelineSegment(
    val from: Pose,
    val to: Pose,
    val seconds: Float,
    val role: String,
) {
    val isHold: Boolean get() = from == to
}

/**
 * The full loop the figure walks, with the tempo rules already applied.
 *
 * Building this separately from the drawing is what makes the timing testable:
 * a 3-second lowering can be checked without rendering anything.
 */
data class Timeline(val segments: List<TimelineSegment>) {

    val totalSeconds: Float = segments.sumOf { it.seconds.toDouble() }.toFloat()

    val isStatic: Boolean get() = totalSeconds <= 0f

    /** The pose at a point in the loop. */
    fun poseAt(seconds: Float): Pose {
        val first = segments.firstOrNull() ?: error("A timeline needs at least one segment")
        if (isStatic) return first.to

        var t = seconds % totalSeconds
        if (t < 0f) t += totalSeconds

        segments.forEach { segment ->
            if (t <= segment.seconds || segment == segments.last()) {
                if (segment.seconds <= 0f) return segment.to
                val progress = (t / segment.seconds).coerceIn(0f, 1f)
                return segment.from.blendTo(segment.to, ease(progress))
            }
            t -= segment.seconds
        }
        return first.to
    }

    /** Smooth at both ends, so the figure does not snap into and out of a rep. */
    private fun ease(t: Float): Float = t * t * (3f - 2f * t)
}

/**
 * Turns the pose file's sequence into a timeline, applying the tempo for the
 * ladder rung the owner is currently on.
 */
fun ExerciseAnimation.timeline(tempo: Tempo = Tempo.Default): Timeline {
    val frames = sequence.mapNotNull { frame -> pose(frame.pose)?.let { frame to it } }
    if (frames.isEmpty()) return Timeline(emptyList())

    // A single pose is a hold: nothing moves, so there is nothing to time.
    if (frames.size == 1) {
        val only = frames.first().second
        return Timeline(listOf(TimelineSegment(only, only, 0f, "hold")))
    }

    val segments = mutableListOf<TimelineSegment>()
    val hardest = hardestPose?.let { pose(it) }

    frames.zipWithNext { (_, fromPose), (toFrame, toPose) ->
        val seconds = when {
            toFrame.role == "lower" && tempo.lowerSeconds != null -> tempo.lowerSeconds
            else -> toFrame.seconds
        }
        segments += TimelineSegment(fromPose, toPose, seconds, toFrame.role)

        // A pause rung holds still at the hardest point before moving on.
        if (tempo.pauseSeconds > 0f && hardest != null && toPose == hardest) {
            segments += TimelineSegment(hardest, hardest, tempo.pauseSeconds, "pause")
        }
    }

    return Timeline(segments)
}
