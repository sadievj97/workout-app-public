package com.cutcoach.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cutcoach.MainActivity
import com.cutcoach.R

/**
 * Posting one evening reminder.
 *
 * Deliberately quiet machinery: one channel, no grouping, no actions, no
 * progress. A reminder at 21:00 has one job, which is to be read and then
 * ignored while you put the phone down.
 */
object ReminderNotifier {

    private const val CHANNEL_ID = "evening"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Evening reminders",
            // Default rather than high: this should appear, not interrupt.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "The wind-down steps that make a 05:00 wake possible."
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Whether the phone will actually show anything.
     *
     * Android 13 made notifications a runtime permission, so this can be false
     * even though everything else is set up correctly.
     */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context, reminder: PlannedReminder) {
        if (!canPost(context)) return
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(reminder.title)
            .setContentText(reminder.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        // One id per moment, so tonight's replaces last night's rather than
        // stacking a week of identical reminders in the shade.
        NotificationManagerCompat.from(context)
            .notify(reminder.momentId.hashCode(), notification)
    }
}
