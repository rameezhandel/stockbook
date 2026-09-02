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
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.card
import com.stockbook.core.model.Currency
import com.stockbook.core.money.Money
import com.stockbook.core.store.DayEntryKind
import com.stockbook.core.store.SearchHit
import com.stockbook.core.text.Strings

/**
 * One result. Tapping it opens whatever the record is — a bill, a receipt, a
 * purchase, an expense.
 *
 * **It says what kind of thing it is, because the list is mixed.** Every other
 * list in the app is one kind of record, and its rows can leave that unsaid.
 * Here a name and a figure without a word beside them would be four rows the
 * owner has to open to tell apart.
 */
@Composable
internal fun SearchRow(
    hit: SearchHit,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                hit.who,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // The kind first, then the number that was probably typed to get
                // here. A record with no number says only what it is, which is
                // still the thing the row has to establish.
                buildString {
                    append(label(hit.kind, strings))
                    hit.reference?.let { append(" · $it") }
                },
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.text(hit.amount, currency),
                style = NocturneType.inter(14.0),
                color = Nocturne.text,
                maxLines = 1
            )
            Text(
                // The day, always. The lists in the book have a span above them
                // and can leave it out; a result could be from any year, which is
                // the point of searching rather than scrolling.
                strings.longDate(hit.at),
                style = NocturneType.meta,
                color = Nocturne.neutral500
            )
        }
    }
}

/**
 * What the row calls each kind. Singular: the plurals in `Strings` head lists.
 *
 * A `when` with no `else`, so a seventh kind of record stops this compiling
 * rather than quietly showing up unlabelled.
 */
private fun label(kind: DayEntryKind, strings: Strings): String = when (kind) {
    DayEntryKind.BILL -> strings.billLabel
    DayEntryKind.PAYMENT -> strings.paymentLabel
    DayEntryKind.CREDIT_NOTE -> strings.creditNoteLabel
    DayEntryKind.PURCHASE -> strings.purchaseLabel
    DayEntryKind.SUPPLIER_PAYMENT -> strings.voucherLabel
    DayEntryKind.EXPENSE -> strings.expenseLabel
}
