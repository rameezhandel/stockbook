package com.stockbook.app.feature.bills

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
import com.stockbook.core.model.Bill
import com.stockbook.core.model.Currency
import com.stockbook.core.money.Money
import com.stockbook.core.text.Strings

/**
 * A bill as it appears on Today and on Bills. Tapping one opens the document.
 *
 * The row deliberately carries no destructive action: voiding lives inside the
 * opened bill, so reaching it costs a considered tap and the list stays a list.
 */
@Composable
fun BillRow(
    bill: Bill,
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
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // A bill entered as a figure has no names to list, so the row
                // leads with what the shop calls it instead — the number on the
                // paper, which is what somebody asking about it will quote.
                // Falling through to `summary` would leave the row headed by
                // nothing at all.
                if (bill.isItemised) bill.summary else bill.reference(strings),
                style = NocturneType.rowValue,
                color = if (bill.voided) Nocturne.neutral500 else Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                meta(bill, currency, strings),
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            Money.text(bill.total, currency),
            style = NocturneType.inter(15.0),
            color = when {
                bill.voided -> Nocturne.neutral500
                bill.isPartPaid -> Nocturne.accent400
                else -> Nocturne.text
            }
        )
        Spacer(Modifier.width(8.dp))
        Glyph(Icon.openRow, size = 12.dp, tint = Nocturne.neutral600)
    }
}

/** `voided · Ahmed Contracting · 09:41 · 2 items · owes SAR 94` */
private fun meta(bill: Bill, currency: Currency, strings: Strings): String {
    val parts = mutableListOf<String>()
    if (bill.voided) parts.add(strings.voided)
    if (bill.who.isNotBlank()) parts.add(bill.who)
    parts.add(strings.time(bill.createdAt))
    // "0 items" is not a fact about a bill entered as a total; it is the app
    // insisting on a count of something nobody said anything about.
    if (bill.isItemised) parts.add(strings.items(bill.lines.size))
    if (bill.isPartPaid && bill.balance > 0) {
        parts.add(strings.owes(Money.text(bill.balance, currency)))
    }
    return parts.joinToString(" · ")
}
