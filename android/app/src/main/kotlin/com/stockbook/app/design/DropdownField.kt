package com.stockbook.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * "Pick one of these": a leading mark, the current choice, and a caret.
 *
 * The list is the platform's `DropdownMenu` — it already handles the scrolling,
 * the dismissal and the outside tap correctly on a phone held one-handed behind
 * a counter. Only the label is ours.
 */
@Composable
fun <T> DropdownField(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    title: (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    /**
     * The short recognisable stamp on the left — a currency symbol, a language
     * code. Null where there is no useful stamp, as with a customer name.
     */
    mark: ((T) -> String)? = null,
    rowTitle: (T) -> String = title
) {
    var open by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                label,
                style = NocturneType.fieldLabel,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }

        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Metrics.inputHeight)
                    .clip(RoundedCornerShape(Metrics.controlRadius))
                    .background(Nocturne.bg)
                    .hairline(radius = Metrics.controlRadius)
                    .clickable { open = true }
                    .padding(horizontal = 10.dp)
            ) {
                if (mark != null) {
                    Text(
                        mark(selected),
                        style = NocturneType.inter(15.0, FontWeight.Medium),
                        color = Nocturne.accent,
                        modifier = Modifier.widthIn(min = 34.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                }
                Text(
                    title(selected),
                    style = NocturneType.inter(14.0),
                    color = Nocturne.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Glyph(Icon.chooseFromList, size = 13.dp, tint = Nocturne.neutral500)
            }

            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.background(Nocturne.surface)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Glyph(
                                    Icon.confirm,
                                    size = 14.dp,
                                    tint = if (option == selected) Nocturne.accent else Nocturne.surface
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    rowTitle(option),
                                    style = NocturneType.inter(14.0),
                                    color = if (option == selected) Nocturne.accent else Nocturne.text
                                )
                            }
                        },
                        onClick = {
                            open = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}
