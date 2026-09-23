package com.cutcoach.health

import android.app.Activity
import java.time.LocalDate

/**
 * Where Energy Scores come from, without the rest of the app knowing who
 * provides them.
 *
 * Samsung's Energy Score is not in Health Connect, so it needs Samsung's own
 * SDK — a licensed .aar that is downloaded by hand and is not in this
 * repository. Everything SDK-specific lives in the `samsung` source root, which
 * the build only compiles when that file is present.
 *
 * So this interface carries everything a screen or a rule could want, including
 * the diagnostics. The alternative is a UI that imports the SDK, and then a
 * clone without the .aar does not compile — a bad first minute for a reader, in
 * exchange for a feature they may not want.
 */
interface EnergyScoreSource {

    /**
     * Recent daily scores, newest first, or empty when there are none.
     *
     * Empty rather than an error: no Energy Score is an ordinary state. Watches
     * go unworn, developer mode gets switched off, and Samsung needs a night
     * before it computes anything.
     */
    suspend fun recent(days: Int): List<Float>

    /** What the provider can say for itself, for the probe screen. */
    suspend fun probe(days: Int): EnergyScoreReport

    /** Ask for permission. Needs an Activity, because the provider shows a sheet. */
    suspend fun requestPermission(activity: Activity): Boolean
}

/** One day's score. */
data class EnergyScoreReading(val date: LocalDate, val score: Float?)

/**
 * What a probe found, in words the owner can act on.
 *
 * Each failure gets its own sentence, because "it did not work" is useless when
 * the causes are as different as a missing SDK, developer mode being off, an
 * ungranted permission, and a watch that has simply never produced the value.
 * The last of those is not a failure at all.
 */
data class EnergyScoreReport(
    val available: Boolean,
    val headline: String,
    val detail: String,
    val readings: List<EnergyScoreReading> = emptyList(),
)

/**
 * The one source, if there is one.
 *
 * Registered at startup by whoever can provide it. Null is the normal state on
 * a build without the Samsung SDK, and every caller copes with that.
 */
object EnergyScores {

    @Volatile
    var source: EnergyScoreSource? = null

    /** Newest first, empty when nothing can provide them. */
    suspend fun recent(days: Int): List<Float> =
        runCatching { source?.recent(days).orEmpty() }.getOrDefault(emptyList())

    /** What this build can say about Energy Score. */
    suspend fun probe(days: Int = 14): EnergyScoreReport =
        source?.let { runCatching { it.probe(days) }.getOrNull() }
            ?: EnergyScoreReport(
                available = false,
                headline = "Not built with the Samsung SDK.",
                detail = "Energy Score needs Samsung's own SDK, which is a licensed " +
                    "download and is not in this repository. See app/libs/README.md. " +
                    "Nothing else in the app depends on it.",
            )
}
