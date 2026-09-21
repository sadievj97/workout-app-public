package com.cutcoach.data.db

import android.content.Context
import kotlinx.coroutines.flow.Flow
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

    suspend fun setLadderStep(exerciseId: String, step: Int) =
        db.exerciseStateDao().upsert(
            ExerciseStateEntity(
                exerciseId = exerciseId,
                ladderStep = step,
                updatedAt = System.currentTimeMillis(),
            ),
        )

    fun allLadderSteps(): Flow<List<ExerciseStateEntity>> = db.exerciseStateDao().all()

    // ---- Body -------------------------------------------------------------

    suspend fun recordWeight(date: LocalDate, kg: Double) =
        db.bodyMetricDao().setWeight(date.toString(), kg)

    suspend fun recordWaist(date: LocalDate, cm: Double) =
        db.bodyMetricDao().setWaist(date.toString(), cm)

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
 * The 7-day rolling average the protocol asks for, computed over whatever days
 * actually have a weight.
 *
 * Returns null rather than guessing when there is nothing to average.
 */
fun List<BodyMetricEntity>.sevenDayAverageKg(): Double? {
    val weights = take(7).mapNotNull { it.weightKg }
    return if (weights.isEmpty()) null else weights.average()
}
