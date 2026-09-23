package com.cutcoach.samsung

import android.app.Activity
import android.content.Context
import android.os.Build
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalDateFilter
import java.time.LocalDate

/**
 * Does this phone actually give up an Energy Score?
 *
 * The same move as the Data Check screen in Phase 2, and for the same reason:
 * Phase 5 exists entirely for Energy Score, so before anything is built on top
 * of it, find out what the phone really hands over. A day of integration on a
 * value that turns out to be absent is a day spent on nothing.
 *
 * Deliberately a probe rather than a feature. It reports, it does not decide,
 * and nothing in the rules engine reads it.
 *
 * Everything here is wrapped: the SDK is an optional .aar, developer mode can
 * be switched off in Samsung Health at any time, and neither is the owner's
 * fault. A missing class or a refused permission has to read as "not available"
 * rather than as a crash.
 */
object EnergyScoreProbe {

    /** The SDK's own manifest says 29; the rest of the app supports 28. */
    private const val MIN_API = 29

    /** What one day of Energy Score looks like, or why there is none. */
    data class Reading(val date: LocalDate, val score: Float?)

    data class Report(
        val available: Boolean,
        val headline: String,
        val detail: String,
        val readings: List<Reading> = emptyList(),
    )

    /**
     * Whether the SDK is even on the classpath.
     *
     * Guards the runtime, not the build. Without the .aar this file does not
     * compile at all, so this cannot catch a missing SDK on a fresh clone -- it
     * catches an API level below 29, and it is cheap insurance if the class ever
     * goes missing at runtime.
     */
    val isPresent: Boolean by lazy {
        Build.VERSION.SDK_INT >= MIN_API && runCatching {
            Class.forName("com.samsung.android.sdk.health.data.HealthDataService")
        }.isSuccess
    }

    /** The one permission this probe wants: read Energy Score, nothing else. */
    private fun energyScorePermission(): Set<Permission> =
        setOf(Permission.of(DataTypes.ENERGY_SCORE, AccessType.READ))

    /**
     * Ask Samsung Health for permission. Needs an Activity, because the SDK
     * shows its own sheet.
     */
    suspend fun requestPermission(activity: Activity): Boolean = runCatching {
        val store = HealthDataService.getStore(activity)
        val wanted = energyScorePermission()
        val granted = store.getGrantedPermissions(wanted)
        if (granted.containsAll(wanted)) return@runCatching true
        store.requestPermissions(wanted, activity).containsAll(wanted)
    }.getOrDefault(false)

    /**
     * Read the last [days] of Energy Score and say what came back.
     *
     * Every failure mode gets its own sentence, because "it did not work" is
     * useless when the causes are as different as a missing SDK, developer mode
     * being off, an ungranted permission, and a watch that has simply never
     * produced the value.
     */
    suspend fun run(context: Context, days: Int = 14): Report {
        if (Build.VERSION.SDK_INT < MIN_API) {
            return Report(
                available = false,
                headline = "Not available on this Android version.",
                detail = "The Samsung SDK needs API $MIN_API; this phone is " +
                    "API ${Build.VERSION.SDK_INT}.",
            )
        }
        if (!isPresent) {
            return Report(
                available = false,
                headline = "The Samsung SDK is not in this build.",
                detail = "Drop samsung-health-data-api-1.1.0.aar into app/libs " +
                    "and rebuild. The rest of the app does not need it.",
            )
        }

        return try {
            val store = HealthDataService.getStore(context)
            val wanted = energyScorePermission()
            val granted = store.getGrantedPermissions(wanted)

            if (!granted.containsAll(wanted)) {
                return Report(
                    available = false,
                    headline = "Permission not granted yet.",
                    detail = "Tap \"Ask Samsung Health\" below. If nothing appears, " +
                        "developer mode is off: Samsung Health, menu, Settings, " +
                        "About Samsung Health, tap the version line ten times.",
                )
            }

            val today = LocalDate.now()
            val request = DataTypes.ENERGY_SCORE
                .readDataRequestBuilder
                .setLocalDateFilter(LocalDateFilter.of(today.minusDays(days.toLong()), today))
                .build()

            val response = store.readData(request)
            val readings = response.dataList.map { point ->
                Reading(
                    // The value is a Float on Samsung's side, which is why this
                    // is not rounded here: the probe's job is to report what
                    // arrived, not to tidy it.
                    date = point.startTime.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                    score = runCatching {
                        point.getValue(DataType.EnergyScoreType.ENERGY_SCORE)
                    }.getOrNull(),
                )
            }.sortedByDescending { it.date }

            if (readings.isEmpty()) {
                Report(
                    available = true,
                    headline = "Connected, but no Energy Score in the last $days days.",
                    detail = "The SDK answered and the permission is granted, so the " +
                        "link works. Samsung computes Energy Score from sleep and " +
                        "activity, and it needs several nights of watch wear before " +
                        "it produces one.",
                )
            } else {
                Report(
                    available = true,
                    headline = "${readings.size} Energy Scores in the last $days days.",
                    detail = "Latest: ${readings.first().score ?: "no value"} on " +
                        "${readings.first().date}.",
                    readings = readings,
                )
            }
        } catch (e: Throwable) {
            // Throwable rather than Exception on purpose: a missing class from
            // an absent .aar arrives as an Error, and that must read as "not
            // available" rather than take the screen down.
            Report(
                available = false,
                headline = "The SDK refused.",
                detail = "${e.javaClass.simpleName}: ${e.message ?: "no message"}. " +
                    "Most often this means developer mode is off in Samsung Health.",
            )
        }
    }
}
