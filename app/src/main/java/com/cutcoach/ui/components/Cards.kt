package com.cutcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cutcoach.ui.theme.Ink
import com.cutcoach.ui.theme.Surface
import com.cutcoach.ui.theme.SurfaceEdge
import com.cutcoach.ui.theme.TextHigh
import com.cutcoach.ui.theme.TextLow
import com.cutcoach.ui.theme.TextMid
import com.cutcoach.ui.theme.tabular

/**
 * The shared furniture.
 *
 * The screens this replaced were a stack of identical panels, each one a small
 * heading over a column of label-on-the-left value-on-the-right rows. Every
 * fact was the same size and the same weight as every other, so nothing could
 * be found at a glance and the whole thing read as a form.
 *
 * So the pieces here are deliberately unequal. [HeroCard] is allowed to be
 * large and coloured, and there is one of it per screen. [StatTile] gives a
 * number room to be a number. [LabelledValue] survives for the genuinely
 * list-shaped things — a nutrition target, a setting — and is now the quietest
 * thing on the page rather than the only thing on it.
 */

/**
 * A translucent panel.
 *
 * White at low opacity rather than an opaque fill, so a card sitting over
 * artwork picks it up instead of punching a hole through it.
 */
@Composable
fun CutCard(
    modifier: Modifier = Modifier,
    background: Color = Surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(BorderStroke(1.dp, SurfaceEdge), RoundedCornerShape(20.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

/**
 * The one loud thing on a screen: a full-bleed gradient panel.
 *
 * [art] draws behind the content across the whole box, so a figure can bleed
 * off the edge rather than sit in a slot.
 */
@Composable
fun HeroCard(
    brush: Brush,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    art: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(brush)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        art?.invoke(this)
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = { content() },
        )
    }
}

/**
 * A small all-caps heading.
 *
 * Wide letter-spacing and a dim colour: findable when you are scanning for it,
 * invisible when you are not.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = TextLow) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        ),
        color = color,
        modifier = modifier,
    )
}

/**
 * One number, given room.
 *
 * This is the shape the old screens were missing: a figure readable at arm's
 * length with the phone on the floor, rather than a figure at the right-hand
 * end of a sentence.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    valueColor: Color = TextHigh,
    progress: Float? = null,
    progressColor: Color = valueColor,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, SurfaceEdge), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SectionLabel(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = valueColor,
            )
            unit?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                )
            }
        }
        progress?.let { MeterLine(it, progressColor) }
        caption?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
    }
}

/** A hairline progress bar, for under a [StatTile]'s number. */
@Composable
fun MeterLine(progress: Float, colour: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(SurfaceEdge),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colour),
        )
    }
}

/** A small rounded tag: a status, a count, a category. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    colour: Color = TextMid,
    fill: Color = Surface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        ),
        color = colour,
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(fill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * A label on the left and its value on the right.
 *
 * Kept for the genuinely list-shaped things, and no longer the only tool
 * available. Numbers use tabular figures so a column of them lines up.
 */
@Composable
fun LabelledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextHigh,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMid)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = valueColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The one button on a screen meant to be pressed without looking.
 *
 * The vertical padding plus the line height clears the 56 dp from the design
 * direction; this is pressed one-handed, half awake, with the phone on the
 * floor.
 */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Ink,
    content: Color = TextHigh,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = content,
        )
    }
}
