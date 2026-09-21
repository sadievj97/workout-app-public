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

    private fun buzz(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    fun release() {
        tones?.release()
        tones = null
    }

    private companion object {
        const val TONE_VOLUME = 90
    }
}
