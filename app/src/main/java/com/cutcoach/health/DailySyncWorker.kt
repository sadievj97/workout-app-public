package com.cutcoach.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cutcoach.data.db.TrainingRepository
import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * The daily read, so last night's numbers are there when the app opens rather
 * than being fetched while you stand over the phone at 05:35.
 *
 * This is what the background read permission is for. It writes only the one
 * thing worth keeping locally: the weight the watch or scale recorded, merged
 * into the same table as manual entries. Everything else is read fresh, because
 * a cached step count goes stale within the hour.
 */
class DailySyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = HealthConnectManager(applicationContext)
        if (manager.availability != HealthConnectManager.Availability.AVAILABLE) {
            // Nothing to retry against: Health Connect is not there.
            return Result.success()
        }
        if (!manager.hasAllPermissions()) return Result.success()

        val protocol = ProtocolRepository.load(applicationContext)
        if (protocol !is ProtocolLoad.Success) return Result.failure()

        return try {
            val today = LocalDate.now()
            val (todayHealth, lastNight) = readTodayAndLastNight(
                context = applicationContext,
                repo = protocol.repository,
                today = today,
            )

            // Weight is the only value worth storing: it feeds a 7-day average
            // that has to survive the watch not syncing on a given day. Manual
            // entries still win, so this never overwrites one.
            val training = TrainingRepository.get(applicationContext)
            todayHealth?.weightKg?.let { kg ->
                if (training.bodyOn(today)?.weightKg == null) {
                    training.recordWeight(today, kg)
                }
            }
            lastNight?.weightKg?.let { kg ->
                val yesterday = today.minusDays(1)
                if (training.bodyOn(yesterday)?.weightKg == null) {
                    training.recordWeight(yesterday, kg)
                }
            }

            Result.success()
        } catch (_: Exception) {
            // A failed read is worth another go later, not a crash.
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "cutcoach-daily-health-sync"

        /**
         * Runs about once a day. WorkManager decides exactly when, which is the
         * right trade: the app does not need a precise time, and fighting the
         * scheduler costs battery.
         *
         * Samsung's battery optimisation can still put this to sleep. If the
         * numbers stop appearing, exempt CutCoach in the phone's settings.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
