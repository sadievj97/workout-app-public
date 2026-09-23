package com.cutcoach.health

/**
 * Where Energy Scores come from, without the rules knowing who provides them.
 *
 * This exists so that deleting `com/cutcoach/samsung/` leaves the rest of the
 * app compiling. The Samsung SDK is an optional .aar that is not in the
 * repository, and the readiness rule — which the protocol approved long before
 * any of this — must not become impossible to build without it.
 *
 * The rules ask this interface. Samsung answers it, or nobody does.
 */
interface EnergyScoreSource {

    /**
     * Recent daily scores, newest first, or empty when there are none.
     *
     * Empty rather than an error: no Energy Score is an ordinary state, not a
     * failure. Developer mode gets switched off, watches go unworn, and Samsung
     * needs a night before it computes anything.
     */
    suspend fun recent(days: Int): List<Float>
}

/**
 * The one source, if there is one.
 *
 * Registered at startup by whoever can provide it. Null is the normal state on
 * a build without the Samsung SDK, and every caller has to cope with that.
 */
object EnergyScores {

    @Volatile
    var source: EnergyScoreSource? = null

    /** Newest first, empty when nothing can provide them. */
    suspend fun recent(days: Int): List<Float> =
        runCatching { source?.recent(days).orEmpty() }.getOrDefault(emptyList())
}
