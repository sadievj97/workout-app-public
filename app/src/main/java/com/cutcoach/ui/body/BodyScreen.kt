package com.cutcoach.ui.body

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cutcoach.data.db.TrainingRepository
import com.cutcoach.data.db.sevenDayAverageKg
import com.cutcoach.data.db.watchDaysInWindow
import com.cutcoach.data.protocol.ProtocolRepository
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Weight every day, waist on Sundays, and the 7-day average the protocol
 * actually judges progress by.
 *
 * In program week 1 no verdict is shown, because the protocol says week 1 is
 * water and cortisol. The numbers are still displayed; only the judgement is
 * withheld.
 */
@Composable
fun BodyScreen(
    repo: ProtocolRepository,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val training = remember { TrainingRepository.get(context) }
    val scope = rememberCoroutineScope()

    val recent by training.recentBody(today, days = 30).collectAsStateWithLifecycle(emptyList())
    val average = recent.sevenDayAverageKg(today)
    val watchDays = recent.watchDaysInWindow(today)
    val todayRow = recent.firstOrNull { it.date == today.toString() }

    var weightInput by remember { mutableStateOf("") }
    var waistInput by remember { mutableStateOf("") }

    val isSunday = today.dayOfWeek == DayOfWeek.SUNDAY
    val programWeek = repo.programWeek(today)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Body", style = MaterialTheme.typography.displaySmall, color = Mist)

        CutCard {
            SectionLabel("7-day average")
            if (average == null) {
                Text(
                    "Nothing weighed yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MistDim,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.1f".format(average),
                        style = MaterialTheme.typography.displayMedium.tabular(),
                        color = SunriseAmber,
                    )
                    Text(
                        " kg",
                        style = MaterialTheme.typography.titleLarge,
                        color = MistDim,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                val goal = repo.protocol.meta.goal
                LabelledValue("Started at", "%.1f kg".format(goal.startWeightKg))
                LabelledValue("Target", goal.targetWeightKg.describe("kg"))

                // Say where the number came from. A watch reading and a weight
                // you stood on the scale for are not the same evidence.
                if (watchDays > 0) {
                    Text(
                        if (watchDays == 1) {
                            "One of these days came from the watch."
                        } else {
                            "$watchDays of these days came from the watch."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MistDim,
                    )
                }
            }

            // The week-1 guardrail, stated rather than silently applied.
            if (programWeek == 1) {
                Box(Modifier.height(4.dp))
                Text(
                    "No verdict in week 1. Early change is water and cortisol, not fat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CoolSky,
                )
            }
        }

        CutCard {
            SectionLabel("Today, ${today.format(DateTimeFormatter.ofPattern("EEEE d MMM"))}")

            todayRow?.weightKg?.let {
                LabelledValue(
                    if (todayRow.fromWatch) "From the watch" else "Logged",
                    "%.1f kg".format(it),
                )
                ClearButton("Clear this day") { scope.launch { training.clearWeight(today) } }
            }

            NumberEntry(
                value = weightInput,
                onValueChange = { weightInput = it },
                label = "Weight in kg",
                onSave = {
                    weightInput.toDoubleOrNull()?.let { kg ->
                        scope.launch { training.recordWeight(today, kg) }
                        weightInput = ""
                    }
                },
            )

            Text(
                repo.protocol.tracking.weight.display,
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
            )
        }

        // Waist is weekly, on the day the protocol names.
        if (isSunday) {
            CutCard(background = SlateEdge) {
                SectionLabel("Waist, Sundays only")
                todayRow?.waistCm?.let {
                    LabelledValue("Logged", "%.1f cm".format(it))
                    ClearButton("Clear this day") { scope.launch { training.clearWaist(today) } }
                }
                NumberEntry(
                    value = waistInput,
                    onValueChange = { waistInput = it },
                    label = "Waist in cm",
                    onSave = {
                        waistInput.toDoubleOrNull()?.let { cm ->
                            scope.launch { training.recordWaist(today, cm) }
                            waistInput = ""
                        }
                    },
                )
                Text(
                    repo.protocol.tracking.waist.how,
                    style = MaterialTheme.typography.bodySmall,
                    color = MistDim,
                )
            }
        }

        CutCard {
            SectionLabel("Recent")
            if (recent.isEmpty()) {
                Text("Nothing yet.", style = MaterialTheme.typography.bodyMedium, color = MistDim)
            }
            recent.take(14).forEach { row ->
                LabelledValue(
                    label = LocalDate.parse(row.date)
                        .format(DateTimeFormatter.ofPattern("EEE d MMM")),
                    value = buildString {
                        row.weightKg?.let {
                            append("%.1f kg".format(it))
                            if (row.fromWatch) append(" ·watch")
                        }
                        row.waistCm?.let {
                            if (isNotEmpty()) append("   ")
                            append("%.1f cm".format(it))
                        }
                    },
                )
            }
        }
    }
}

/**
 * Removing a measurement, behind one confirming tap.
 *
 * Deliberately two taps. This screen is used half-awake with the phone on the
 * floor, and a stray touch that silently deleted a logged weight would be the
 * same class of mistake it exists to undo.
 */
@Composable
private fun ClearButton(label: String, onClear: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }

    TextButton(
        onClick = {
            if (confirming) {
                onClear()
                confirming = false
            } else {
                confirming = true
            }
        },
    ) {
        Text(
            if (confirming) "Tap again to clear" else label,
            style = MaterialTheme.typography.labelMedium,
            color = if (confirming) SunriseAmber else MistDim,
        )
    }
}

@Composable
private fun NumberEntry(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { new ->
                // Only digits and one decimal point, so the keyboard cannot
                // produce something that fails to parse.
                if (new.isEmpty() || new.matches(Regex("^\\d{0,3}([.,]\\d{0,2})?$"))) {
                    onValueChange(new.replace(',', '.'))
                }
            },
            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.tabular(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Mist,
                unfocusedTextColor = Mist,
                focusedBorderColor = SunriseAmber,
                unfocusedBorderColor = SlateEdge,
                focusedLabelColor = SunriseAmber,
                unfocusedLabelColor = MistDim,
                cursorColor = SunriseAmber,
            ),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onSave,
            enabled = value.toDoubleOrNull() != null,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SunriseAmber,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = SlateEdge,
                disabledContentColor = MistDim,
            ),
        ) {
            Text("Save", style = MaterialTheme.typography.labelLarge)
        }
    }
}
