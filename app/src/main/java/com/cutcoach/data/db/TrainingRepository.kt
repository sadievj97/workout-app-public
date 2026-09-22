package com.cutcoach.data.db

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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

    fun historyFor(exerciseId: String): Flow<List<SetLogEntity>> =
        db.setLogDao().historyFor(exerciseId)

    fun loggedExerciseIds(): Flow<List<String>> = db.setLogDao().loggedExerciseIds()

    /** What you managed last time, which is what progression compares against. */
    suspend fun lastSessionSets(exerciseId: String): List<SetLogEntity> =
        db.setLogDao().lastSessionSets(exerciseId)

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
