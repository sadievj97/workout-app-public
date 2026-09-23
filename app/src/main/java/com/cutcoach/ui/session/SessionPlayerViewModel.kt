package com.cutcoach.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutcoach.data.db.TrainingRepository
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.settings.SettingsRepository
import com.cutcoach.data.session.SessionStep
import com.cutcoach.data.session.Side
import com.cutcoach.data.session.buildSessionPlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    private val training: TrainingRepository,
    private val settings: SettingsRepository,
    val sessionId: String,
    val programWeek: Int?,
) : ViewModel() {

    /**
     * Rest is the one duration the owner can override in Settings. It is read
     * when each rest starts rather than baked into the plan, so changing it
     * mid-session applies from the next set instead of nothing.
     */
    private var effectiveRestSeconds = repo.restSeconds

    /** The database row for this session, created as soon as the player opens. */
    private var sessionLogId: Long? = null

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

    /** Sets logged in the first moments before the database row exists. */
    private val pending = mutableListOf<LoggedSet>()

    /** The reps field for the current rep set, pre-filled with the target floor. */
    var repsInput by mutableIntStateOf(0)
        private set

    val current: SessionStep get() = steps.getOrElse(index) { SessionStep.Finished }

    val isFinished: Boolean get() = current is SessionStep.Finished

    /** True once the session has run past its planned length plus the grace period. */
    val isOverrunning: Boolean
        get() = plannedMinutes > 0 && elapsedSeconds > (plannedMinutes + graceMinutes) * 60

    /**
     * Which rung each exercise is on, so every set records how hard it was.
     *
     * Declared here, above [init], and it has to stay above it. Kotlin runs
     * property initialisers and init blocks in source order, and viewModelScope
     * dispatches on Main.immediate — so a coroutine launched from init while
     * already on the main thread starts running *synchronously*, before any
     * property declared further down exists. This map used to be declared 150
     * lines below, and the init block read it on the way past: every session
     * containing a rep or a hold died with a NullPointerException the moment
     * Start was pressed. Cardio days survived, because they have neither, which
     * is why it stayed hidden.
     */
    private val ladderSteps = mutableMapOf<String, Int>()

    init {
        prepareStep()

        // The session row is created up front, so a session abandoned halfway
        // still leaves a record of what was done.
        viewModelScope.launch {
            sessionLogId = training.startSession(
                date = LocalDate.now(),
                sessionId = sessionId,
                sessionName = session?.name ?: sessionId,
                programWeek = programWeek,
                isDeload = programWeek != null && repo.isDeloadWeek(programWeek),
                plannedMinutes = plannedMinutes,
            )
            // Anything logged before the row existed still gets written.
            pending.forEach { persist(it) }
            pending.clear()
        }

        viewModelScope.launch {
            settings.restSecondsOverride.collect { effectiveRestSeconds = it ?: repo.restSeconds }
        }

        // The rung each exercise is on, so every set records how hard it was.
        viewModelScope.launch {
            steps.forEach { step ->
                val id = when (step) {
                    is SessionStep.RepSet -> step.exerciseId
                    is SessionStep.HoldSet -> step.exerciseId
                    else -> null
                } ?: return@forEach
                if (id !in ladderSteps) ladderSteps[id] = training.ladderStep(id)
            }
        }

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
            is SessionStep.Rest -> startCountdown(effectiveRestSeconds)
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
                    viewModelScope.launch { sessionLogId?.let { training.endSession(it) } }
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
                record(LoggedSet(step.exerciseId, step.setNumber, step.side, seconds = step.seconds))
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

    // ---- Recording --------------------------------------------------------

    /** How many sets have been written down so far, for the leave dialog. */
    val loggedSetCount: Int get() = logged.size

    /**
     * Close the session without walking the rest of the steps.
     *
     * For leaving part way through having actually done the work — the last
     * two exercises away from the phone, or the phone put down. It writes an
     * end time and nothing else: the sets that were logged stay, and the ones
     * that were not are not invented, so this records a real session with an
     * incomplete log rather than a fabricated complete one.
     */
    fun endSessionNow() {
        viewModelScope.launch { sessionLogId?.let { training.endSession(it) } }
    }

    /**
     * Keeps a set on screen and writes it to the database.
     *
     * The screen list is updated first so logging never feels like it is
     * waiting on storage, which matters when you are counting down a rest.
     */
    private fun record(set: LoggedSet) {
        logged += set
        val id = sessionLogId
        if (id == null) {
            pending += set
        } else {
            viewModelScope.launch { persist(set) }
        }
    }

    private suspend fun persist(set: LoggedSet) {
        val id = sessionLogId ?: return
        training.logSet(
            sessionLogId = id,
            exerciseId = set.exerciseId,
            setNumber = set.setNumber,
            side = set.side.name,
            reps = set.reps,
            seconds = set.seconds,
            ladderStep = ladderSteps[set.exerciseId] ?: 0,
        )
    }

    /** Skip the current step without logging anything. */
    fun skip() {
        // A hold skipped early still records what was actually held.
        val step = current
        if (step is SessionStep.HoldSet) {
            val held = step.seconds - secondsLeft
            if (held > 0) {
                record(LoggedSet(step.exerciseId, step.setNumber, step.side, seconds = held))
            }
        }
        advance()
    }

    /** Stop a hold early, recording how long it actually lasted. */
    fun finishHoldNow() {
        val step = current as? SessionStep.HoldSet ?: return
        val held = step.seconds - secondsLeft
        record(LoggedSet(step.exerciseId, step.setNumber, step.side, seconds = held))
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
        record(LoggedSet(step.exerciseId, step.setNumber, step.side, reps = repsInput))
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
