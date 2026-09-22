package com.cutcoach.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A session row plus the count of sets logged against it, for the history list. */
data class SessionWithCount(
    @Embedded val session: SessionLogEntity,
    val setCount: Int,
)

@Dao
interface SessionLogDao {

    @Insert
    suspend fun insert(session: SessionLogEntity): Long

    @Query("UPDATE session_log SET endedAt = :endedAt WHERE id = :id")
    suspend fun markEnded(id: Long, endedAt: Long)

    @Query("UPDATE session_log SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String)

    @Query(
        """
        SELECT s.*, (SELECT COUNT(*) FROM set_log WHERE sessionLogId = s.id) AS setCount
        FROM session_log s
        ORDER BY s.startedAt DESC
        """,
    )
    fun recentSessions(): Flow<List<SessionWithCount>>

    @Query("SELECT * FROM session_log WHERE date = :date ORDER BY startedAt DESC")
    fun sessionsOn(date: String): Flow<List<SessionLogEntity>>

    /** Used to reattach to a session that was left running. */
    @Query("SELECT * FROM session_log WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun unfinishedSession(): SessionLogEntity?
}

@Dao
interface SetLogDao {

    @Insert
    suspend fun insert(set: SetLogEntity): Long

    @Query("SELECT * FROM set_log WHERE sessionLogId = :sessionLogId ORDER BY id")
    fun setsFor(sessionLogId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_log WHERE sessionLogId = :sessionLogId ORDER BY id")
    suspend fun setsForOnce(sessionLogId: Long): List<SetLogEntity>

    /** Every set of one exercise, newest first: the per-exercise history. */
    @Query(
        """
        SELECT sl.* FROM set_log sl
        JOIN session_log s ON s.id = sl.sessionLogId
        WHERE sl.exerciseId = :exerciseId
        ORDER BY s.startedAt DESC, sl.id ASC
        """,
    )
    fun historyFor(exerciseId: String): Flow<List<SetLogEntity>>

    /** The sets of the most recent session that included this exercise. */
    @Query(
        """
        SELECT sl.* FROM set_log sl
        WHERE sl.sessionLogId = (
            SELECT s.id FROM session_log s
            JOIN set_log x ON x.sessionLogId = s.id
            WHERE x.exerciseId = :exerciseId AND s.endedAt IS NOT NULL
            ORDER BY s.startedAt DESC LIMIT 1
        )
        AND sl.exerciseId = :exerciseId
        ORDER BY sl.id
        """,
    )
    suspend fun lastSessionSets(exerciseId: String): List<SetLogEntity>

    @Query("SELECT DISTINCT exerciseId FROM set_log")
    fun loggedExerciseIds(): Flow<List<String>>
}

@Dao
interface ExerciseStateDao {

    @Upsert
    suspend fun upsert(state: ExerciseStateEntity)

    @Query("SELECT * FROM exercise_state WHERE exerciseId = :exerciseId")
    suspend fun forExercise(exerciseId: String): ExerciseStateEntity?

    @Query("SELECT * FROM exercise_state")
    fun all(): Flow<List<ExerciseStateEntity>>
}

@Dao
interface BodyMetricDao {

    /**
     * Weight and waist are written separately, because a Sunday has both and
     * every other day has only weight. A plain upsert of the whole row would
     * erase whichever one was not being entered.
     *
     * The WHERE clause is the protocol's "manual entries win" rule, enforced by
     * the store rather than by whoever happens to call it. A watch reading
     * fills a day that has none and quietly does nothing on a day already
     * weighed by hand. Keeping it in SQL means a background sync landing at the
     * same moment as a typed entry cannot win by arriving second.
     */
    @Query(
        """
        INSERT INTO body_metric (date, weightKg, waistCm, source)
        VALUES (:date, :weightKg, NULL, :source)
        ON CONFLICT(date) DO UPDATE SET weightKg = :weightKg, source = :source
        WHERE :source = 'manual'
           OR body_metric.weightKg IS NULL
           OR body_metric.source <> 'manual'
        """,
    )
    suspend fun setWeight(date: String, weightKg: Double, source: String = WeightSource.MANUAL)

    @Query(
        """
        INSERT INTO body_metric (date, weightKg, waistCm, source)
        VALUES (:date, NULL, :waistCm, :source)
        ON CONFLICT(date) DO UPDATE SET waistCm = :waistCm
        """,
    )
    suspend fun setWaist(date: String, waistCm: Double, source: String = WeightSource.MANUAL)

    /**
     * Undo a weight, for the mistyped one at 05:35.
     *
     * Two statements rather than a delete, because a Sunday row also carries a
     * waist measurement that has nothing to do with the weight. The row only
     * goes when nothing is left in it.
     */
    @Query("UPDATE body_metric SET weightKg = NULL, source = 'manual' WHERE date = :date")
    suspend fun clearWeight(date: String)

    @Query("UPDATE body_metric SET waistCm = NULL WHERE date = :date")
    suspend fun clearWaist(date: String)

    @Query("DELETE FROM body_metric WHERE date = :date AND weightKg IS NULL AND waistCm IS NULL")
    suspend fun deleteIfEmpty(date: String)

    @Query("SELECT * FROM body_metric WHERE date = :date")
    suspend fun on(date: String): BodyMetricEntity?

    @Query("SELECT * FROM body_metric ORDER BY date DESC")
    fun all(): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metric WHERE date <= :date ORDER BY date DESC LIMIT :limit")
    fun recent(date: String, limit: Int): Flow<List<BodyMetricEntity>>
}
