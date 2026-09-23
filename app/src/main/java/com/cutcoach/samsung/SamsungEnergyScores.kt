package com.cutcoach.samsung

import android.content.Context
import com.cutcoach.health.EnergyScoreSource
import com.cutcoach.health.EnergyScores

/**
 * Samsung answering [EnergyScoreSource].
 *
 * The only place the rules pipeline touches the Samsung SDK, and it touches it
 * through an interface it does not know is Samsung. That is what lets the
 * `samsung` folder be deleted on a build without the .aar without taking the
 * readiness rule down with it.
 */
class SamsungEnergyScores(private val context: Context) : EnergyScoreSource {

    override suspend fun recent(days: Int): List<Float> {
        val report = EnergyScoreProbe.run(context, days)
        if (!report.available) return emptyList()
        return report.readings.mapNotNull { it.score }
    }

    companion object {
        /**
         * Called once at startup.
         *
         * If the Samsung folder is deleted, this call is the one other line to
         * remove, which app/libs/README.md says.
         */
        fun register(context: Context) {
            if (!EnergyScoreProbe.isPresent) return
            EnergyScores.source = SamsungEnergyScores(context.applicationContext)
        }
    }
}
