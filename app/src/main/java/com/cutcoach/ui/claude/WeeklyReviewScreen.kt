package com.cutcoach.ui.claude

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cutcoach.claude.CallLog
import com.cutcoach.claude.ClaudeClient
import com.cutcoach.claude.Outcome
import com.cutcoach.claude.Prompts
import com.cutcoach.claude.WeeklyReviewAnswer
import com.cutcoach.claude.weeklyReview
import com.cutcoach.data.protocol.ProtocolRepository
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
import kotlinx.coroutines.launch

/**
 * The week in plain language, with at most one change.
 *
 * Asked for rather than generated automatically: it costs tokens and it is a
 * thing to sit down with on a Sunday, not something to find waiting every time
 * the app opens.
 *
 * The call log lives at the bottom, because the brief asks for the prompt
 * version, model and token counts to be logged and there is no point logging
 * them somewhere the owner cannot see.
 */
@Composable
fun WeeklyReviewScreen(
    repo: ProtocolRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Outcome<WeeklyReviewAnswer>?>(null) }
    val totals = produceState<CallLog.Totals?>(null, outcome) {
        value = CallLog.totals(context)
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
        Text("Weekly review", style = MaterialTheme.typography.displaySmall, color = Mist)

        if (!ClaudeClient.isConfigured) {
            CutCard {
                Text(
                    "Not set up yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Mist,
                )
                Text(
                    "Deploy the Cloudflare Worker, then add cutcoach.proxyUrl and " +
                        "cutcoach.proxySecret to local.properties and rebuild. Your " +
                        "Anthropic key stays on Cloudflare and never enters the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MistDim,
                )
            }
            return@Column
        }

        CutCard {
            Text(
                "Fourteen days of daily aggregates go to Claude: hours slept, " +
                    "lights out against the ramp, sleeping heart rate, steps, the " +
                    "weekly weight averages, and your last set of each exercise. " +
                    "No raw records leave the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
            )
            Button(
                onClick = {
                    busy = true
                    outcome = null
                    scope.launch {
                        outcome = weeklyReview(context, repo)
                        busy = false
                    }
                },
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
                    Text("Review my week", style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        when (val result = outcome) {
            null -> Unit
            is Outcome.Ready -> ReviewBody(result.answer)
            is Outcome.Refused -> CutCard(background = SlateEdge) { RefusedCard(result.reasons) }
            is Outcome.Broke -> CutCard {
                Text(
                    result.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SunriseAmber,
                )
            }
        }

        totals.value?.takeIf { it.calls > 0 }?.let { CostCard(it) }
    }
}

@Composable
private fun ReviewBody(answer: WeeklyReviewAnswer) {
    Section(CutIcon.MOON, "Sleep", answer.sleep)
    Section(CutIcon.HEART, "Sleeping heart rate", answer.sleepingHeartRate)
    Section(CutIcon.FOOTSTEPS, "Steps", answer.steps)
    Section(CutIcon.SCALE, "Weight", answer.weight)
    Section(CutIcon.BARBELL, "Strength", answer.strength)

    CutCard(background = SlateEdge) {
        SectionLabel("One change")
        val change = answer.oneChange
        if (change == null || change.kind == "nothing") {
            Text(
                "Nothing to change this week.",
                style = MaterialTheme.typography.bodyLarge,
                color = Mist,
            )
            Text(
                "Which is an answer, not a failure to find one.",
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
            )
        } else {
            Text(
                describe(change.kind, change.stepsChange, change.caloriesKcalChange),
                style = MaterialTheme.typography.titleMedium,
                color = SunriseAmber,
            )
            Text(change.reason, style = MaterialTheme.typography.bodySmall, color = MistDim)
            Text(
                "Checked against the guardrails before being shown. The protocol " +
                    "allows one change, then three weeks of waiting.",
                style = MaterialTheme.typography.labelSmall,
                color = MistDim,
            )
        }
    }
}

private fun describe(kind: String, steps: Int, calories: Int): String = when {
    steps > 0 -> "Add $steps steps a day."
    calories != 0 -> "Change the daily target by $calories kcal."
    else -> kind.replaceFirstChar(Char::uppercase)
}

@Composable
private fun Section(icon: CutIcon, title: String, body: String) {
    CutCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CutIconView(icon, CoolSky, size = 20.dp)
            SectionLabel(title)
        }
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Mist)
    }
}

/**
 * What the Claude layer has cost.
 *
 * The brief asks for the prompt version, model and token counts to be logged.
 * Showing them is the other half: a feature that quietly starts costing ten
 * times what it did is something you want to notice.
 */
@Composable
private fun CostCard(totals: CallLog.Totals) {
    CutCard {
        SectionLabel("What this has cost")
        LabelledValue("Calls", totals.calls.toString())
        LabelledValue("Refused", totals.rejected.toString())
        LabelledValue("Tokens in", totals.inputTokens.toString())
        LabelledValue("Tokens out", totals.outputTokens.toString())
        LabelledValue("Prompt version", Prompts.VERSION)
        Box(Modifier.height(4.dp))
        Text(
            "No prompt or answer text is kept, only these counts. The text carries " +
                "your health summaries, and a log of it on the phone would be a " +
                "second copy of the thing this app is careful about.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
    }
}
