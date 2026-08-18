package com.stockbook.app.feature.bills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.FadedRule
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.Bill
import com.stockbook.core.model.Currency
import com.stockbook.core.money.Money
import com.stockbook.core.text.Strings

/**
 * One bill, drawn as the thing you turn round and show a customer.
 *
 * Used both for the confirmation right after saving and for opening any bill
 * from history, so those two can never drift apart.
 *
 * Every value here is the **snapshot taken at sale time**. A product renamed or
 * repriced since does not change what this says.
 */
@Composable
fun BillTemplate(
    bill: Bill,
    currency: Currency,
    strings: Strings,
    modifier: Modifier = Modifier,
    shopName: String = ""
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .card(Metrics.statRadius)
            .hairline(radius = Metrics.statRadius)
            .padding(16.dp)
    ) {
        if (shopName.isNotBlank()) {
            Kicker(shopName, modifier = Modifier.padding(bottom = 5.dp))
        }

        // Nothing sits beside the reference any more. A bill entered wrongly is
        // corrected on the document itself, so there is no mark a bill can be
        // carrying by the time anybody reads one.
        Text(
            bill.reference(strings),
            style = NocturneType.inter(20.0, FontWeight.Medium),
            color = Nocturne.text,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            strings.billWhen(strings.longDate(bill.createdAt), strings.time(bill.createdAt)),
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (bill.who.isNotBlank()) {
            Text(
                strings.billedTo(bill.who),
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // One rule where there is nothing between them, two where there is. A bill
        // entered as a figure has no lines to list, and the empty band of nothing
        // that a second rule opened up read as a document that had lost its
        // contents rather than one that never had any.
        FadedRule(modifier = Modifier.padding(vertical = 12.dp))

        if (bill.isItemised) {
            bill.lines.forEachIndexed { index, line ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.name, style = NocturneType.inter(14.0), color = Nocturne.text)
                        // The arithmetic stays visible. A customer querying a total
                        // is nearly always querying one line's quantity or price,
                        // and this is the answer without anyone recomputing it.
                        Text(
                            strings.quantityAtPrice(line.qty, Money.text(line.price, currency)),
                            style = NocturneType.meta,
                            color = Nocturne.neutral500,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Money.text(line.lineTotal, currency),
                        style = NocturneType.inter(14.0),
                        color = Nocturne.text
                    )
                }
            }

            FadedRule(modifier = Modifier.padding(vertical = 12.dp))
        }

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.total,
                style = NocturneType.inter(13.0),
                color = Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
            Text(
                Money.text(bill.total, currency),
                style = NocturneType.bigNumber(25.0),
                color = Nocturne.text
            )
        }

        Text(
            paymentNote(bill, currency, strings),
            style = NocturneType.inter(12.5),
            color = Nocturne.accent400,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

/**
 * Settled at the counter, or what is left and who owes it.
 */
private fun paymentNote(bill: Bill, currency: Currency, strings: Strings): String {
    val paid = bill.paid ?: return strings.paidInFullCash
    return strings.partPaidNote(
        paid = Money.text(paid, currency),
        who = bill.who,
        balance = Money.text(bill.balance, currency)
    )
}
