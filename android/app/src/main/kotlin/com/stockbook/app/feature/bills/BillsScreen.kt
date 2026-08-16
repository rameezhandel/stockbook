package com.stockbook.app.feature.bills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.DropdownField
import com.stockbook.app.design.EmptyStateBox
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.Customer
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * Every bill ever saved, newest first — and, filtered to one customer, the
 * answer to "what has this person bought and what do they still owe me?"
 *
 * Nothing here is deleted. A bill entered wrong is *voided*, which puts its
 * stock back and leaves the record in place with a mark.
 */
@Composable
fun BillsScreen(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    val currency = state.settings.currency
    val customers = remember(state) { store.customers() }

    /** The customer key, or empty for everyone. A lookup, not a mode. */
    var customerKey by remember { mutableStateOf("") }
    val selected = customers.firstOrNull { it.key == customerKey }
    val bills = if (selected == null) state.bills else store.billsForCustomer(customerKey)

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(title = strings.billsTitle, bottomPadding = 10.dp)

        if (customers.isNotEmpty()) {
            val options = remember(customers) { listOf<Customer?>(null) + customers }
            DropdownField(
                options = options,
                selected = selected,
                onSelect = { customerKey = it?.key ?: "" },
                title = { it?.name ?: strings.allCustomers },
                modifier = Modifier
                    .padding(horizontal = Metrics.screenPadding)
                    .padding(bottom = 10.dp)
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = Metrics.screenPadding,
                end = Metrics.screenPadding,
                bottom = 18.dp
            )
        ) {
            if (selected != null) {
                item {
                    CustomerSummary(selected, currency, strings)
                    Spacer(Modifier.height(Metrics.rowGap + 4.dp))
                }
            }

            if (bills.isEmpty()) {
                item {
                    EmptyStateBox(
                        icon = Icon.bills,
                        message = strings.noBillsEver,
                        actionTitle = strings.startABill,
                        onAction = { router.startBill() },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            items(bills, key = { it.number }) { bill ->
                BillRow(
                    bill = bill,
                    currency = currency,
                    strings = strings,
                    onClick = { router.openBill(bill) },
                    modifier = Modifier.padding(bottom = Metrics.rowGap)
                )
            }
        }
    }
}

/**
 * Both figures count **live bills only** — a voided bill did not happen, so it
 * is neither a sale nor a debt. It still appears in the list below, marked,
 * because history is never hidden.
 */
@Composable
private fun CustomerSummary(
    customer: Customer,
    currency: com.stockbook.core.model.Currency,
    strings: Strings
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .hairline(radius = Metrics.cardRadius)
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(Icon.customer, size = 16.dp, tint = Nocturne.accent)
            Spacer(Modifier.width(9.dp))
            Text(
                customer.name,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Figure(
                label = strings.transactions,
                value = Money.text(customer.total, currency),
                detail = strings.bills(customer.billCount),
                modifier = Modifier.weight(1f)
            )
            Figure(
                label = strings.pendingPayment,
                value = if (customer.owed > 0) Money.text(customer.owed, currency) else strings.nothingPending,
                detail = null,
                tint = if (customer.owed > 0) Nocturne.accent400 else Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Figure(
    label: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier,
    tint: Color = Nocturne.text
) {
    Column(modifier = modifier) {
        Text(label, style = NocturneType.inter(11.0), color = Nocturne.neutral500)
        Text(
            value,
            style = NocturneType.inter(17.0),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (detail != null) {
            Text(detail, style = NocturneType.meta, color = Nocturne.neutral500)
        }
    }
}
