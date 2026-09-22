package com.cutcoach.data.protocol

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Loads protocol.json and exercises.json from the app's assets and answers
 * questions about them.
 *
 * Everything here is a lookup or an arithmetic consequence of what is in those
 * two files. No training number is invented in this class: the deload
 * multiplier, the ramp times and the schedule all come from the protocol.
 *
 * The files ship inside the app and never change while it runs, so they are
 * read once and kept.
 */
class ProtocolRepository private constructor(
    val protocol: Protocol,
    val library: ExerciseLibrary,
) {

    // ---- Straight lookups -------------------------------------------------

    fun exercise(id: String): Exercise? = library.exercise(id)

    fun session(id: String): SessionDefinition? = protocol.sessions[id]

    /** The schedule entry for a weekday, or null if the protocol has no entry. */
    fun scheduleFor(day: DayOfWeek): ScheduledDay? =
        protocol.weeklySchedule[dayKey(day)]

    fun sessionFor(day: DayOfWeek): SessionDefinition? =
        scheduleFor(day)?.let { session(it.sessionId) }

    // ---- The program calendar --------------------------------------------

    val programStart: LocalDate? = protocol.meta.programStartDate?.let(LocalDate::parse)

    /**
     * Which program week a date falls in, counting from 1. Null before the
     * program starts or after it ends.
     *
     * The program week runs Monday to Sunday because the schedule does.
     */
    fun programWeek(date: LocalDate): Int? {
        val start = programStart ?: return null
        if (date < start) return null
        val week = (ChronoUnit.DAYS.between(start, date) / 7).toInt() + 1
        return if (week <= protocol.meta.programLengthWeeks) week else null
    }

    /** Every 4th program week, per progression_rules.deload.every_nth_week. */
    fun isDeloadWeek(programWeek: Int): Boolean =
        programWeek > 0 && programWeek % protocol.progressionRules.deload.everyNthWeek == 0

    fun isDeloadOn(date: LocalDate): Boolean =
        programWeek(date)?.let(::isDeloadWeek) == true

    /**
     * How many sets to actually do. On a deload week the protocol cuts sets by
     * a third, rounded, never below one: 4 becomes 3, 3 becomes 2.
     */
    fun setsFor(plannedSets: Int, programWeek: Int?): Int {
        if (programWeek == null || !isDeloadWeek(programWeek)) return plannedSets
        val reduced = (plannedSets * protocol.progressionRules.deload.setMultiplier).roundToInt()
        return max(1, reduced)
    }

    // ---- The bedtime ramp -------------------------------------------------

    val rampStart: LocalDate? = protocol.sleep.rampStartDate?.let(LocalDate::parse)

    /**
     * Which night of the bedtime ramp a given evening is, counting from 1.
     *
     * A ramp night is dated by the evening you go to bed, not the morning you
     * wake up.
     */
    fun rampNight(evening: LocalDate): Int? {
        val start = rampStart ?: return null
        if (evening < start) return null
        return ChronoUnit.DAYS.between(start, evening).toInt() + 1
    }

    /** The lights-out target for a given evening, from the ramp table. */
    fun lightsOutOn(evening: LocalDate): LocalTime? {
        val night = rampNight(evening) ?: return null
        val step = protocol.sleep.bedtimeRamp.firstOrNull { it.nights.contains(night) } ?: return null
        return LocalTime.parse(step.lightsOut)
    }

    val wakeTime: LocalTime get() = LocalTime.parse(protocol.sleep.wakeTime)

    /**
     * Shorter than this and a recorded sleep is a nap rather than the night.
     * Read from the file, so "how short is a nap" stays adjustable.
     */
    val napThreshold: Duration
        get() = Duration.ofMinutes((protocol.sleep.napThresholdHours * 60).toLong())

    // ---- Reminders --------------------------------------------------------

    /**
     * The moments of the day that can carry a notification, in order, with
     * tonight's real bedtime substituted for the timeline's end-state one.
     *
     * During the ramp those differ: the timeline says 21:30 because that is
     * where the ramp lands, while tonight's target may still be 22:45. A
     * reminder that fired at the wrong one would be worse than none.
     */
    fun reminderMoments(evening: LocalDate): List<ReminderMoment> =
        protocol.dailyTimeline.map { entry ->
            val scheduled = LocalTime.parse(entry.time)
            val tonight = if (entry.isLightsOut) lightsOutOn(evening) ?: scheduled else scheduled
            ReminderMoment(
                id = entry.time,
                label = entry.label,
                time = tonight,
                isLightsOut = entry.isLightsOut,
                onByDefault = !scheduled.isBefore(LocalTime.parse(protocol.reminders.defaultOnFrom)),
            )
        }

    // ---- Convenience ------------------------------------------------------

    val restSeconds: Int get() = protocol.sessionDefaults.restSeconds
    val switchSidesSeconds: Int get() = protocol.sessionDefaults.switchSidesSeconds
    val warmup: Warmup get() = library.warmupDefault

    private fun dayKey(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "MON"
        DayOfWeek.TUESDAY -> "TUE"
        DayOfWeek.WEDNESDAY -> "WED"
        DayOfWeek.THURSDAY -> "THU"
        DayOfWeek.FRIDAY -> "FRI"
        DayOfWeek.SATURDAY -> "SAT"
        DayOfWeek.SUNDAY -> "SUN"
    }

    companion object {
        private const val PROTOCOL_ASSET = "protocol.json"
        private const val EXERCISES_ASSET = "exercises.json"

        @Volatile
        private var cached: ProtocolRepository? = null

        /**
         * `ignoreUnknownKeys` lets the protocol carry notes and explanations
         * the code has no use for, which is most of why those files are
         * readable by a human. The naming strategy maps snake_case in the file
         * onto camelCase in Kotlin so neither side has to compromise.
         */
        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            ignoreUnknownKeys = true
            namingStrategy = JsonNamingStrategy.SnakeCase
        }

        /**
         * Reads both files. Returns a [ProtocolLoad] rather than throwing, so a
         * typo in the protocol shows as a readable message on screen instead of
         * a crash at 05:35.
         */
        fun load(context: Context): ProtocolLoad {
            cached?.let { return ProtocolLoad.Success(it) }
            val result = parse(
                protocolText = readAsset(context, PROTOCOL_ASSET),
                exercisesText = readAsset(context, EXERCISES_ASSET),
            )
            if (result is ProtocolLoad.Success) cached = result.repository
            return result
        }

        /**
         * Parses the two files from text. Separate from [load] so unit tests
         * can run the real protocol.json through it on the desktop, without an
         * Android device in the loop.
         */
        fun parse(protocolText: String, exercisesText: String): ProtocolLoad {
            val protocol = try {
                json.decodeFromString<Protocol>(protocolText)
            } catch (e: Exception) {
                return ProtocolLoad.Failure(PROTOCOL_ASSET, e)
            }

            val library = try {
                json.decodeFromString<ExerciseLibrary>(exercisesText)
            } catch (e: Exception) {
                return ProtocolLoad.Failure(EXERCISES_ASSET, e)
            }

            return ProtocolLoad.Success(ProtocolRepository(protocol, library))
        }

        private fun readAsset(context: Context, name: String): String =
            context.assets.open(name).bufferedReader().use { it.readText() }
    }
}

/**
 * One moment in the day that can carry a reminder.
 *
 * [id] is the timeline's own written time, which is stable across the ramp;
 * [time] is when it actually fires tonight.
 */
data class ReminderMoment(
    val id: String,
    val label: String,
    val time: LocalTime,
    val isLightsOut: Boolean,
    val onByDefault: Boolean,
)

/** The outcome of reading the two protocol files. */
sealed interface ProtocolLoad {
    data class Success(val repository: ProtocolRepository) : ProtocolLoad

    /** Which file failed, and why, in terms the owner can act on. */
    data class Failure(val file: String, val cause: Throwable) : ProtocolLoad {
        val message: String get() = "Could not read $file: ${cause.message ?: cause::class.simpleName}"
    }
}
