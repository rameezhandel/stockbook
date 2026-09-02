package com.stockbook.app.feature.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.ChoicePill
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.ScreenHeader
import com.stockbook.core.model.Customer
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.Supplier
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppTab
import com.stockbook.core.text.Strings

/**
 * Everybody the shop deals with: who owes it, and who it owes.
 *
 * Its own tab because it is its own task. This was the top half of Reports, and
 * Reports was two screens sharing one scroll — a directory you come to in order
 * to **find somebody**, stacked on a ledger you come to in order to **browse
 * records**. Different verbs, and the chip row switched both at once, which is
 * why expenses — having no people — never fitted the pattern.
 *
 * **One tab for both sides, not two.** A shop looks up a name; which side of the
 * counter that name is on is something it already knows. Chips rather than tabs
 * for the same reason the Book's were: the two are not used symmetrically, and a
 * supplier is looked up a fraction as often as a customer.
 *
 * Nothing here corrects anything. A name is opened, and what can be done to it
 * lives on the party's own screen.
 */
@Composable
fun PeopleScreen(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    val currency = state.settings.currency

    /**
     * Which side is showing. `rememberSaveable` so it survives a trip into a
     * party's screen and back — somebody who came here for suppliers should not
     * be handed customers again on the way out.
     */
    var side by rememberSaveable { mutableStateOf(Side.CUSTOMERS) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 18.dp)
    ) {
        ScreenHeader(title = strings.tab(AppTab.PEOPLE), bottomPadding = 10.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.screenPadding)
                .padding(bottom = 12.dp)
        ) {
            ChoicePill(
                title = strings.customersTitle,
                icon = Icon.customer,
                selected = side == Side.CUSTOMERS,
                onClick = { side = Side.CUSTOMERS },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            ChoicePill(
                title = strings.suppliersTitle,
                icon = Icon.items,
                selected = side == Side.SUPPLIERS,
                onClick = { side = Side.SUPPLIERS },
                modifier = Modifier.weight(1f)
            )
        }

        // `customers()` and `suppliers()` are plain functions over a StateFlow
        // snapshot, so the read has to be keyed on `state` or the list will not
        // move when a bill is written or a payment taken.
        //
        // No `else` on purpose: a third kind of person has to break this and be
        // placed deliberately.
        when (side) {
            Side.CUSTOMERS -> {
                val rows = remember(state) { store.customers().map { it.row() } }
                PartyList(
                    title = strings.customersTitle,
                    rows = rows,
                    search = { query -> store.customers(matching = query).map { it.row() } },
                    addTitle = strings.addACustomer,
                    emptyMessage = strings.noCustomersYet,
                    currency = currency,
                    strings = strings,
                    onAdd = { router.openNewCustomer() },
                    onOpen = { key -> router.openCustomerScreen(key) },
                    modifier = Modifier.padding(horizontal = Metrics.screenPadding)
                )
            }
            Side.SUPPLIERS -> {
                val rows = remember(state) { store.suppliers().map { it.row() } }
                PartyList(
                    title = strings.suppliersTitle,
                    rows = rows,
                    search = { query -> store.suppliers(matching = query).map { it.row() } },
                    addTitle = strings.addASupplier,
                    emptyMessage = strings.noSuppliersYet,
                    currency = currency,
                    strings = strings,
                    onAdd = { router.openNewSupplier() },
                    onOpen = { key -> router.openSupplierScreen(key) },
                    modifier = Modifier.padding(horizontal = Metrics.screenPadding)
                )
            }
        }
    }
}

/** Which side of the counter is showing. */
private enum class Side { CUSTOMERS, SUPPLIERS }

/** `Customer` as the directory draws it. */
private fun Customer.row() = PartyRow(
    key = key,
    name = name,
    contact = listOfNotNull(phone, place).takeIf { it.isNotEmpty() }?.joinToString(" · "),
    owed = owed
)

/** And the same the other way round. */
private fun Supplier.row() = PartyRow(
    key = key,
    name = name,
    contact = listOfNotNull(phone, place).takeIf { it.isNotEmpty() }?.joinToString(" · "),
    owed = owed
)
