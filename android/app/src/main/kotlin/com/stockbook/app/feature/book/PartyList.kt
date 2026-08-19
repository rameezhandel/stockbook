package com.stockbook.app.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
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
 * The directory at the top of each half of the Book: who the shop deals with, and
 * the way into any one of them.
 *
 * This replaced a dropdown. A customer has a balance, a statement, payments and
 * credit notes against them, and until now the only way to reach any of it was to
 * pick a name out of a filter on a list of bills — which meant that people, the
 * thing half this app is about, were the one entity with no screen. The evidence
 * that this was wrong is that Today needed a banner *and* a purpose-built sheet
 * to get from "Ahmed still owes" to Ahmed.
 *
 * Capped rather than complete. A shop with two hundred customers should not have
 * to scroll past all of them to reach today's bills, so the list shows the few
 * who owe most — [com.stockbook.core.store.StockbookStore.customers] hands them
 * over in that order — and the rest are behind the search box or the toggle.
 */
@Composable
fun PartyList(
    title: String,
    rows: List<PartyRow>,
    /** Everybody, by name, matching what has been typed. Only read while searching. */
    search: (String) -> List<PartyRow>,
    addTitle: String,
    emptyMessage: String,
    currency: Currency,
    strings: Strings,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val searching = query.isNotBlank()
    val shown = when {
        searching -> search(query)
        expanded -> rows
        else -> rows.take(VISIBLE)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Kicker(title, modifier = Modifier.weight(1f))
            GhostButton(addTitle, onClick = onAdd, fontSize = 12.0)
        }
        Spacer(Modifier.height(8.dp))

        if (rows.isEmpty()) {
            Text(emptyMessage, style = NocturneType.meta, color = Nocturne.neutral500)
            return@Column
        }

        // Offered only once the list is longer than it is worth reading through.
        // A shop with four customers does not need a way to search four names.
        if (rows.size > VISIBLE) {
            NocturneField(
                value = query,
                onValueChange = { query = it },
                placeholder = strings.search,
                height = 40.dp,
                fontSize = 13.5,
                modifier = Modifier.padding(bottom = Metrics.rowGap)
            )
        }

        if (shown.isEmpty()) {
            Text(strings.nobodyMatches, style = NocturneType.meta, color = Nocturne.neutral500)
            return@Column
        }

        for (row in shown) {
            PartyRowView(
                row = row,
                currency = currency,
                strings = strings,
                onClick = { onOpen(row.key) },
                modifier = Modifier.padding(bottom = Metrics.rowGap)
            )
        }

        // Nothing to expand while searching: the search already showed everybody
        // who answers to what was typed.
        if (!searching && !expanded && rows.size > VISIBLE) {
            GhostButton(strings.all, onClick = { expanded = true }, fontSize = 12.0)
        }
    }
}

/** How many fit above the documents before the list stops being a summary. */
private const val VISIBLE = 5

/**
 * One name and what stands against it.
 *
 * The balance is the row's whole reason to be read, so it is what the eye lands
 * on: accent where money is outstanding, neutral where it is not. "Settled up"
 * rather than a blank — the absence of a figure reads as a row that failed to
 * load.
 */
@Composable
private fun PartyRowView(
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
