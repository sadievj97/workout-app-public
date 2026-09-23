package com.cutcoach.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.settings.SettingsRepository
import com.cutcoach.reminders.ReminderNotifier
import com.cutcoach.rules.PainKind
import com.cutcoach.reminders.ReminderWorker
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.CutIcon
import com.cutcoach.ui.components.CutIconView
import com.cutcoach.ui.components.LabelledValue
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.Sky
import com.cutcoach.ui.theme.TextHigh
import com.cutcoach.ui.theme.TextMid
import com.cutcoach.ui.theme.Surface
import com.cutcoach.ui.theme.SurfaceEdge
import com.cutcoach.ui.theme.Ember
import com.cutcoach.ui.theme.tabular
import com.cutcoach.ui.theme.Ink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The few things worth changing from the phone.
 *
 * Everything else lives in protocol.json, which stays the source of truth.
 * What is here are overrides that sit on top of it, each with a way back to the
 * protocol's own value.
 */
@Composable
fun SettingsScreen(
    repo: ProtocolRepository,
    onOpenFigures: () -> Unit,
    onOpenDataCheck: () -> Unit,
    onOpenLadders: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenEnergyProbe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    val scope = rememberCoroutineScope()

    val restOverride by settings.restSecondsOverride.collectAsStateWithLifecycle(null)
    val rehabText by settings.rehabExercisesRaw.collectAsStateWithLifecycle("")
    val videoUrls by settings.allVideoUrls.collectAsStateWithLifecycle(emptyMap())

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, color = TextHigh)

        PainCard()

        CutCard {
            SectionLabel("Claude")
            NavRow(
                icon = CutIcon.CLOCK,
                label = "Weekly review: the week in plain language",
                onClick = onOpenReview,
            )
        }

        CutCard {
            SectionLabel("Samsung Health")
            NavRow(
                icon = CutIcon.SUNRISE,
                label = "Energy Score probe: does this phone provide one?",
                onClick = onOpenEnergyProbe,
            )
            Text(
                "Energy Score is not in Health Connect, so it needs Samsung's own " +
                    "SDK and developer mode. Nothing depends on it yet.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
        }

        CutCard {
            SectionLabel("Difficulty")
            NavRow(
                icon = CutIcon.LADDER,
                label = "Starting rungs: where you begin on each ladder",
                onClick = onOpenLadders,
            )
            Text(
                "Everything starts on its first rung. If you can already do the " +
                    "harder version, say so here rather than working up to where " +
                    "you already are.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
        }

        CutCard {
            SectionLabel("Figures")
            NavRow(
                icon = CutIcon.PERSON,
                label = "Form check: review all fifteen figures",
                onClick = onOpenFigures,
            )
        }

        CutCard {
            SectionLabel("Watch data")
            NavRow(
                icon = CutIcon.HEART,
                label = "Data check: what Health Connect actually has",
                onClick = onOpenDataCheck,
            )
            Text(
                "Read only. Nothing in the app depends on watch data yet.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
        }

        EveningReminders(repo)

        // Read-only, and the screen says why.
        CutCard {
            SectionLabel("The program")
            LabelledValue("Starts", repo.protocol.meta.programStartDate ?: "not set")
            LabelledValue("Bedtime ramp night 1", repo.protocol.sleep.rampStartDate ?: "not set")
            LabelledValue(
                "Length",
                repo.protocol.meta.programLengthWeeks
                    ?.let { "$it weeks" }
                    ?: "open-ended, to ${repo.protocol.meta.goal.targetWeightKg.describe("kg")}",
            )
            LabelledValue("Wake", repo.protocol.sleep.wakeTime)
            Text(
                "These are set in protocol.json, not here. Moving a start date after " +
                    "the fact would renumber every session you have already logged, so " +
                    "it should be a deliberate edit to the file.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
        }

        CutCard {
            SectionLabel("Rest between sets")
            val protocolRest = repo.protocol.sessionDefaults.restSeconds
            var input by remember(restOverride) {
                mutableStateOf((restOverride ?: protocolRest).toString())
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.matches(Regex("^\\d{0,3}$"))) input = it },
                    label = { Text("Seconds", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.tabular(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColours(),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        input.toIntOrNull()?.takeIf { it > 0 }?.let {
                            scope.launch { settings.setRestSeconds(it) }
                        }
                    },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ember,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Save", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (restOverride != null && restOverride != protocolRest) {
                TextButton(onClick = { scope.launch { settings.setRestSeconds(null) } }) {
                    Text(
                        "Back to the protocol's ${protocolRest}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = Sky,
                    )
                }
            }
        }

        // Rehab is typed in and only ever displayed.
        CutCard {
            SectionLabel("Sunday rehab exercises")
            var draft by remember(rehabText) { mutableStateOf(rehabText) }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("One per line", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = fieldColours(),
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { scope.launch { settings.setRehabExercises(draft) } },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ember,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "Typed in from your rehab program. The app only ever displays these: " +
                    "no recommendation in any phase will change or substitute rehab work.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
        }

        CutCard {
            SectionLabel("Demo video links")
            Text(
                "Paste a link you trust for each exercise. The cue card shows a " +
                    "button only once a link is set.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
            Box(Modifier.height(4.dp))
            repo.library.exercises.forEach { exercise ->
                val segment by settings.videoSegment(exercise.id)
                    .collectAsStateWithLifecycle(null)
                VideoLinkRow(
                    name = exercise.name,
                    current = videoUrls[exercise.id] ?: exercise.videoUrl,
                    startSeconds = segment?.first ?: exercise.videoStartS,
                    endSeconds = segment?.second ?: exercise.videoEndS,
                    onSave = { scope.launch { settings.setVideoUrl(exercise.id, it) } },
                    onSaveSegment = { start, end ->
                        scope.launch { settings.setVideoSegment(exercise.id, start, end) }
                    },
                )
            }
        }
    }
}

/**
 * A row that opens another screen.
 *
 * The icon is here because Settings is a list of destinations that all look
 * alike in words: at a glance the ladder, the figure and the pulse line tell
 * them apart faster than reading three similar sentences does.
 */
@Composable
private fun NavRow(icon: CutIcon, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CutIconView(icon, Sky, size = 22.dp)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Sky)
    }
}

/**
 * Reporting pain, which stops the app suggesting any training at all.
 *
 * It expires at the end of the day rather than sticking, because the guardrail
 * is severe and should be re-stated rather than quietly muting the app for a
 * week after one bad shoulder.
 *
 * The app gives no medical advice beyond the protocol's own line: stop, and see
 * a clinician.
 */
@Composable
private fun PainCard() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    val scope = rememberCoroutineScope()
    val report by settings.painReport.collectAsStateWithLifecycle(null)

    CutCard(background = if (report != null) SurfaceEdge else Surface) {
        SectionLabel("Today")

        if (report == null) {
            Text(
                "Feeling something you should not be?",
                style = MaterialTheme.typography.bodyLarge,
                color = TextHigh,
            )
            TextButton(
                onClick = { scope.launch { settings.reportPain(PainKind.SERIOUS) } },
            ) {
                Text(
                    "Report sharp or worsening pain",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ember,
                )
            }
            Text(
                "Chest pain, dizziness or fainting counts too. The app will stop " +
                    "suggesting training for the rest of today and tell you to see " +
                    "a clinician.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
        } else {
            Text(
                "Pain reported today. No training is being suggested.",
                style = MaterialTheme.typography.bodyLarge,
                color = Ember,
            )
            TextButton(onClick = { scope.launch { settings.reportPain(null) } }) {
                Text(
                    "Clear it",
                    style = MaterialTheme.typography.labelMedium,
                    color = Sky,
                )
            }
        }
    }
}

/**
 * Which moments of the day carry a notification.
 *
 * Every entry in the timeline is offered, not just the evening ones: the 15:30
 * "do not skip" is as easy to forget as lights out. The evening ones are on to
 * begin with because those are the ones that decide whether 05:00 is possible.
 *
 * The lights-out row shows tonight's ramp target rather than the timeline's
 * 21:30, because during the ramp they differ and the reminder fires at the
 * former.
 */
@Composable
private fun EveningReminders(repo: ProtocolRepository) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    val scope = rememberCoroutineScope()

    val on by settings.remindersOn.collectAsStateWithLifecycle(true)
    val chosen by settings.reminderChoices.collectAsStateWithLifecycle(emptyMap())
    val moments = remember(repo) { repo.reminderMoments(LocalDate.now()) }
    val canPost = remember(on) { ReminderNotifier.canPost(context) }

    /** What the timeline writes for lights out, which the ramp only reaches later. */
    val endState = remember(repo) {
        repo.protocol.dailyTimeline.firstOrNull { it.isLightsOut }?.time.orEmpty()
    }

    // Any change re-aims the next job, so a switch takes effect tonight rather
    // than after the next launch.
    fun reschedule() {
        scope.launch {
            if (settings.remindersOn.first()) {
                ReminderWorker.schedule(context, repo, settings.reminderChoices.first())
            } else {
                ReminderWorker.cancel(context)
            }
        }
    }

    CutCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { SectionLabel("Reminders") }
            Switch(
                checked = on,
                onCheckedChange = { wanted ->
                    scope.launch {
                        settings.setRemindersOn(wanted)
                        reschedule()
                    }
                },
                colors = switchColours(),
            )
        }

        if (!canPost) {
            Text(
                "Android is blocking notifications for CutCoach. Turn them on in " +
                    "the phone's app settings, or these will stay silent.",
                style = MaterialTheme.typography.bodySmall,
                color = Ember,
            )
        }

        if (on) {
            moments.forEach { moment ->
                val enabled = chosen[moment.id] ?: moment.onByDefault
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${moment.time} ${moment.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (enabled) TextHigh else TextMid,
                        )
                        if (moment.isLightsOut && moment.time.toString() != endState) {
                            Text(
                                "Tonight's ramp target. The timeline says $endState, " +
                                    "which the ramp reaches later.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMid,
                            )
                        }
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { wanted ->
                            scope.launch {
                                settings.setReminder(moment.id, wanted)
                                reschedule()
                            }
                        },
                        colors = switchColours(),
                    )
                }
            }
        }

        Text(
            repo.protocol.reminders.lieInGuardrail,
            style = MaterialTheme.typography.bodySmall,
            color = TextMid,
        )
    }
}

@Composable
private fun switchColours() = SwitchDefaults.colors(
    checkedThumbColor = Ink,
    checkedTrackColor = Ember,
    uncheckedThumbColor = TextMid,
    uncheckedTrackColor = SurfaceEdge,
    uncheckedBorderColor = SurfaceEdge,
)

@Composable
private fun VideoLinkRow(
    name: String,
    current: String,
    startSeconds: Float,
    endSeconds: Float,
    onSave: (String) -> Unit,
    onSaveSegment: (Float?, Float?) -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    var start by remember(startSeconds) { mutableStateOf(secondsText(startSeconds)) }
    var end by remember(endSeconds) { mutableStateOf(secondsText(endSeconds)) }
    // Keep the field in step if the stored value changes underneath it.
    LaunchedEffect(current) { draft = current }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = TextHigh)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text("https://", style = MaterialTheme.typography.bodySmall, color = TextMid)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = fieldColours(),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSave(draft) }) {
                Text("Save", style = MaterialTheme.typography.labelMedium, color = Ember)
            }
        }

        // The few seconds worth watching. A whole demo video is usually mostly
        // talking; the loop is for the rep itself.
        if (draft.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondsField("Loop from", start, { start = it }, Modifier.weight(1f))
                SecondsField("to", end, { end = it }, Modifier.weight(1f))
                TextButton(
                    onClick = {
                        onSaveSegment(start.toFloatOrNull(), end.toFloatOrNull())
                    },
                ) {
                    Text("Set", style = MaterialTheme.typography.labelMedium, color = Ember)
                }
            }
        }
    }
}

@Composable
private fun SecondsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.matches(Regex("^\\d{0,4}$"))) onValueChange(it) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.tabular(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = fieldColours(),
        modifier = modifier,
    )
}

private fun secondsText(value: Float): String =
    if (value <= 0f) "" else value.toInt().toString()

@Composable
private fun fieldColours() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextHigh,
    unfocusedTextColor = TextHigh,
    focusedBorderColor = Ember,
    unfocusedBorderColor = SurfaceEdge,
    focusedLabelColor = Ember,
    unfocusedLabelColor = TextMid,
    cursorColor = Ember,
)
