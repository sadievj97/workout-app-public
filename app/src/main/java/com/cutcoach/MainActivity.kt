package com.cutcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cutcoach.ui.theme.CoolSky
import com.cutcoach.ui.theme.CutCoachTheme
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.PreDawnNavy
import com.cutcoach.ui.theme.Slate
import com.cutcoach.ui.theme.SunriseAmber
import com.cutcoach.ui.theme.tabular

/**
 * Step 2 of Phase 1: the theme.
 *
 * The screen below is a test card, not a feature. It exists so the typeface,
 * the palette and the tabular figures can be checked on the actual phone in
 * the actual lighting before any real screen depends on them. Step 3 replaces
 * it with the protocol.json loader.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CutCoachTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ThemeTestCard()
                }
            }
        }
    }
}

@Composable
private fun ThemeTestCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("CutCoach", style = MaterialTheme.typography.displaySmall, color = SunriseAmber)
        Text(
            "Atkinson Hyperlegible, dark by default.",
            style = MaterialTheme.typography.bodyMedium,
            color = MistDim,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // The timer is where the boldness is spent. This is the real size.
        Section("The timer, at full size") {
            Text("01:00", style = MaterialTheme.typography.displayLarge, color = SunriseAmber)
            Text("WORK", style = MaterialTheme.typography.labelLarge, color = SunriseAmber)
            Text("00:45", style = MaterialTheme.typography.displayLarge, color = CoolSky)
            Text("REST", style = MaterialTheme.typography.labelLarge, color = CoolSky)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // If tabular figures are working, these two rows are exactly as wide as
        // each other. If the font feature failed, the 1s row is visibly shorter.
        Section("Tabular figures") {
            Text(
                "11111111",
                style = MaterialTheme.typography.headlineMedium.tabular(),
                color = Mist,
            )
            Text(
                "88888888",
                style = MaterialTheme.typography.headlineMedium.tabular(),
                color = Mist,
            )
            Text(
                "Both rows must be the same width.",
                style = MaterialTheme.typography.bodySmall,
                color = MistDim,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Section("Type scale") {
            Text("Strength A", style = MaterialTheme.typography.headlineMedium, color = Mist)
            Text("Push and legs", style = MaterialTheme.typography.titleMedium, color = Mist)
            Text(
                "Hands slightly wider than your shoulders. Body in one straight " +
                    "line from head to heels, glutes and abs tight.",
                style = MaterialTheme.typography.bodyLarge,
                color = Mist,
            )
            Text("Set 3 of 4 - target 8 to 15 reps", style = MaterialTheme.typography.bodyMedium.tabular(), color = MistDim)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Section("Palette") {
            Swatch("Pre-dawn navy", PreDawnNavy)
            Swatch("Slate", Slate)
            Swatch("Mist", Mist)
            Swatch("Sunrise amber", SunriseAmber)
            Swatch("Cool sky", CoolSky)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MistDim,
        )
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box56(color)
        Text(name, style = MaterialTheme.typography.bodyMedium, color = Mist)
    }
}

@Composable
private fun Box56(color: Color) {
    Column(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        content = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF101B2D, heightDp = 1400)
@Composable
private fun ThemeTestCardPreview() {
    CutCoachTheme { ThemeTestCard() }
}
