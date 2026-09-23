package com.cutcoach.ui.claude

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.cutcoach.claude.ClaudeClient
import com.cutcoach.claude.HarderAnswer
import com.cutcoach.claude.Outcome
import com.cutcoach.claude.Rejection
import com.cutcoach.claude.SubstituteAnswer
import com.cutcoach.claude.harderVariation
import com.cutcoach.claude.substitute
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.CutIcon
import com.cutcoach.ui.components.CutIconView
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.Sky
import com.cutcoach.ui.theme.TextHigh
import com.cutcoach.ui.theme.TextMid
import com.cutcoach.ui.theme.SurfaceEdge
import com.cutcoach.ui.theme.Ember
import kotlinx.coroutines.launch

/**
 * Asking Claude for a swap, from the cue card.
 *
 * The answer is never shown raw. It has already been through the guardrails by
 * the time it arrives here, and a refusal is shown as a refusal with its
 * reasons: a model that keeps proposing banned things is something the owner
 * should be able to see rather than something the app quietly hides.
 */
@Composable
fun SubstituteCard(
    repo: ProtocolRepository,
    exerciseId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Outcome<SubstituteAnswer>?>(null) }

    CutCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CutIconView(CutIcon.QUESTION, Sky, size = 20.dp)
            SectionLabel("Cannot do this today?")
        }

        if (!ClaudeClient.isConfigured) {
            Text(
                "The Claude layer is not set up yet. Deploy the Worker and add its " +
                    "address to local.properties.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
            return@CutCard
        }

        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Why not?", style = MaterialTheme.typography.bodySmall) },
            placeholder = {
                Text(
                    "no pull-up bar, hotel room, no floor space",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextHigh,
                unfocusedTextColor = TextHigh,
                focusedBorderColor = Sky,
                unfocusedBorderColor = SurfaceEdge,
                focusedLabelColor = Sky,
                unfocusedLabelColor = TextMid,
                cursorColor = Sky,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                busy = true
                outcome = null
                scope.launch {
                    outcome = substitute(context, repo, exerciseId, reason)
                    busy = false
                }
            },
            enabled = reason.isNotBlank() && !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Sky,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                disabledContainerColor = SurfaceEdge,
                disabledContentColor = TextMid,
            ),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = TextMid,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Ask for a swap", style = MaterialTheme.typography.titleMedium)
            }
        }

        when (val result = outcome) {
            null -> Unit
            is Outcome.Ready -> SubstituteResult(repo, result.answer)
            is Outcome.Refused -> RefusedCard(result.reasons)
            is Outcome.Broke -> Text(
                result.message,
                style = MaterialTheme.typography.bodySmall,
                color = Ember,
            )
        }
    }
}

@Composable
private fun SubstituteResult(repo: ProtocolRepository, answer: SubstituteAnswer) {
    Box(Modifier.height(6.dp))
    val name = answer.withExerciseId?.let { repo.exercise(it)?.name }
        ?: answer.withDescription
        ?: "No replacement"

    Text(name, style = MaterialTheme.typography.titleMedium, color = Ember)
    Text(
        buildString {
            append("${answer.sets} sets")
            answer.reps?.let { append(" of $it reps") }
            answer.seconds?.let { append(" of ${it}s") }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = TextHigh,
    )
    Text(answer.reason, style = MaterialTheme.typography.bodySmall, color = TextMid)
    Text(
        "Checked against the protocol's guardrails before being shown.",
        style = MaterialTheme.typography.labelSmall,
        color = TextMid,
    )
}

/**
 * A refusal, with every rule that was broken.
 *
 * Shown rather than hidden. The point of the guardrails is that they hold; the
 * point of showing them is that the owner can see when they had to.
 */
@Composable
fun RefusedCard(reasons: List<Rejection>) {
    Box(Modifier.height(6.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CutIconView(CutIcon.ALERT, Ember, size = 18.dp)
        Text(
            if (reasons.size == 1) "Suggestion refused" else "Suggestion refused, ${reasons.size} reasons",
            style = MaterialTheme.typography.titleSmall,
            color = Ember,
        )
    }
    reasons.forEach { rejection ->
        Text(rejection.because, style = MaterialTheme.typography.bodySmall, color = TextHigh)
        Text(rejection.rule, style = MaterialTheme.typography.labelSmall, color = TextMid)
    }
}

/**
 * A harder variation, for a ladder that has topped out.
 *
 * Only offered on the last rung: asking for something harder than a rung you
 * have not reached is how volume creeps in the back door.
 */
@Composable
fun HarderCard(
    repo: ProtocolRepository,
    exerciseId: String,
    currentRung: Int,
    modifier: Modifier = Modifier,
) {
    val exercise = repo.exercise(exerciseId) ?: return
    val onLastRung = currentRung >= exercise.ladder.lastIndex
    if (!onLastRung || !ClaudeClient.isConfigured) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Outcome<HarderAnswer>?>(null) }

    CutCard(background = SurfaceEdge, modifier = modifier) {
        SectionLabel("The ladder has run out")
        Text(
            "You are on the last rung. Claude can suggest a harder version of " +
                "the same movement.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMid,
        )
        exercise.ladderEndNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextMid)
        }

        TextButton(
            onClick = {
                busy = true
                outcome = null
                scope.launch {
                    outcome = harderVariation(context, repo, exerciseId)
                    busy = false
                }
            },
            enabled = !busy,
        ) {
            Text(
                if (busy) "Asking..." else "Suggest something harder",
                style = MaterialTheme.typography.labelLarge,
                color = Sky,
            )
        }

        when (val result = outcome) {
            null -> Unit
            is Outcome.Ready -> {
                Text(
                    result.answer.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ember,
                )
                Text(
                    result.answer.howTo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHigh,
                )
                Text(
                    result.answer.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                )
            }
            is Outcome.Refused -> RefusedCard(result.reasons)
            is Outcome.Broke -> Text(
                result.message,
                style = MaterialTheme.typography.bodySmall,
                color = Ember,
            )
        }
    }
}
