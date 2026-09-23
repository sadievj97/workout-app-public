package com.cutcoach.ui.session

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The sound and buzz at zero.
 *
 * It uses the alarm stream deliberately: at 05:35 the phone is often on the
 * floor on silent, and the end of a rest period is the one thing that must get
 * through. The tone is short so it does not wake the house.
 */
class Chime(context: Context) {

    private val appContext = context.applicationContext

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = appContext.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Vibrator::class.java)
    }

    private var tones: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_ALARM, TONE_VOLUME)
    } catch (_: RuntimeException) {
        // Some devices refuse a tone generator when another app holds audio.
        // A missing beep is not worth crashing a session for.
        null
    }

    /** One short buzz and beep: a phase has ended. */
    fun ring() {
        buzz(longArrayOf(0, 180))
        tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    /** A double buzz and a longer tone: the session is over. */
    fun ringFinish() {
        buzz(longArrayOf(0, 160, 120, 160))
        tones?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
    }

    /**
     * Buzz, or do not buzz. Never crash.
     *
     * hasVibrator() answers "is there hardware", not "am I allowed", so on a
     * build that had not declared VIBRATE this passed and the call that
     * followed threw SecurityException — on the main thread, inside the session
     * player, every time a timer reached zero. The app died mid-workout and
     * relaunched onto the same finished timer, so it died again: "CutCoach keeps
     * stopping".
     *
     * The manifest now declares the permission, which is the actual fix. This
     * catch is the second one, on the same reasoning already written above the
     * tone generator: a missing buzz is not worth losing a session over. That
     * reasoning was applied to the beep and not to the buzz, and it was the buzz
     * that brought the app down.
     */
    private fun buzz(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: RuntimeException) {
            // SecurityException if the permission is missing, IllegalState on
            // some devices while a call is in progress. Neither is the owner's
            // problem at 05:35.
        }
    }

    fun release() {
        tones?.release()
        tones = null
    }

    private companion object {
        const val TONE_VOLUME = 90
    }
}
