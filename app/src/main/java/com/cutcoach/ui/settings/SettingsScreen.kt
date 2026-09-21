package com.cutcoach.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.settings.SettingsRepository
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.LabelledValue
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SlateEdge
import com.cutcoach.ui.theme.SunriseAmber
import com.cutcoach.ui.theme.tabular
import kotlinx.coroutines.launch

/**
 * The few things worth changing from the phone.
 *
 * Everything else lives in protocol.json, which stays the source of truth.
 * What is here are overrides that sit on top of it, each with a way back to the
 * protocol's own value.
 */
@Composable
fun SettingsScreen(repo: ProtocolRepository, modifier: Modifier = Modifier) {
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
        Text("Settings", style = MaterialTheme.typography.displaySmall, color = Mist)

        // Read-only, and the screen says why.
        CutCard {
            SectionLabel("The program")
            LabelledValue("Starts", repo.protocol.meta.programStartDate ?: "not set")
            LabelledValue("Bedtime ramp night 1", repo.protocol.sleep.rampStartDate ?: "not set")
            LabelledValue("Length", "${repo.protocol.meta.programLengthWeeks} weeks")
            LabelledValue("Wake", repo.protocol.sleep.wakeTime)
            Text(
                "These are set in protocol.json, not here. Moving a start date after " +
                    "the fact would renumber every session you have already logged, so " +
                    "it should be a deliberate edit to the file.",
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
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
                        containerColor = SunriseAmber,
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
                        color = CoolSky,
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
                    containerColor = SunriseAmber,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "Typed in from your rehab program. The app only ever displays these: " +
                    "no recommendation in any phase will change or substitute rehab work.",
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
            )
        }

        CutCard {
            SectionLabel("Demo video links")
            Text(
                "Paste a link you trust for each exercise. The cue card shows a " +
                    "button only once a link is set.",
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
            )
            Box(Modifier.height(4.dp))
            repo.library.exercises.forEach { exercise ->
                VideoLinkRow(
                    name = exercise.name,
                    current = videoUrls[exercise.id] ?: exercise.videoUrl,
                    onSave = { scope.launch { settings.setVideoUrl(exercise.id, it) } },
                )
            }
        }
    }
}

@Composable
private fun VideoLinkRow(name: String, current: String, onSave: (String) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    // Keep the field in step if the stored value changes underneath it.
    LaunchedEffect(current) { draft = current }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = Mist)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text("https://", style = MaterialTheme.typography.bodySmall, color = MistDim)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = fieldColours(),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSave(draft) }) {
                Text("Save", style = MaterialTheme.typography.labelMedium, color = SunriseAmber)
            }
        }
    }
}

@Composable
private fun fieldColours() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Mist,
    unfocusedTextColor = Mist,
    focusedBorderColor = SunriseAmber,
    unfocusedBorderColor = SlateEdge,
    focusedLabelColor = SunriseAmber,
    unfocusedLabelColor = MistDim,
    cursorColor = SunriseAmber,
)
