package com.cutcoach

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.cutcoach.data.protocol.loadFromAssets
import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.settings.SettingsRepository
import com.cutcoach.health.DailySyncWorker
import com.cutcoach.reminders.ReminderNotifier
import com.cutcoach.reminders.ReminderWorker
import com.cutcoach.ui.CutCoachApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The only activity. Android launches this class; everything else is Compose.
 *
 * `enableEdgeToEdge` lets the navy background run under the status bar so the
 * screen has no hard edge at 05:35; [CutCoachApp] then insets the content so
 * nothing hides behind it.
 */
class MainActivity : ComponentActivity() {

    private val askForNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Declined is a real answer, not a failure. The reminders simply
            // stay silent; nothing else in the app depends on this.
            if (granted) bookReminders()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Asked for once the app has been opened, so the daily read is already
        // running by the time last night's numbers matter. KEEP means opening
        // the app repeatedly does not reschedule it.
        DailySyncWorker.schedule(this)

        ReminderNotifier.ensureChannel(this)
        registerEnergyScores()
        bookReminders()

        setContent { CutCoachApp() }
    }

    override fun onStart() {
        super.onStart()
        requestNotificationsIfNeeded()
    }

    /**
     * Hand Samsung's Energy Score to the rules, if this build has it.
     *
     * Reflection for exactly one call, which is not a thing to do lightly. The
     * alternative is a direct reference, and that makes a fresh clone without
     * the optional Samsung .aar fail to compile — a bad first minute for anyone
     * reading the repository, in exchange for a feature they may not want.
     *
     * The Gradle build excludes the whole `samsung` source set when the .aar is
     * absent, so the class genuinely is not there and this genuinely does
     * nothing. Everything else about Energy Score goes through
     * [com.cutcoach.health.EnergyScoreSource], an ordinary typed interface.
     */
    private fun registerEnergyScores() {
        runCatching {
            Class.forName("com.cutcoach.samsung.SamsungEnergyScores\$Companion")
            val holder = Class.forName("com.cutcoach.samsung.SamsungEnergyScores")
            val companion = holder.getField("Companion").get(null)
            companion.javaClass
                .getMethod("register", android.content.Context::class.java)
                .invoke(companion, this)
        }
    }

    /**
     * Android 13 made notifications a runtime permission.
     *
     * Asked from onStart rather than onCreate: a request made while the phone
     * is still locked is answered by the system before anyone sees it, and
     * onCreate does not run again when the app is merely brought forward. A
     * reminder the owner never sees is worse than no reminder at all.
     *
     * Android stops showing the dialog after two dismissals, which is why
     * Settings also says plainly when notifications are blocked.
     */
    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ReminderNotifier.canPost(this)) return
        askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Re-aims the next reminder on every launch.
     *
     * The bedtime target moves as the ramp progresses, and a job booked a week
     * ago would fire at a target that has since changed.
     */
    private fun bookReminders() {
        lifecycleScope.launch {
            val protocol = ProtocolRepository.loadFromAssets(applicationContext)
            if (protocol !is ProtocolLoad.Success) return@launch

            val settings = SettingsRepository.get(applicationContext)
            if (!settings.remindersOn.first()) {
                ReminderWorker.cancel(applicationContext)
                return@launch
            }
            ReminderWorker.schedule(
                context = applicationContext,
                repo = protocol.repository,
                chosen = settings.reminderChoices.first(),
            )
        }
    }
}
