package com.cutcoach.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cutcoach.data.db.TrainingRepository
import com.cutcoach.data.protocol.Exercise
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.Sky
import com.cutcoach.ui.theme.TextHigh
import com.cutcoach.ui.theme.TextMid
import com.cutcoach.ui.theme.Ember
import kotlinx.coroutines.launch

/**
 * Where you start on each exercise's ladder.
 *
 * Every exercise begins on its first rung, which for someone who can already do
 * fifteen full push-ups means weeks of sessions that are too easy before the
 * progression rules catch up. This is the one screen where all fifteen can be
 * set in a single sitting.
 *
 * It is not a difficulty level for the program. The ladders are per exercise on
 * purpose: being four rungs up on push-ups and on the first rung for pull-ups
 * is the normal case, and a single "intermediate" could not say it.
 */
@Composable
fun LadderScreen(
    repo: ProtocolRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val training = remember { TrainingRepository.get(context) }
    val scope = rememberCoroutineScope()

    val steps by training.ladderSteps.collectAsStateWithLifecycle(emptyMap())

    // Only the exercises the schedule actually uses, in the library's order.
    val scheduled = remember(repo) {
        repo.protocol.sessions.values
            .flatMap { it.exercises }
            .map { it.exerciseId }
            .toSet()
    }
    val exercises = remember(repo, scheduled) {
        repo.library.exercises.filter { it.id in scheduled }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("Back", style = MaterialTheme.typography.labelMedium, color = Sky)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Starting rungs",
                    style = MaterialTheme.typography.displaySmall,
                    color = TextHigh,
                )
                Text(
                    "Tap the hardest version of each movement you can already do with " +
                        "good form for the whole set. Everything starts on the first " +
                        "rung until you say otherwise.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                )
                Text(
                    "This sets difficulty and tempo only. It never changes how many " +
                        "sets you do, and rehab work has no ladder at all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                )
            }
        }

        items(exercises, key = { it.id }) { exercise ->
            LadderCard(
                exercise = exercise,
                step = steps[exercise.id] ?: 0,
                onSelect = { index ->
                    scope.launch {
                        training.setLadderStep(exercise.id, index, exercise.ladder.size)
                    }
                },
            )
        }
    }
}

@Composable
private fun LadderCard(exercise: Exercise, step: Int, onSelect: (Int) -> Unit) {
    CutCard {
        SectionLabel(exercise.name)

        exercise.ladder.forEachIndexed { index, rung ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(index) }
                    .heightIn(min = 56.dp)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (index == step) Ember else TextMid,
                )
                Text(
                    rung,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        index == step -> Ember
                        index < step -> TextMid
                        else -> TextHigh
                    },
                    modifier = Modifier.weight(1f),
                )
                if (index == step) {
                    Text(
                        "Start here",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ember,
                    )
                }
            }
        }

        if (step > 0) {
            Box(Modifier.height(2.dp))
            TextButton(onClick = { onSelect(0) }) {
                Text(
                    "Back to the first rung",
                    style = MaterialTheme.typography.labelMedium,
                    color = Sky,
                )
            }
        }

        exercise.ladderEndNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextMid)
        }
    }
}

