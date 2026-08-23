package com.stockbook.app.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Bottom sheets, drawn by the app rather than by Material's `ModalBottomSheet`.
 *
 * The design is specific in ways the system sheet does not expose: an
 * `rgba(16,17,28,0.74)` scrim, rounding on the **top corners only**, a maximum
 * height of 84%, and the tab bar staying visible behind the scrim. Drawing it
 * here gives all of that.
 *
 * There is **no grab handle**. The design called for one and it was drawn, but
 * this sheet has no drag gesture — the handle was an invitation to do something
 * that did nothing. A sheet closes by its own Close or Done button, or by
 * tapping the scrim. Either add the gesture or do not draw the affordance;
 * drawing it alone is the one option that teaches the owner the app ignores
 * them.
 */
@Composable
fun BottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!visible) return

    // The system back gesture closes the sheet, not the app.
    //
    // Written once here rather than at each of the dozen call sites, and it lands
    // on the right one for free: `BackHandler` hands the press to the callback
    // registered *last*, and a sheet is composed after the screen it covers. A
    // sheet opened over the statement therefore closes itself and leaves the
    // statement standing, which is what the layering already says on screen.
    BackHandler(onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize()) {
        // The scrim swallows taps, which is how a sheet is dismissed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Nocturne.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        AnimatedVisibility(
            visible = true,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.84f)
                    .clip(RoundedCornerShape(topStart = Metrics.sheetRadius, topEnd = Metrics.sheetRadius))
                    .background(Nocturne.surface)
                    // A tap inside must not reach the scrim behind it.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                // The header still needs air above it, or it sits against the
                // rounded corner.
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Metrics.screenPadding)
                        .navigationBarsPadding()
                        // Every sheet in the app carries fields, so this one line
                        // lifts all of them clear of the keyboard rather than each
                        // sheet remembering to.
                        .imePadding()
                        .padding(bottom = 32.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/** The title row every sheet opens with: heading, optional sub-line, close. */
@Composable
fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth().padding(bottom = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = NocturneType.sheetTitle, color = Nocturne.text)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        IconButton(Icon.close, onClick = onClose, size = 16.dp, tint = Nocturne.neutral500)
    }
}
