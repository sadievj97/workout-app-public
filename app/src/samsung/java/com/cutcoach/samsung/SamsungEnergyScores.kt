package com.cutcoach.samsung

import android.app.Activity
import android.content.Context
import com.cutcoach.health.EnergyScoreReport
import com.cutcoach.health.EnergyScoreSource
import com.cutcoach.health.EnergyScores

/**
 * Samsung answering [EnergyScoreSource].
 *
 * The only file in the app that both touches the Samsung SDK and is known to
 * anything else, and even here the knowing runs one way: this class imports
 * `health`, and nothing in `health` imports this.
 *
 * That is what lets the whole `src/samsung` source root be left out of the
 * build when the .aar is absent. The probe screen still draws, the readiness
 * rule still runs, and both simply see no source.
 */
class SamsungEnergyScores(private val context: Context) : EnergyScoreSource {

    override suspend fun recent(days: Int): List<Float> {
        val report = EnergyScoreProbe.run(context, days)
        if (!report.available) return emptyList()
        return report.readings.mapNotNull { it.score }
    }

    override suspend fun probe(days: Int): EnergyScoreReport =
        EnergyScoreProbe.run(context, days)

    override suspend fun requestPermission(activity: Activity): Boolean =
        EnergyScoreProbe.requestPermission(activity)

    companion object {
        /**
         * Called once at startup, reflectively, from MainActivity.
         *
         * Reflective because a direct call would be a compile-time reference to
         * this package, and then a clone without the .aar would not build.
         */
        fun register(context: Context) {
            if (!EnergyScoreProbe.isPresent) return
            EnergyScores.source = SamsungEnergyScores(context.applicationContext)
        }
    }
}
