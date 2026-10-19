package com.cutcoach.data.db

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Everything the app writes down, behind one door.
 *
 * Screens talk to this rather than to Room directly, so the storage details
 * stay in one place and Phase 2 can merge watch data in without touching any
 * screen.
 */
class TrainingRepository(private val db: CutCoachDatabase) {

    // ---- Sessions ---------------------------------------------------------

    suspend fun startSession(
        date: LocalDate,
        sessionId: String,
        sessionName: String,
        programWeek: Int?,
        isDeload: Boolean,
        plannedMinutes: Int,
    ): Long = db.sessionLogDao().insert(
        SessionLogEntity(
            date = date.toString(),
            sessionId = sessionId,
            sessionName = sessionName,
            programWeek = programWeek,
            isDeload = isDeload,
            startedAt = System.currentTimeMillis(),
            plannedMinutes = plannedMinutes,
        ),
    )

    suspend fun endSession(sessionLogId: Long) =
        db.sessionLogDao().markEnded(sessionLogId, System.currentTimeMillis())

    /**
     * Record a session that happened without the app.
     *
     * Written as started and ended in one go, with no sets. That is the honest
     * shape of it: the session happened, and nothing is known about what was
     * done inside it. Nothing here fills the sets in from the targets — a
     * target is what was planned, and writing it down as if performed would put
     * numbers in the log that were never typed by anyone, which is the exact
     * thing the `no_guessing` guardrail forbids.
     *
     * [atTime] lets a past day be filled in. The timestamps land at [atTime] on that
     * date rather than at the moment of typing, so History sorts it where it
     * belongs instead of at the top.
     */
    suspend fun markSessionDone(
        date: LocalDate,
        sessionId: String,
        sessionName: String,
        programWeek: Int?,
        isDeload: Boolean,
        plannedMinutes: Int,
        atTime: LocalTime = LocalTime.NOON,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val stamp = date.atTime(atTime).atZone(zone).toInstant().toEpochMilli()
        return db.sessionLogDao().insert(
            SessionLogEntity(
                date = date.toString(),
                sessionId = sessionId,
                sessionName = sessionName,
                programWeek = programWeek,
                isDeload = isDeload,
                startedAt = stamp,
                endedAt = stamp,
                plannedMinutes = plannedMinutes,
            ),
        )
    }

    /** Whether a session is already recorded for a date, to avoid a silent double entry. */
    suspend fun sessionAlreadyOn(date: LocalDate, sessionId: String): Boolean =
        db.sessionLogDao().countOn(date.toString(), sessionId) > 0

    suspend fun setSessionNote(sessionLogId: Long, note: String) =
        db.sessionLogDao().setNote(sessionLogId, note)

    fun recentSessions(): Flow<List<SessionWithCount>> = db.sessionLogDao().recentSessions()

    fun setsFor(sessionLogId: Long): Flow<List<SetLogEntity>> =
        db.setLogDao().setsFor(sessionLogId)

    // ---- Sets -------------------------------------------------------------

    suspend fun logSet(
        sessionLogId: Long,
        exerciseId: String,
        setNumber: Int,
        side: String,
        reps: Int? = null,
        seconds: Int? = null,
        ladderStep: Int = 0,
    ) = db.setLogDao().insert(
        SetLogEntity(
            sessionLogId = sessionLogId,
            exerciseId = exerciseId,
            setNumber = setNumber,
            side = side,
            reps = reps,
            seconds = seconds,
            ladderStep = ladderStep,
            loggedAt = System.currentTimeMillis(),
        ),
    )

    /** Undo one logged set, for stepping back in the player. */
    suspend fun deleteSet(
        sessionLogId: Long,
        exerciseId: String,
        setNumber: Int,
        side: String,
    ) = db.setLogDao().deleteSet(sessionLogId, exerciseId, setNumber, side)

    fun historyFor(exerciseId: String): Flow<List<SetLogEntity>> =
        db.setLogDao().historyFor(exerciseId)

    fun loggedExerciseIds(): Flow<List<String>> = db.setLogDao().loggedExerciseIds()

    /** What you managed last time, which is what progression compares against. */
    suspend fun lastSessionSets(exerciseId: String): List<SetLogEntity> =
        db.setLogDao().lastSessionSets(exerciseId)

    /**
     * Whether this exercise's most recent session went unrecorded.
     *
     * True when a session containing it finished more recently than the last
     * time anything was written down for it. [lastSessionSets] cannot see such
     * a session at all — it reaches the session through set_log, and a session
     * with no sets has no rows there — so without this check the app would read
     * an older session as the latest news and advance a ladder on the strength
     * of numbers that have since been superseded by a session nobody logged.
     *
     * [sessionIds] are the sessions the protocol says contain this exercise.
     * The protocol file is the only thing that knows that; the database stores
     * a session id, not its contents.
     */
    suspend fun lastSessionWentUnlogged(exerciseId: String, sessionIds: List<String>): Boolean {
        if (sessionIds.isEmpty()) return false
        val newestSession = db.sessionLogDao().newestFinishedAmong(sessionIds) ?: return false
        val newestNumbers = db.setLogDao().lastSetsRecordedAt(exerciseId) ?: return true
        return newestSession > newestNumbers
    }

    // ---- Difficulty -------------------------------------------------------

    suspend fun ladderStep(exerciseId: String): Int =
        db.exerciseStateDao().forExercise(exerciseId)?.ladderStep ?: 0

    /**
     * Where an exercise sits on its ladder.
     *
     * [rungs] is the length of that exercise's ladder, and the step is clamped
     * to it. An index past the end would leave [ladderStep] pointing at nothing:
     * the cue card would show no rung as current and the figure would fall back
     * to the default tempo, both silently.
     */
    suspend fun setLadderStep(exerciseId: String, step: Int, rungs: Int) =
        db.exerciseStateDao().upsert(
            ExerciseStateEntity(
                exerciseId = exerciseId,
                ladderStep = step.coerceIn(0, (rungs - 1).coerceAtLeast(0)),
                updatedAt = System.currentTimeMillis(),
            ),
        )

    fun allLadderSteps(): Flow<List<ExerciseStateEntity>> = db.exerciseStateDao().all()

    /** Every exercise's rung, keyed by id, for the screens that show them all. */
    val ladderSteps: Flow<Map<String, Int>> =
        db.exerciseStateDao().all().map { rows -> rows.associate { it.exerciseId to it.ladderStep } }

    fun ladderStepFlow(exerciseId: String): Flow<Int> =
        db.exerciseStateDao().all().map { rows ->
            rows.firstOrNull { it.exerciseId == exerciseId }?.ladderStep ?: 0
        }

    // ---- Body -------------------------------------------------------------

    /**
     * A weight, from the owner or from the watch.
     *
     * Passing the source matters: the store uses it to keep a watch reading
     * from displacing a day already weighed by hand.
     */
    suspend fun recordWeight(
        date: LocalDate,
        kg: Double,
        source: String = WeightSource.MANUAL,
    ) = db.bodyMetricDao().setWeight(date.toString(), kg, source)

    suspend fun recordWaist(date: LocalDate, cm: Double) =
        db.bodyMetricDao().setWaist(date.toString(), cm)

    /**
     * Remove a weight that should not be there.
     *
     * A mistyped number at 05:35 would otherwise sit in the 7-day average with
     * no way to take it out, only to type a different one over it. Clearing it
     * also clears the source, so a watch reading may fill the day again.
     */
    suspend fun clearWeight(date: LocalDate) = with(db.bodyMetricDao()) {
        clearWeight(date.toString())
        deleteIfEmpty(date.toString())
    }

    suspend fun clearWaist(date: LocalDate) = with(db.bodyMetricDao()) {
        clearWaist(date.toString())
        deleteIfEmpty(date.toString())
    }

    suspend fun bodyOn(date: LocalDate): BodyMetricEntity? = db.bodyMetricDao().on(date.toString())

    fun allBody(): Flow<List<BodyMetricEntity>> = db.bodyMetricDao().all()

    fun recentBody(from: LocalDate, days: Int): Flow<List<BodyMetricEntity>> =
        db.bodyMetricDao().recent(from.toString(), days)

    companion object {
        @Volatile
        private var instance: TrainingRepository? = null

        fun get(context: Context): TrainingRepository =
            instance ?: synchronized(this) {
                instance ?: TrainingRepository(CutCoachDatabase.get(context)).also { instance = it }
            }
    }
}

/**
 * The 7-day rolling average the protocol asks for: the days that have a weight
 * within the calendar week ending on [endingOn].
 *
 * Windowed by date, not by row count. Rows only exist for days something was
 * logged, so counting seven rows would silently reach back a fortnight after a
 * few missed days and call the result a 7-day average. The protocol judges
 * progress by this number, so it has to mean what it says.
 *
 * Returns null rather than guessing when nothing in the window was weighed.
 */
fun List<BodyMetricEntity>.sevenDayAverageKg(endingOn: LocalDate): Double? {
    val earliest = endingOn.minusDays(WINDOW_DAYS - 1L)
    val weights = inWindow(earliest, endingOn).mapNotNull { it.weightKg }
    return if (weights.isEmpty()) null else weights.average()
}

/** How many of the days in that window came from the watch rather than by hand. */
fun List<BodyMetricEntity>.watchDaysInWindow(endingOn: LocalDate): Int {
    val earliest = endingOn.minusDays(WINDOW_DAYS - 1L)
    return inWindow(earliest, endingOn).count { it.weightKg != null && it.fromWatch }
}

private fun List<BodyMetricEntity>.inWindow(
    earliest: LocalDate,
    latest: LocalDate,
): List<BodyMetricEntity> = filter { row ->
    // A row whose date will not parse is skipped rather than crashing a screen
    // the owner is standing over at 05:35.
    val date = runCatching { LocalDate.parse(row.date) }.getOrNull()
    date != null && !date.isBefore(earliest) && !date.isAfter(latest)
}

private const val WINDOW_DAYS = 7
