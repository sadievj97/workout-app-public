package com.cutcoach.samsung

import android.app.Activity
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.CutIcon
import com.cutcoach.ui.components.CutIconView
import com.cutcoach.ui.components.LabelledValue
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SlateEdge
import com.cutcoach.ui.theme.SunriseAmber
import com.cutcoach.ui.theme.tabular
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * The Energy Score probe, on screen.
 *
 * Nothing here feeds the rules engine. It answers one question — does this
 * phone hand over an Energy Score, and what does it look like — so that the
 * decision about whether Phase 5 is worth building rests on what the phone
 * actually does rather than on what Samsung's overview page lists.
 */
@Composable
fun EnergyScoreProbeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<EnergyScoreProbe.Report?>(null) }

    fun probe() {
        busy = true
        scope.launch {
            report = EnergyScoreProbe.run(context)
            busy = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("Back", style = MaterialTheme.typography.labelMedium, color = CoolSky)
        }
        Text("Energy Score probe", style = MaterialTheme.typography.displaySmall, color = Mist)

        CutCard {
            Text(
                "Samsung's Energy Score is not in Health Connect, so it needs their " +
                    "own SDK and developer mode. This checks whether your phone " +
                    "actually provides one before anything is built on it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MistDim,
            )
            Text(
                "Read only. Nothing here is stored or sent anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
            )
        }

        Button(
            onClick = { probe() },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SunriseAmber,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = SlateEdge,
                disabledContentColor = MistDim,
            ),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(22.dp),
                    color = MistDim,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Check my phone", style = MaterialTheme.typography.titleLarge)
            }
        }

        report?.let { result ->
            CutCard(background = if (result.available) null.let { SlateEdge } else SlateEdge) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CutIconView(
                        if (result.available) CutIcon.SUNRISE else CutIcon.ALERT,
                        if (result.available) CoolSky else SunriseAmber,
                        size = 22.dp,
                    )
                    Text(
                        result.headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (result.available) Mist else SunriseAmber,
                    )
                }
                Text(result.detail, style = MaterialTheme.typography.bodyMedium, color = MistDim)

                if (!result.available) {
                    Box(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            val activity = context as? Activity ?: return@TextButton
                            busy = true
                            scope.launch {
                                EnergyScoreProbe.requestPermission(activity)
                                report = EnergyScoreProbe.run(context)
                                busy = false
                            }
                        },
                    ) {
                        Text(
                            "Ask Samsung Health",
                            style = MaterialTheme.typography.labelLarge,
                            color = CoolSky,
                        )
                    }
                }
            }

            if (result.readings.isNotEmpty()) {
                CutCard {
                    SectionLabel("What came back")
                    result.readings.forEach { reading ->
                        LabelledValue(
                            reading.date.format(DateTimeFormatter.ofPattern("EEE d MMM")),
                            reading.score?.let { "%.1f".format(it) } ?: "no value",
                        )
                    }
                    Text(
                        "Raw, unrounded, and not used by anything. If these look " +
                            "right, Phase 5 has something to build on.",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MistDim,
                    )
                }
            }
        }
    }
}
