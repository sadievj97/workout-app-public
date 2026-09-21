package com.cutcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.ui.theme.CutCoachTheme
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SunriseAmber
import com.cutcoach.ui.today.TodayScreen
import java.time.LocalDate
import java.time.LocalTime

/**
 * The root of the app. It reads the protocol once, then hands it to the
 * screens.
 *
 * `safeDrawingPadding` keeps content clear of the status bar and the gesture
 * area. Without it the first line of every screen hides behind the clock.
 */
@Composable
fun CutCoachApp() {
    val context = LocalContext.current
    val load = remember { ProtocolRepository.load(context) }

    CutCoachTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.safeDrawingPadding()) {
                when (load) {
                    is ProtocolLoad.Success -> TodayScreen(
                        repo = load.repository,
                        today = LocalDate.now(),
                        now = LocalTime.now(),
                        onStartSession = { /* wired up in the next step */ },
                    )

                    is ProtocolLoad.Failure -> ProtocolError(load)
                }
            }
        }
    }
}

/**
 * Shown when protocol.json or exercises.json cannot be read. The app is useless
 * without them, so this says which file and why rather than crashing.
 */
@Composable
private fun ProtocolError(failure: ProtocolLoad.Failure) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "The protocol did not load",
            style = MaterialTheme.typography.headlineMedium,
            color = SunriseAmber,
        )
        Text(failure.message, style = MaterialTheme.typography.bodyMedium, color = Mist)
        Text(
            "Fix the file in app/src/main/assets and run the app again.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
    }
}
