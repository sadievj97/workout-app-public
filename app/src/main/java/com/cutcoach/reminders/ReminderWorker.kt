package com.cutcoach.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cutcoach.data.protocol.loadFromAssets
import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Fires one reminder, then books the next one.
 *
 * A chain of one-shot jobs rather than a repeating one, because the bedtime
 * target moves as the ramp progresses and each firing has to look the next time
 * up again rather than assume it is the same as tonight's.
 *
 * WorkManager rather than AlarmManager: exact alarms need a permission meant
 * for alarm clocks, and these are reminders. The trade is that Android may
 * delay one by a few minutes, which for "lamps only" is fine.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val momentId = inputData.getString(KEY_MOMENT_ID)
        val protocol = ProtocolRepository.loadFromAssets(applicationContext)
        if (protocol !is ProtocolLoad.Success) return Result.failure()

        val settings = SettingsRepository.get(applicationContext)
        if (!settings.remindersOn.first()) {
            // Switched off while this was waiting. Say nothing and stop.
            return Result.success()
        }

        val chosen = settings.reminderChoices.first()
        val now = LocalDateTime.now()

        val fired = momentId?.let { id ->
            protocol.repository.reminderMoments(now.toLocalDate()).firstOrNull { it.id == id }
        }

        // Post the one that came due, if it is still switched on.
        val bedtime = bedtimeOn(protocol.repository::reminderMoments, now.toLocalDate())
        fired
            ?.takeIf { chosen[it.id] ?: it.onByDefault }
            ?.let { moment ->
                ReminderNotifier.post(applicationContext, reminderFor(moment, now, bedtime))
            }

        // Book the next from just after the one that fired, not from the clock.
        // WorkManager can run a job a moment early, and rescheduling from an
        // early "now" would book the same reminder again and spin.
        val firedAt = fired?.let { LocalDateTime.of(now.toLocalDate(), it.time).plusSeconds(1) }
        val basis = if (firedAt != null && firedAt.isAfter(now)) firedAt else now

        schedule(applicationContext, protocol.repository, chosen, basis)
        return Result.success()
    }

    companion object {
        private const val KEY_MOMENT_ID = "moment_id"
        private const val NAME = "cutcoach-next-reminder"

        /**
         * Books the next reminder due, and only that one.
         *
         * Each firing books its successor, so a single unique job walks through
         * the evening rather than a queue of jobs going stale in the background.
         */
        fun schedule(
            context: Context,
            repo: ProtocolRepository,
            chosen: Map<String, Boolean>,
            now: LocalDateTime = LocalDateTime.now(),
        ) {
            val next = plan(repo::reminderMoments, chosen, now).firstOrNull()
                ?: return cancel(context)

            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayUntil(next.at, now).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_MOMENT_ID to next.momentId))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                // REPLACE, so changing a switch in Settings re-aims the next one
                // instead of leaving the old time booked.
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
