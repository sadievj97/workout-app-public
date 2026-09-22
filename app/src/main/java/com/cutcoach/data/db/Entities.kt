package com.cutcoach.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Everything the app records. All of it stays on this phone: there is no
 * account, no backend and no sync.
 *
 * The tables deliberately store what happened, not what was planned. The plan
 * always comes from protocol.json, so changing a target later never rewrites
 * history.
 */

/** One session, whether or not it was finished. */
@Entity(tableName = "session_log")
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO date the session belongs to. */
    val date: String,
    val sessionId: String,
    /** Copied in so history still reads correctly if the protocol is renamed. */
    val sessionName: String,
    val programWeek: Int?,
    val isDeload: Boolean,
    val startedAt: Long,
    /** Null while a session is still in progress or was abandoned. */
    val endedAt: Long? = null,
    val plannedMinutes: Int,
    val note: String = "",
)

/**
 * One set as performed. Either [reps] or [seconds] is set, never both, because
 * the protocol has rep exercises and timed holds and nothing that is both.
 */
@Entity(
    tableName = "set_log",
    foreignKeys = [
        ForeignKey(
            entity = SessionLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionLogId"), Index("exerciseId")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionLogId: Long,
    val exerciseId: String,
    val setNumber: Int,
    /** "NONE", "LEFT" or "RIGHT". */
    val side: String,
    val reps: Int? = null,
    val seconds: Int? = null,
    /** Which rung of the exercise's ladder this set was performed at. */
    val ladderStep: Int = 0,
    val loggedAt: Long,
)

/**
 * How hard each exercise currently is: which rung of its ladder you are on.
 * The only mutable state about difficulty, and the thing Phase 3's progression
 * rules will move.
 */
@Entity(tableName = "exercise_state")
data class ExerciseStateEntity(
    @PrimaryKey val exerciseId: String,
    val ladderStep: Int = 0,
    val updatedAt: Long,
)

/**
 * Daily body measurements. One row per date, with waist usually only on
 * Sundays.
 *
 * [source] says where the number came from. Manual entries win where both
 * exist, so the two have to stay distinguishable: a watch reading written down
 * as "manual" would look like something the owner stood on the scale for.
 */
@Entity(tableName = "body_metric")
data class BodyMetricEntity(
    @PrimaryKey val date: String,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val source: String = WeightSource.MANUAL,
) {
    val fromWatch: Boolean get() = source == WeightSource.WATCH
}

/**
 * Stored as text rather than an enum so the column stays readable in the
 * database and a future source can be added without a migration.
 */
object WeightSource {
    /** Typed in on the Body screen. Always wins. */
    const val MANUAL = "manual"

    /** A WeightRecord read from Health Connect by the daily sync. */
    const val WATCH = "watch"
}
