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
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.design.SecondaryButton
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
 * Nothing on the list corrects anything. A bill entered wrong is opened first,
 * and edited or removed from inside the document — which is the only place the
 * owner can see what they are about to change.
 */
@Composable
fun BillsScreen(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    /** False inside the book, which carries one header for both halves. */
    showHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    val currency = state.settings.currency
    val customers = remember(state) { store.customers() }

    /** The customer key, or empty for everyone. A lookup, not a mode. */
    var customerKey by remember { mutableStateOf("") }
    val selected = customers.firstOrNull { it.key == customerKey }
    val bills = if (selected == null) state.bills else store.billsForCustomer(customerKey)

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) ScreenHeader(title = strings.billsTitle, bottomPadding = 10.dp)

        // The filter is the app's customer surface, so adding one lives beside it
        // rather than in Settings.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.screenPadding)
                .padding(bottom = 10.dp)
        ) {
            if (customers.isEmpty()) {
                // Nothing to filter yet, so the button says what it does instead
                // of being an icon beside an absent dropdown.
                SecondaryButton(
                    strings.addACustomer,
                    onClick = { router.openNewCustomer() },
                    fullWidth = true,
                    height = Metrics.inputHeight,
                    fontSize = 13.0
                )
            } else {
                val options = remember(customers) { listOf<Customer?>(null) + customers }
                DropdownField(
                    options = options,
                    selected = selected,
                    onSelect = { customerKey = it?.key ?: "" },
                    title = { it?.name ?: strings.allCustomers },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                SecondaryButton(
                    "",
                    onClick = { router.openNewCustomer() },
                    height = Metrics.inputHeight,
                    leading = Icon.customer
                )
            }
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
                    CustomerSummary(
                        customer = selected,
                        currency = currency,
                        strings = strings,
                        onEdit = { router.openCustomer(selected) },
                        onStatement = { router.openStatement(selected) },
                        onRecordPayment = { router.paymentFor = selected },
                        onCreditNote = {
                            router.editingCreditNote = null
                            router.creditNoteFor = selected
                        }
                    )
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
 * What one customer has bought and what they still owe, above their bills.
 *
 * Both figures come from the customer the roster merged together, so a name
 * spelled two ways on two bills is one person with one balance here.
 */
@Composable
private fun CustomerSummary(
    customer: Customer,
    currency: com.stockbook.core.model.Currency,
    strings: Strings,
    onEdit: () -> Unit,
    onStatement: () -> Unit,
    onRecordPayment: () -> Unit,
    onCreditNote: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .hairline(radius = Metrics.cardRadius)
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Glyph(Icon.customer, size = 16.dp, tint = Nocturne.accent)
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    customer.name,
                    style = NocturneType.rowPrimary,
                    color = Nocturne.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val contact = listOfNotNull(customer.phone, customer.place)
                if (contact.isNotEmpty()) {
                    Text(
                        contact.joinToString(" · "),
                        style = NocturneType.meta,
                        color = Nocturne.neutral500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Editing is where a phone number gets added to somebody who has only
            // ever been a name on a bill.
            IconButton(
                Icon.edit,
                onClick = onEdit,
                size = 13.dp,
                tint = Nocturne.neutral500,
                contentDescription = strings.editCustomer
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
                value = when {
                    customer.owed > 0 -> Money.text(customer.owed, currency)
                    customer.owed < 0 -> strings.inAdvance(Money.text(-customer.owed, currency))
                    else -> strings.nothingPending
                },
                detail = null,
                tint = if (customer.owed > 0) Nocturne.accent400 else Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
        }

        // The statement across the whole width, and the two things that write to
        // the account beneath it. The statement is the one that only *reads* —
        // it is what somebody opens to answer a question, where the two below it
        // change what the customer owes.
        SecondaryButton(
            strings.statement,
            onClick = onStatement,
            fullWidth = true,
            height = 38.dp,
            fontSize = 12.5,
            modifier = Modifier.fillMaxWidth().padding(top = 11.dp)
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            // Always offered, including to somebody who owes nothing.
            //
            // It used to appear only while there was a balance, which meant that
            // settling up in full took the button away — and money comes over a
            // counter in more than one instalment, sometimes ahead of the bill.
            // The sheet has always handled that case: pay more than is owed and
            // it says so, "SAR 200 in advance". Hiding the way in while the sheet
            // knew what to do was the app disagreeing with itself.
            PrimaryButton(
                strings.recordAPayment,
                onClick = onRecordPayment,
                fullWidth = true,
                height = 38.dp,
                fontSize = 12.5,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            // Offered even to somebody who owes nothing: goods come back after a
            // bill has been settled, and that leaves them in credit. Secondary
            // beside the payment, because taking money is the daily act and
            // writing some off is the occasional one.
            SecondaryButton(
                strings.issueACreditNote,
                onClick = onCreditNote,
                fullWidth = true,
                height = 38.dp,
                fontSize = 12.5,
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
