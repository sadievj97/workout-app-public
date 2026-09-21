package com.cutcoach.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.session.SessionStep
import com.cutcoach.data.session.Side
import com.cutcoach.data.session.buildSessionPlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One set as it was actually performed. Step 6 writes these to the database. */
data class LoggedSet(
    val exerciseId: String,
    val setNumber: Int,
    val side: Side,
    val reps: Int? = null,
    val seconds: Int? = null,
)

/**
 * Drives one session: which step you are on, what the timer says, and what you
 * logged.
 *
 * Timed steps run themselves and move on at zero. Rep sets wait for you,
 * because only you know how many you managed.
 */
class SessionPlayerViewModel(
    private val repo: ProtocolRepository,
    val sessionId: String,
    val programWeek: Int?,
) : ViewModel() {

    val session = repo.session(sessionId)
    val steps: List<SessionStep> = session?.let { buildSessionPlan(repo, it, programWeek) } ?: emptyList()

    /** The planned length of this session in minutes, for the session clock. */
    val plannedMinutes: Int = repo.protocol.weeklySchedule.values
        .firstOrNull { it.sessionId == sessionId }?.durationMin?.maxInt ?: 0

    private val graceMinutes = repo.protocol.sessionDefaults.sessionOverrunGraceMin

    var index by mutableIntStateOf(0)
        private set

    /** Seconds left on the current timed step. Zero on a rep set. */
    var secondsLeft by mutableIntStateOf(0)
        private set

    /** Whether the current timed step is counting. */
    var running by mutableStateOf(false)
        private set

    /** Seconds since the session began, for the session clock. */
    var elapsedSeconds by mutableIntStateOf(0)
        private set

    /** Bumped every time the app should make a noise. The screen watches it. */
    var chimeCount by mutableIntStateOf(0)
        private set

    var finishChimed by mutableStateOf(false)
        private set

    /** What you have logged so far this session. */
    val logged = mutableStateListOf<LoggedSet>()

    /** The reps field for the current rep set, pre-filled with the target floor. */
    var repsInput by mutableIntStateOf(0)
        private set

    val current: SessionStep get() = steps.getOrElse(index) { SessionStep.Finished }

    val isFinished: Boolean get() = current is SessionStep.Finished

    /** True once the session has run past its planned length plus the grace period. */
    val isOverrunning: Boolean
        get() = plannedMinutes > 0 && elapsedSeconds > (plannedMinutes + graceMinutes) * 60

    init {
        prepareStep()
        // One ticker for the whole session: it drives both the step countdown
        // and the session clock, so they can never drift apart.
        viewModelScope.launch {
            while (true) {
                delay(1000)
                elapsedSeconds += 1
                if (running && secondsLeft > 0) {
                    secondsLeft -= 1
                    if (secondsLeft == 0) {
                        running = false
                        onTimerReachedZero()
                    }
                }
            }
        }
    }

    // ---- Moving through the session ---------------------------------------

    private fun prepareStep() {
        when (val step = current) {
            is SessionStep.Warmup -> startCountdown(step.seconds)
            is SessionStep.Rest -> startCountdown(step.seconds)
            is SessionStep.SwitchSides -> startCountdown(step.seconds)
            is SessionStep.HoldSet -> startCountdown(step.seconds)
            is SessionStep.RepSet -> {
                running = false
                secondsLeft = 0
                repsInput = defaultRepsFor(step)
            }
            SessionStep.Finished -> {
                running = false
                secondsLeft = 0
                if (!finishChimed) {
                    finishChimed = true
                    chimeCount += 1
                }
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        secondsLeft = seconds
        running = true
    }

    private fun onTimerReachedZero() {
        chimeCount += 1
        when (val step = current) {
            // A hold that ran the full length counts as the full length.
            is SessionStep.HoldSet -> {
                logged += LoggedSet(step.exerciseId, step.setNumber, step.side, seconds = step.seconds)
                advance()
            }
            is SessionStep.Warmup, is SessionStep.Rest, is SessionStep.SwitchSides -> advance()
            else -> Unit
        }
    }

    private fun advance() {
        if (index < steps.lastIndex) {
            index += 1
            prepareStep()
        }
    }

    /** Skip the current step without logging anything. */
    fun skip() {
        // A hold skipped early still records what was actually held.
        val step = current
        if (step is SessionStep.HoldSet) {
            val held = step.seconds - secondsLeft
            if (held > 0) {
                logged += LoggedSet(step.exerciseId, step.setNumber, step.side, seconds = held)
            }
        }
        advance()
    }

    /** Stop a hold early, recording how long it actually lasted. */
    fun finishHoldNow() {
        val step = current as? SessionStep.HoldSet ?: return
        val held = step.seconds - secondsLeft
        logged += LoggedSet(step.exerciseId, step.setNumber, step.side, seconds = held)
        advance()
    }

    fun pauseOrResume() {
        if (secondsLeft > 0) running = !running
    }

    /** Add time back to a rest period that ended too soon. */
    fun addSeconds(extra: Int) {
        if (current is SessionStep.RepSet || current is SessionStep.Finished) return
        secondsLeft += extra
        running = true
    }

    // ---- Rep sets ---------------------------------------------------------

    fun changeReps(delta: Int) {
        repsInput = (repsInput + delta).coerceAtLeast(0)
    }

    /** Record the current rep set and move on to the rest period. */
    fun logReps() {
        val step = current as? SessionStep.RepSet ?: return
        logged += LoggedSet(step.exerciseId, step.setNumber, step.side, reps = repsInput)
        advance()
    }

    /**
     * What to pre-fill the rep counter with: the bottom of the target range, or
     * whatever you managed last time in this session for a max-reps exercise.
     */
    private fun defaultRepsFor(step: SessionStep.RepSet): Int {
        step.target.min?.let { return it }
        return logged.lastOrNull { it.exerciseId == step.exerciseId && it.reps != null }?.reps
            ?: FALLBACK_MAX_REPS_START
    }

    fun exerciseName(id: String): String = repo.exercise(id)?.name ?: id

    fun exercise(id: String) = repo.exercise(id)

    private companion object {
        /** Only used for a max-reps exercise on its first set of a session. */
        const val FALLBACK_MAX_REPS_START = 8
    }
}
