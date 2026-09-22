package com.cutcoach.rules

import android.content.Context
import com.cutcoach.data.db.BodyMetricEntity
import com.cutcoach.data.db.TrainingRepository
import com.cutcoach.data.db.sevenDayAverageKg
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.settings.SettingsRepository
import com.cutcoach.health.DailyHealth
import com.cutcoach.health.HealthConnectManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * Gathering everything the rules need, then asking them.
 *
 * The rules themselves are pure and testable; this is the part that goes to the
 * database, the settings and the watch. Keeping the two apart is what lets the
 * twelve rule tests run on a laptop with no phone attached.
 */
suspend fun adviceForToday(
    context: Context,
    repo: ProtocolRepository,
    today: LocalDate,
    todayHealth: DailyHealth?,
    lastNight: DailyHealth?,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Recommendation> {
    val training = TrainingRepository.get(context)
    val settings = SettingsRepository.get(context)

    val body = training.recentBody(today, days = BODY_DAYS).first()
    val rungs = training.ladderSteps.first()

    // The last session's sets for each exercise the week actually schedules.
    // Rehab is not among them: it lives in Settings and has no ladder, which is
    // the rehab_untouched guardrail holding by construction rather than by a
    // check that could be forgotten.
    val scheduled = repo.protocol.sessions.values
        .flatMap { it.exercises }
        .map { it.exerciseId }
        .distinct()

    val lastSets = scheduled.associateWith { id ->
        training.lastSessionSets(id).map { SetResult(reps = it.reps, seconds = it.seconds) }
    }.filterValues { it.isNotEmpty() }

    return recommendationsFor(
        repo = repo,
        input = RuleInput(
            today = today,
            programWeek = repo.programWeek(today),
            weeklyWeightKg = weeklyAverages(body, today),
            weeklyWaistCm = weeklyWaists(body, today),
            today_ = todayHealth,
            lastNight = lastNight,
            sleepingHrBaseline = sleepingHrBaseline(context, repo, today, zone),
            pain = settings.painReport.first(),
            lastPlateauChange = settings.lastPlateauChange.first(),
            rehabExercises = settings.rehabExercises.first(),
            lastSets = lastSets,
            rungs = rungs,
            zone = zone,
        ),
    )
}

/** Enough days to cover the plateau window with a week to spare. */
private const val BODY_DAYS = 40

/**
 * The 7-day average at each week end, newest first.
 *
 * Each entry is the same rolling average the Body screen shows, taken a week
 * further back each time, so the plateau rule compares like with like.
 */
private fun weeklyAverages(body: List<BodyMetricEntity>, today: LocalDate): List<Double?> =
    (0..WEEKS_BACK).map { week -> body.sevenDayAverageKg(today.minusWeeks(week.toLong())) }

/** Sunday waist measurements, newest first, one per week. */
private fun weeklyWaists(body: List<BodyMetricEntity>, today: LocalDate): List<Double?> =
    (0..WEEKS_BACK).map { week ->
        val from = today.minusWeeks(week.toLong() + 1)
        val to = today.minusWeeks(week.toLong())
        body.filter { row ->
            val date = runCatching { LocalDate.parse(row.date) }.getOrNull()
            date != null && date.isAfter(from) && !date.isAfter(to)
        }.firstNotNullOfOrNull { it.waistCm }
    }

/** Four weeks covers the three-week plateau window and the week before it. */
private const val WEEKS_BACK = 4

/**
 * Sleeping heart rates over the readiness rule's own baseline window.
 *
 * Read fresh rather than cached: the rule refuses to speak without a full
 * window, so a short read simply keeps it quiet, which is the correct failure.
 */
private suspend fun sleepingHrBaseline(
    context: Context,
    repo: ProtocolRepository,
    today: LocalDate,
    zone: ZoneId,
): List<Int> {
    val manager = HealthConnectManager(context)
    if (manager.availability != HealthConnectManager.Availability.AVAILABLE) return emptyList()
    if (manager.grantedPermissions().isEmpty()) return emptyList()

    return manager.sleepingHeartRateBaseline(
        endingOn = today,
        nights = repo.protocol.readinessAdjustment.baselineWindowDays,
        zone = zone,
    )
}
