package com.stockbook.app.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The hairline edge that stands in for elevation on this dark ground. Never a
 * stack of shadows — the design is explicit that depth is one 1px line.
 */
fun Modifier.hairline(color: Color = Nocturne.neutral800, radius: Dp): Modifier =
    border(BorderStroke(Metrics.hairline, color), RoundedCornerShape(radius))

/** A card: surface ground, rounded, with the hairline. */
fun Modifier.card(radius: Dp = Metrics.cardRadius): Modifier =
    clip(RoundedCornerShape(radius)).background(Nocturne.surface)

/**
 * One of the three spans a figure can be shown over: this month, last month,
 * this year.
 *
 * Here rather than beside either screen that uses it, because it was written
 * twice and the two drifted. The copy on Expenses lost `TextAlign.Center`, so
 * three equal-width chips held their labels against the left edge; it also drew
 * `card()` whether or not it was the chosen one, on a card of the same colour,
 * which left the ground doing nothing to say which span was showing. Home's
 * copy — this one — was right, and matched both iPhone screens.
 *
 * Sized by the caller: every use hands it `Modifier.weight(1f)` in a row, so the
 * three come out equal whatever the words are.
 */
@Composable
fun SpanChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        title,
        style = NocturneType.inter(11.5),
        color = if (selected) Nocturne.accent else Nocturne.neutral500,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(Metrics.controlRadius))
            .background(if (selected) Nocturne.primaryPressed else Color.Transparent)
            .hairline(
                if (selected) Nocturne.accent else Nocturne.divider,
                Metrics.controlRadius
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp)
    )
}

/**
 * A date the owner can change: a label, the date itself, and a calendar behind
 * a tap.
 *
 * The box is `NocturneField`'s, deliberately. This sits beside one on all four
 * forms that record a document, and drawn as a bare line of accent text — which
 * is what it was — it read as a label rather than as something that could be
 * tapped. Everything the owner can change on these forms now looks the same.
 *
 * The dialog itself stays at the call site: each form owns the state the picker
 * writes into, and hoisting it in here would mean handing back a `Long` from
 * `DatePickerState` and re-anchoring the time zone in four places instead of
 * one. See the iOS twin, `NocturneDateField`.
 */
@Composable
fun DateField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Metrics.inputHeight
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = NocturneType.fieldLabel,
            color = Nocturne.neutral400,
            modifier = Modifier.padding(bottom = 5.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(Metrics.controlRadius))
                .background(Nocturne.bg)
                .hairline(Nocturne.neutral800, Metrics.controlRadius)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                value,
                style = NocturneType.inter(13.0),
                color = Nocturne.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A small uppercase section label — "RECENT BILLS". */
@Composable
fun Kicker(text: String, tint: Color = Nocturne.neutral500, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = NocturneType.kicker,
        color = tint,
        modifier = modifier
    )
}

@Composable
fun Glyph(
    icon: ImageVector,
    size: Dp = 16.dp,
    tint: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    M3Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (tint == Color.Unspecified) Nocturne.text else tint,
        modifier = modifier.size(size)
    )
}

/**
 * The header block every screen opens with: a title, an optional kicker above
 * and sub-line below, and one action on the right.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    kickerTint: Color = Nocturne.accent,
    /**
     * Makes the kicker itself the way somewhere, with a caret after it so it
     * reads as one. Home's kicker is today's date and tapping it opens that day;
     * a date that silently did something when touched, and looked like every
     * other kicker in the app, would be found by accident or not at all.
     */
    onKicker: (() -> Unit)? = null,
    subtitle: String? = null,
    bottomPadding: Dp = 12.dp,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Metrics.screenPadding)
            .padding(top = 10.dp, bottom = bottomPadding)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (kicker != null) {
                if (onKicker == null) {
                    Kicker(kicker, tint = kickerTint, modifier = Modifier.padding(bottom = 3.dp))
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Metrics.controlRadius))
                            .clickable(onClick = onKicker)
                            // Room for a thumb around a line of 11pt type, and
                            // the negative start keeps the text itself aligned
                            // with the title under it.
                            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                            .offset(x = (-4).dp)
                    ) {
                        Kicker(kicker, tint = kickerTint)
                        Glyph(Icon.stepForward, size = 13.dp, tint = kickerTint)
                    }
                    Spacer(Modifier.height(1.dp))
                }
            }
            Text(title, style = NocturneType.screenTitle, color = Nocturne.text)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** A stat card on Today. The "Sold today" card is the one gradient in the app. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    gradient: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Metrics.statRadius))
            .then(if (gradient) Modifier.background(Nocturne.statCardGradient) else Modifier.background(Nocturne.surface))
            .hairline(radius = Metrics.statRadius)
            .padding(14.dp)
    ) {
        Text(label, style = NocturneType.inter(11.0), color = Nocturne.neutral500)
        Text(
            value,
            style = NocturneType.fittedNumber(value),
            color = Nocturne.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/** The standard empty state: optional icon, a line of copy, one primary action. */
@Composable
fun EmptyStateBox(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .dashedBox()
            .padding(horizontal = 18.dp, vertical = 26.dp)
    ) {
        if (icon != null) {
            Glyph(icon, size = 26.dp, tint = Nocturne.neutral600)
            Spacer(Modifier.height(9.dp))
        }
        Text(
            message,
            style = NocturneType.body,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (actionTitle != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(actionTitle, onClick = onAction, compact = true)
        }
    }
}

// MARK: Buttons
//
// Written as composables rather than Material buttons: the design specifies its
// own heights, radii, pressed fills and disabled opacity, and fighting
// Material's defaults to reach them costs more than drawing them.

/**
 * The primary action: a 1px accent border on transparent, accent text, and a faint
 * accent wash while held.
 *
 * **Nothing in this design is filled with the accent** — the iOS style says so in
 * as many words, and this had been built the other way round: a solid accent fill
 * with near-black text. Besides being wrong, it made the button disappear the
 * instant it was pressed, because the pressed token is a 22% wash meant to sit
 * *over* a ground rather than to replace one.
 *
 * One per screen, and never two side by side.
 */
@Composable
fun PrimaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
    height: Dp = Metrics.primaryButtonHeight,
    fontSize: Double = 14.0,
    compact: Boolean = false,
    leading: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(if (compact) 34.dp else height)
            .clip(RoundedCornerShape(Metrics.controlRadius))
            // Transparent, washed faintly when held — not filled. `primaryPressed`
            // is accent at 22% opacity, which is a *wash over* the ground and was
            // being used here as a replacement *for* the ground: the moment the
            // button was touched a solid accent fill collapsed to a barely-there
            // tint carrying near-black text, and the button vanished under the
            // thumb that pressed it.
            .background(if (pressed) Nocturne.primaryPressed else Color.Transparent)
            .hairline(Nocturne.accent, Metrics.controlRadius)
            .alpha(if (enabled) 1f else Nocturne.DISABLED_OPACITY)
            .clickable(interaction, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 16.dp)
    ) {
        if (leading != null) {
            Glyph(leading, size = 15.dp, tint = Nocturne.accent)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            title,
            style = NocturneType.inter(if (compact) 12.5 else fontSize, androidx.compose.ui.text.font.FontWeight.Medium),
            color = Nocturne.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SecondaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
    height: Dp = Metrics.primaryButtonHeight,
    fontSize: Double = 14.0,
    leading: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(height)
            .clip(RoundedCornerShape(Metrics.controlRadius))
            .background(if (pressed) Nocturne.secondaryPressed else Color.Transparent)
            .hairline(radius = Metrics.controlRadius)
            .alpha(if (enabled) 1f else Nocturne.DISABLED_OPACITY)
            .clickable(interaction, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        if (leading != null) {
            Glyph(leading, size = 15.dp, tint = Nocturne.text)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            title,
            style = NocturneType.inter(fontSize),
            color = Nocturne.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GhostButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Double = 12.5,
    tint: Color = Nocturne.accent
) {
    Text(
        text = title,
        style = NocturneType.inter(fontSize, androidx.compose.ui.text.font.FontWeight.Medium),
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(Metrics.controlRadius))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Metrics.minimumTouchTarget)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

/** An icon-only tap target that meets the minimum size whatever the glyph is. */
@Composable
fun IconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = Nocturne.text,
    contentDescription: String? = null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Metrics.minimumTouchTarget)
            .clip(RoundedCornerShape(Metrics.controlRadius))
            .clickable(onClick = onClick)
    ) {
        M3Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}

/** Screen-edge padding, applied once so no screen repeats the number. */
fun screenPadding() = PaddingValues(horizontal = Metrics.screenPadding)

/**
 * A 1px rule that fades to transparent at both ends.
 *
 * A Nocturne signature the handoff calls out explicitly and asks to preserve.
 * Used on the bill document, between the lines and the total.
 */
@Composable
fun FadedRule(modifier: Modifier = Modifier, inset: Dp = 24.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Metrics.hairline)
            .drawWithCache {
                val stop = (inset.toPx() / size.width).coerceIn(0f, 0.5f)
                val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to Color.Transparent,
                    stop to Nocturne.neutral800,
                    (1f - stop) to Nocturne.neutral800,
                    1f to Color.Transparent
                )
                onDrawBehind { drawRect(brush) }
            }
    )
}

/**
 * One of two or three answers, all of them on screen at once: an icon, a word,
 * and an accent outline on the one in force.
 *
 * Where a dropdown hides the alternatives behind a tap, this shows them — the
 * right trade for a short list somebody chooses by comparing (full payment
 * against part payment, dark against light) and the wrong one for fourteen
 * currencies.
 *
 * Started life as `PaymentPill` inside the cart, and moved here the second time a
 * screen needed it rather than the third.
 */
@Composable
fun ChoicePill(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(38.dp)
            .hairline(if (selected) Nocturne.accent else Nocturne.neutral800, Metrics.controlRadius)
            .clickable(onClick = onClick)
    ) {
        Glyph(icon, size = 14.dp, tint = if (selected) Nocturne.accent else Nocturne.neutral500)
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = NocturneType.inter(13.0, FontWeight.Medium),
            color = if (selected) Nocturne.accent else Nocturne.neutral500
        )
    }
}
