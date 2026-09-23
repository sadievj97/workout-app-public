package com.cutcoach.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cutcoach.data.poses.PoseLoad
import com.cutcoach.data.poses.PoseRepository
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.protocol.SessionChoice
import com.cutcoach.ui.components.Pill
import com.cutcoach.ui.figure.AnimatedStickFigure
import com.cutcoach.ui.figure.FigureRender
import com.cutcoach.ui.theme.InkLift
import com.cutcoach.ui.theme.Surface
import com.cutcoach.ui.theme.SurfaceEdge
import com.cutcoach.ui.theme.TextHigh
import com.cutcoach.ui.theme.TextLow
import com.cutcoach.ui.theme.TextMid
import com.cutcoach.ui.theme.identityFor
import com.cutcoach.ui.theme.tabular
import java.time.DayOfWeek
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Pick a session to do now, whatever day it is.
 *
 * The protocol schedules a session per weekday, and the app used to treat that
 * as the only thing you were allowed to do. It is a plan, not a gate: a
 * Wednesday where you want Monday's session is a Wednesday where you do
 * Monday's session, and forcing a skip instead is how a plan gets abandoned.
 *
 * Today's scheduled session stays first and stays marked, because on most days
 * it is the right answer and it should take one tap, not two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionPickerSheet(
    repo: ProtocolRepository,
    todaysSessionId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val choices = remember(repo) { repo.allSessions() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = InkLift,
        contentColor = TextHigh,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Start a session",
                style = MaterialTheme.typography.headlineSmall,
                color = TextHigh,
            )
            Text(
                "Any session, any day. What you do is logged against today.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
            )

            Box(Modifier.height(4.dp))

            choices.forEach { choice ->
                SessionRow(
                    repo = repo,
                    choice = choice,
                    isToday = choice.id == todaysSessionId,
                    onPick = { onPick(choice.id) },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(
    repo: ProtocolRepository,
    choice: SessionChoice,
    isToday: Boolean,
    onPick: () -> Unit,
) {
    val identity = identityFor(choice.id, choice.session.name, choice.session.focus)
    val context = LocalContext.current
    val poses = remember { PoseRepository.load(context) as? PoseLoad.Success }

    // The session's first exercise, as a thumbnail. It says more about what a
    // session is than its name does.
    val firstExercise = choice.session.exercises.firstOrNull()?.exerciseId
    val animation = firstExercise?.let { poses?.library?.animationFor(it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isToday) Surface else Surface.copy(alpha = 0.04f))
            .clickable(onClick = onPick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A coloured slab carrying the session's own identity, so the list is
        // scannable by colour before it is read.
        Box(
            Modifier
                .size(width = 5.dp, height = 46.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(identity.sweep()),
        )

        if (animation != null && poses != null) {
            AnimatedStickFigure(
                animation = animation,
                style = poses.library.figure,
                library = poses.library,
                render = FigureRender.LINES,
                highlightWorking = false,
                paused = true,
                modifier = Modifier
                    .size(44.dp)
                    .alpha(0.85f),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                choice.session.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextHigh,
            )
            Text(
                summarise(repo, choice),
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = TextMid,
            )
            if (choice.usualDays.isNotEmpty()) {
                Text(
                    "Usually " + choice.usualDays.joinToString(", ") { it.short() },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                )
            }
        }

        if (isToday) {
            Pill("TODAY", colour = identity.accent, fill = SurfaceEdge)
        }
    }
}

/** A one-line description: what it is and how long it takes. */
private fun summarise(repo: ProtocolRepository, choice: SessionChoice): String = buildString {
    choice.session.focus?.let { append(it) }

    val count = choice.session.exercises.size
    if (count > 0) {
        if (isNotEmpty()) append(" · ")
        append("$count exercises")
    }

    choice.schedule?.durationMin?.let {
        if (isNotEmpty()) append(" · ")
        append(it.describe("min"))
    }

    if (isEmpty()) append(repo.session(choice.id)?.note ?: "Session")
}

/** "Mon", in the phone's own language. */
private fun DayOfWeek.short(): String =
    getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
