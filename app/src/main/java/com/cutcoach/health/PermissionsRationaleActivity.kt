package com.cutcoach.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cutcoach.ui.components.CutCard
import com.cutcoach.ui.components.SectionLabel
import com.cutcoach.ui.theme.CutCoachTheme
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SunriseAmber

/**
 * What Health Connect shows before it will hand over any data.
 *
 * Health Connect refuses to display the permission sheet at all unless the app
 * can explain itself here, so this is not optional paperwork: without it the
 * whole flow simply does not start.
 *
 * It is also the honest place to say what this app does and does not do with
 * health data, which for a personal app that never leaves the phone is a short
 * story.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CutCoachTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(Modifier.safeDrawingPadding()) {
                        PrivacyPolicy()
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyPolicy(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "How CutCoach uses health data",
            style = MaterialTheme.typography.headlineMedium,
            color = Mist,
        )

        CutCard {
            SectionLabel("What it reads")
            Text(
                "Steps, sleep sessions, heart rate, resting heart rate, heart rate " +
                    "variability, workout sessions, and weight.",
                style = MaterialTheme.typography.bodyLarge,
                color = Mist,
            )
            Text(
                "Reading only. CutCoach never writes anything back into Health " +
                    "Connect and never changes or deletes what is already there.",
                style = MaterialTheme.typography.bodyMedium,
                color = SunriseAmber,
            )
        }

        CutCard {
            SectionLabel("What it does with it")
            Text(
                "It compares last night's sleep with the bedtime target for that " +
                    "night, tracks sleeping heart rate against the goal, checks steps " +
                    "against the daily target, averages weight over seven days, and on " +
                    "cardio days reports how long the session spent in the right heart " +
                    "rate zone.",
                style = MaterialTheme.typography.bodyLarge,
                color = Mist,
            )
        }

        CutCard {
            SectionLabel("Where it goes")
            Text(
                "Nowhere. The data stays on this phone. There is no account, no " +
                    "server, no analytics and no backup off the device. The app is not " +
                    "published; it is installed from Android Studio onto one phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = Mist,
            )
            Text(
                "Later, a weekly review may send a small summary — daily totals and " +
                    "averages — to an AI service to write it in plain language. Raw " +
                    "health records are never sent.",
                style = MaterialTheme.typography.bodyMedium,
                color = MistDim,
            )
        }

        CutCard {
            SectionLabel("Two things it will not do")
            Text(
                "It never presents the watch's calorie estimate as food to eat back. " +
                    "Those estimates run badly wrong, and eating to them undoes the " +
                    "deficit.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
            )
            Text(
                "It never guesses at missing data. If the watch did not record a " +
                    "night, the app asks rather than inventing a number.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
            )
        }

        CutCard {
            SectionLabel("Taking it back")
            Text(
                "Every permission can be withdrawn at any time in Health Connect, " +
                    "under Settings. The app keeps working without them; it simply " +
                    "stops showing watch data.",
                style = MaterialTheme.typography.bodyLarge,
                color = Mist,
            )
        }
    }
}
