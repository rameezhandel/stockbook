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
import com.stockbook.core.store.PaymentEntry
import com.stockbook.core.text.Strings

/**
 * One slip, either direction. Tapping it opens the receipt it was written on.
 *
 * **The direction is a word, not a sign.** Both figures are money that moved and
 * both are positive; `SAR -900` beside a supplier's name would read as a refund,
 * which this app has no notion of. So the amount says how much and the line under
 * it says which way — "Received" or "Paid" — and the two are tinted apart so a
 * mixed list can be scanned without reading every line.
 */
@Composable
internal fun PaymentRow(
    entry: PaymentEntry,
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
                entry.who,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // The number on the slip is the whole reason to scroll this list
                // — an owner holding receipt 008455 is trying to remember who
                // paid it — so it leads. A payment taken without one has nothing
                // to match against and shows the day instead, which is the next
                // thing somebody would search by.
                entry.reference?.let { strings.paymentRef(it) } ?: strings.longDate(entry.at),
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.text(entry.amount, currency),
                style = NocturneType.inter(14.0),
                color = Nocturne.text,
                maxLines = 1
            )
            Text(
                if (entry.incoming) strings.receivedInPeriod else strings.paidOutInPeriod,
                style = NocturneType.meta,
                color = if (entry.incoming) Nocturne.accent else Nocturne.accent400
            )
        }
    }
}
