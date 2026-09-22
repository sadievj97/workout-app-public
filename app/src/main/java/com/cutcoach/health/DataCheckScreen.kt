package com.cutcoach.health

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.LabelledValue
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SunriseAmber
import com.cutcoach.ui.theme.tabular
import kotlinx.coroutines.launch

/**
 * What this phone actually provides.
 *
 * Built before anything depends on watch data, and deliberately boring: for
 * each record type, how many records exist over the last thirty days and what
 * the most recent one says. A data type can look perfectly well supported in
 * the documentation and be completely empty on a given phone, and it is much
 * cheaper to find that out here than halfway through building a feature on top
 * of it.
 */
@Composable
fun DataCheckScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { HealthConnectManager(context) }
    val scope = rememberCoroutineScope()

    var surveys by remember { mutableStateOf<List<RecordSurvey>>(emptyList()) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var checking by remember { mutableStateOf(false) }
    var ran by remember { mutableStateOf(false) }

    suspend fun refresh() {
        checking = true
        granted = manager.grantedPermissions()
        surveys = manager.survey(days = 30)
        checking = false
        ran = true
    }

    val requestPermissions = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { scope.launch { refresh() } }

    LaunchedEffect(Unit) {
        if (manager.availability == HealthConnectManager.Availability.AVAILABLE) refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("Back", style = MaterialTheme.typography.labelMedium, color = CoolSky)
        }
        Text("Data check", style = MaterialTheme.typography.displaySmall, color = Mist)

        when (manager.availability) {
            HealthConnectManager.Availability.UNAVAILABLE -> Unavailable(context.packageManager)
            HealthConnectManager.Availability.UPDATE_REQUIRED -> UpdateRequired()
            HealthConnectManager.Availability.AVAILABLE -> {
                PermissionSummary(
                    manager = manager,
                    granted = granted,
                    onRequest = { requestPermissions.launch(manager.permissions) },
                )

                CutCard {
                    SectionLabel("Last 30 days")
                    if (checking) {
                        Text(
                            "Reading...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MistDim,
                        )
                    } else if (!ran) {
                        Text(
                            "Not checked yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MistDim,
                        )
                    }
                    surveys.forEach { RecordRow(it) }
                }

                Button(
                    onClick = { scope.launch { refresh() } },
                    enabled = !checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SunriseAmber,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Check again", style = MaterialTheme.typography.labelLarge)
                }

                Text(
                    "Nothing else in the app depends on this data yet. This screen " +
                        "exists to show what the phone really provides first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MistDim,
                )
            }
        }
    }
}

@Composable
private fun PermissionSummary(
    manager: HealthConnectManager,
    granted: Set<String>,
    onRequest: () -> Unit,
) {
    val missing = manager.permissions - granted

    CutCard {
        SectionLabel("Permissions")
        LabelledValue(
            "Granted",
            "${granted.count { it in manager.permissions }} of ${manager.permissions.size}",
        )

        // These two are worth calling out on their own, because without them
        // things fail quietly rather than loudly: short history, and a sync
        // that only runs while the app is open.
        LabelledValue(
            "History beyond 30 days",
            if (HealthConnectManager.PERMISSION_READ_HISTORY in granted) "yes" else "no",
            valueColor = if (HealthConnectManager.PERMISSION_READ_HISTORY in granted) {
                Mist
            } else {
                SunriseAmber
            },
        )
        LabelledValue(
            "Reading in the background",
            if (HealthConnectManager.PERMISSION_READ_IN_BACKGROUND in granted) "yes" else "no",
            valueColor = if (HealthConnectManager.PERMISSION_READ_IN_BACKGROUND in granted) {
                Mist
            } else {
                SunriseAmber
            },
        )

        if (missing.isNotEmpty()) {
            Button(
                onClick = onRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SunriseAmber,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Ask for the rest", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "Health Connect may ask more than once: it keeps history and " +
                    "background reads on a separate screen from the data types.",
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
            )
        }
    }
}

@Composable
private fun RecordRow(survey: RecordSurvey) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                survey.type.label,
                style = MaterialTheme.typography.bodyLarge,
                color = Mist,
            )
            Text(
                when (survey.state) {
                    RecordState.PRESENT -> "${survey.count}"
                    RecordState.EMPTY -> "none"
                    RecordState.NOT_GRANTED -> "not allowed"
                    RecordState.FAILED -> "failed"
                },
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = when (survey.state) {
                    RecordState.PRESENT -> CoolSky
                    else -> SunriseAmber
                },
            )
        }
        Text(
            survey.failure ?: survey.latest?.let { "Latest: $it" } ?: survey.type.why,
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
    }
}

@Composable
private fun Unavailable(packageManager: android.content.pm.PackageManager) {
    val context = LocalContext.current
    CutCard {
        SectionLabel("Health Connect")
        Text(
            "Health Connect is not available on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = SunriseAmber,
        )
        Text(
            "On Android 14 and later it is built into the system settings. Before " +
                "that it is a separate app from the Play Store. Samsung Health syncs " +
                "into it; this app only reads what is already there.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
        TextButton(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "market://details?id=com.google.android.apps.healthdata".toUri(),
                )
                if (intent.resolveActivity(packageManager) != null) {
                    context.startActivity(intent)
                }
            },
        ) {
            Text(
                "Open it in the Play Store",
                style = MaterialTheme.typography.labelMedium,
                color = CoolSky,
            )
        }
    }
}

@Composable
private fun UpdateRequired() {
    CutCard {
        SectionLabel("Health Connect")
        Text(
            "Health Connect needs updating before this app can read anything.",
            style = MaterialTheme.typography.bodyLarge,
            color = SunriseAmber,
        )
    }
}
