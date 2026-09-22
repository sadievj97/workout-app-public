package com.cutcoach.health

import android.content.Context
import com.cutcoach.data.protocol.ProtocolRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today's watch data, and last night's.
 *
 * They are separate reads because the night belongs to the evening before: at
 * 05:35 on Tuesday, "last night" is Monday's night, and that is the one the
 * bedtime ramp is judged against.
 *
 * All the protocol's numbers — the zones, the ramp target, whether today is an
 * interval day — come from protocol.json rather than being written here.
 */
suspend fun readTodayAndLastNight(
    context: Context,
    repo: ProtocolRepository,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<DailyHealth?, DailyHealth?> {
    val manager = HealthConnectManager(context)
    if (manager.availability != HealthConnectManager.Availability.AVAILABLE) return null to null
    if (manager.grantedPermissions().isEmpty()) return null to null

    val heartRate = repo.protocol.heartRate
    val zone2 = heartRate.zone2Bpm.minInt..heartRate.zone2Bpm.maxInt
    val intervals = heartRate.intervalsBpm.minInt..heartRate.intervalsBpm.maxInt
    val napThreshold = repo.napThreshold

    suspend fun read(date: LocalDate): DailyHealth = manager.readDay(
        date = date,
        zone = zone,
        zone2 = zone2,
        intervals = intervals,
        lightsOutTarget = repo.lightsOutOn(date),
        isIntervalDay = repo.scheduleFor(date.dayOfWeek)?.sessionId == INTERVAL_SESSION,
        napThreshold = napThreshold,
    )

    return read(today) to read(today.minusDays(1))
}

/** The Saturday session, which is measured against the interval band rather than Zone 2. */
private const val INTERVAL_SESSION = "intervals"
