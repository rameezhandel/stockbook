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
import com.stockbook.core.model.Expense
import com.stockbook.core.money.Money
import com.stockbook.core.text.Strings

/**
 * One expense. Tapping it opens the sheet it was written on, which is where it
 * is corrected or removed — the same rule a bill and a delivery follow.
 */
@Composable
internal fun ExpenseRow(
    expense: Expense,
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
                expense.note,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                strings.pickedDate(expense.spentAt),
                style = NocturneType.meta,
                color = Nocturne.neutral500
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            Money.text(expense.amount, currency),
            style = NocturneType.inter(14.0),
            color = Nocturne.text,
            maxLines = 1
        )
    }
}
