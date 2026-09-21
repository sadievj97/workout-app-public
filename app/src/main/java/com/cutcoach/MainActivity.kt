package com.cutcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Step 1 of Phase 1: the smallest app that proves the toolchain works.
 *
 * It renders one screen, reads no files and stores nothing. Everything real
 * arrives in later steps: the theme and font in step 2, the protocol.json
 * loader in step 3, the Today screen in step 4.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CutCoachPlaceholderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ItRuns()
                }
            }
        }
    }
}

// The palette from the design direction, applied here in the crudest possible
// way so step 1 has nothing that can fail. Step 2 replaces this with a proper
// theme file and the Atkinson Hyperlegible typeface.
private val PreDawnNavy = Color(0xFF101B2D)
private val Slate = Color(0xFF1D2B42)
private val Mist = Color(0xFFD5DDE8)
private val SunriseAmber = Color(0xFFF2A541)

@Composable
private fun CutCoachPlaceholderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = PreDawnNavy,
            surface = Slate,
            primary = SunriseAmber,
            onBackground = Mist,
            onSurface = Mist,
        ),
        content = content,
    )
}

@Composable
private fun ItRuns(modifier: Modifier = Modifier) {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "CutCoach",
            style = MaterialTheme.typography.displaySmall,
            color = SunriseAmber,
        )
        Text(
            text = "The app builds, installs, and runs.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = today,
            style = MaterialTheme.typography.bodyMedium,
            color = Mist.copy(alpha = 0.7f),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101B2D)
@Composable
private fun ItRunsPreview() {
    CutCoachPlaceholderTheme { ItRuns() }
}
