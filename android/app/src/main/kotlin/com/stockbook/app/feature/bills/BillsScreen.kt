package com.stockbook.app.feature.bills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.feature.book.PartyList
import com.stockbook.app.feature.book.PartyRow
import com.stockbook.app.design.EmptyStateBox
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.ScreenHeader
import com.stockbook.core.model.Customer
import com.stockbook.core.model.ShopState
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * The sales half of the book: who the shop sells to, then every bill it has
 * written, newest first.
 *
 * The customers came first deliberately. This screen used to open on the bills
 * with a customer *filter* above them, which made a person something you narrowed
 * a list by rather than something you could go and look at. What is owed is the
 * question this half of the book exists to answer, and the people are where the
 * answer lives — so they are what it opens on, and the bills are the ledger
 * underneath.
 *
 * Nothing on either list corrects anything. A bill entered wrong is opened first,
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

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) ScreenHeader(title = strings.billsTitle, bottomPadding = 10.dp)

        LazyColumn(
            contentPadding = PaddingValues(
                start = Metrics.screenPadding,
                end = Metrics.screenPadding,
                bottom = 18.dp
            )
        ) {
            item {
                // `customers()` is a plain function over a StateFlow snapshot, so
                // the read has to be keyed on `state` or the list will not move
                // when a bill is written or a payment taken.
                val customers = remember(state) { store.customers().map { it.row() } }
                PartyList(
                    title = strings.customersTitle,
                    rows = customers,
                    search = { query -> store.customers(matching = query).map { it.row() } },
                    addTitle = strings.addACustomer,
                    emptyMessage = strings.noCustomersYet,
                    currency = currency,
                    strings = strings,
                    onAdd = { router.openNewCustomer() },
                    onOpen = { key -> router.openCustomerScreen(key) },
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                Kicker(strings.billsTitle, modifier = Modifier.padding(bottom = 8.dp))
            }

            if (state.bills.isEmpty()) {
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

            items(state.bills, key = { it.number }) { bill ->
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

/** `Customer` as the directory draws it. */
private fun Customer.row() = PartyRow(
    key = key,
    name = name,
    contact = listOfNotNull(phone, place).takeIf { it.isNotEmpty() }?.joinToString(" · "),
    owed = owed
)
