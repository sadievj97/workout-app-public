package com.cutcoach.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/**
 * The one door to Health Connect.
 *
 * Read only, throughout. The app takes what the watch already recorded and
 * never writes, changes or deletes anything: there is no WRITE permission
 * anywhere in the manifest, so it could not even if asked.
 */
class HealthConnectManager(private val context: Context) {

    /**
     * Whether Health Connect is usable on this phone.
     *
     * Three outcomes matter and they need different answers, so this is not a
     * boolean: not installed means go and install it, needs an update means go
     * and update it, and unavailable means nothing here will work at all.
     */
    val availability: Availability
        get() = when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                Availability.UPDATE_REQUIRED
            else -> Availability.UNAVAILABLE
        }

    enum class Availability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

    private val client: HealthConnectClient?
        get() = if (availability == Availability.AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }

    /** Every permission the app asks for. All reads, plus history and background. */
    val permissions: Set<String> = buildSet {
        RECORD_TYPES.forEach { add(HealthPermission.getReadPermission(it.type)) }
        add(PERMISSION_READ_HISTORY)
        add(PERMISSION_READ_IN_BACKGROUND)
    }

    suspend fun grantedPermissions(): Set<String> =
        client?.permissionController?.getGrantedPermissions().orEmpty()

    suspend fun hasAllPermissions(): Boolean =
        grantedPermissions().containsAll(permissions)

    /**
     * What is actually in Health Connect for each record type over a window.
     *
     * This is the whole point of the Data Check screen: before anything depends
     * on a data type, see with your own eyes how many records the phone really
     * provides and what the latest one says. A type that looks fine in the
     * documentation can be entirely empty on a given phone.
     */
    suspend fun survey(days: Int = 30): List<RecordSurvey> {
        val healthConnect = client ?: return emptyList()
        val granted = grantedPermissions()
        val from = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val window = TimeRangeFilter.between(from, Instant.now())

        return RECORD_TYPES.map { type ->
            val permission = HealthPermission.getReadPermission(type.type)
            if (permission !in granted) {
                return@map RecordSurvey(type, RecordState.NOT_GRANTED)
            }

            try {
                val records = healthConnect
                    .readRecords(ReadRecordsRequest(type.type, window))
                    .records

                RecordSurvey(
                    type = type,
                    state = if (records.isEmpty()) RecordState.EMPTY else RecordState.PRESENT,
                    count = records.size,
                    latest = records.lastOrNull()?.let { type.describe(it) },
                )
            } catch (e: Exception) {
                // A type can fail on its own without the others being affected,
                // so one failure must not blank the whole screen.
                RecordSurvey(type, RecordState.FAILED, failure = e.message ?: "unknown error")
            }
        }
    }

    companion object {
        /**
         * Without this, reads are limited to the 30 days before the first grant.
         * Checked against the documentation rather than recalled: a wrong
         * permission string does not fail to build, it fails at runtime with an
         * empty result.
         */
        const val PERMISSION_READ_HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"

        /** So the daily sync can run without the app being opened. */
        const val PERMISSION_READ_IN_BACKGROUND =
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        /** The record types the protocol needs, and how to say what one contains. */
        val RECORD_TYPES: List<HealthRecordType> = listOf(
            HealthRecordType(
                type = StepsRecord::class,
                label = "Steps",
                why = "Against the daily step target.",
            ) { record -> "${(record as StepsRecord).count} steps" },

            HealthRecordType(
                type = SleepSessionRecord::class,
                label = "Sleep sessions",
                why = "Last night's start and end against the bedtime ramp.",
            ) { record ->
                val sleep = record as SleepSessionRecord
                val minutes = ChronoUnit.MINUTES.between(sleep.startTime, sleep.endTime)
                "${minutes / 60}h ${minutes % 60}m"
            },

            HealthRecordType(
                type = HeartRateRecord::class,
                label = "Heart rate",
                why = "Sleeping heart rate, and time in zone on cardio days.",
            ) { record ->
                val samples = (record as HeartRateRecord).samples
                val average = samples.map { it.beatsPerMinute }.average()
                "${samples.size} samples, mean ${average.toInt()} bpm"
            },

            HealthRecordType(
                type = RestingHeartRateRecord::class,
                label = "Resting heart rate",
                why = "A second read on the sleeping heart rate trend.",
            ) { record -> "${(record as RestingHeartRateRecord).beatsPerMinute} bpm" },

            HealthRecordType(
                type = HeartRateVariabilityRmssdRecord::class,
                label = "Heart rate variability",
                why = "Recovery, if the watch provides it.",
            ) { record ->
                val hrv = record as HeartRateVariabilityRmssdRecord
                "%.1f ms".format(hrv.heartRateVariabilityMillis)
            },

            HealthRecordType(
                type = ExerciseSessionRecord::class,
                label = "Workout sessions",
                why = "Matching a recorded session to a cardio day.",
            ) { record ->
                val session = record as ExerciseSessionRecord
                val minutes = ChronoUnit.MINUTES.between(session.startTime, session.endTime)
                "${session.title ?: "session"}, $minutes min"
            },

            HealthRecordType(
                type = WeightRecord::class,
                label = "Weight",
                why = "Merged with manual entries for the 7-day average.",
            ) { record -> "%.1f kg".format((record as WeightRecord).weight.inKilograms) },
        )
    }
}

/** One Health Connect record type, with a plain-language description of a record. */
class HealthRecordType(
    val type: KClass<out Record>,
    val label: String,
    /** Why the protocol wants it, so the Data Check screen is readable. */
    val why: String,
    private val describer: (Record) -> String,
) {
    val permission: String get() = HealthPermission.getReadPermission(type)

    fun describe(record: Record): String = try {
        describer(record)
    } catch (_: Exception) {
        "unreadable"
    }
}

enum class RecordState {
    /** Records exist over the window. */
    PRESENT,

    /** The permission is granted but the phone has nothing. */
    EMPTY,

    /** The permission was never granted, so nothing was even asked for. */
    NOT_GRANTED,

    /** The read itself failed. */
    FAILED,
}

data class RecordSurvey(
    val type: HealthRecordType,
    val state: RecordState,
    val count: Int = 0,
    val latest: String? = null,
    val failure: String? = null,
)
