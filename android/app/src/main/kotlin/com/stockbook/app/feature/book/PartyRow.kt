package com.stockbook.app.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.card
import com.stockbook.core.model.Currency
import com.stockbook.core.money.Money
import com.stockbook.core.text.Strings

/**
 * One person on the list, flattened out of `Customer` or `Supplier`.
 *
 * The two are the same shape pointed in opposite directions, but they are
 * separate types in the domain and giving them a shared interface there would be
 * inventing a concept the arithmetic does not have. Flattening at the edge, where
 * the only question is what to draw, costs one data class and keeps the domain
 * honest.
 */
data class PartyRow(val key: String, val name: String, val contact: String?, val owed: Double)

/**
 * One name and what stands against it.
 *
 * Drawn by `PeopleScreen`, which holds the list itself — this used to sit under
 * a `PartyList` that capped the directory at five names, and the cap went when
 * the screen became lazy.
 *
 * The balance is the row's whole reason to be read, so it is what the eye lands
 * on: accent where money is outstanding, neutral where it is not. "Settled up"
 * rather than a blank — the absence of a figure reads as a row that failed to
 * load.
 */
@Composable
internal fun PartyRowView(
    row: PartyRow,
    currency: Currency,
    strings: Strings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .card(Metrics.rowRadius)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Glyph(Icon.customer, size = 14.dp, tint = Nocturne.neutral500)
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.name,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            row.contact?.let {
                Text(
                    it,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                row.owed > 0 -> Money.text(row.owed, currency)
                row.owed < 0 -> strings.inAdvance(Money.text(-row.owed, currency))
                else -> strings.settledUp
            },
            style = NocturneType.inter(13.0),
            color = if (row.owed > 0) Nocturne.accent400 else Nocturne.neutral500,
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        Glyph(Icon.openRow, size = 12.dp, tint = Nocturne.neutral500)
    }
}
