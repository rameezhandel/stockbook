package com.stockbook.app.feature.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.ChoicePill
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
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
 * **Everybody, not the top five.** The list used to stop at five names with an
 * "All" underneath, because this screen was one long `Column` — every row was
 * built whether or not it was on screen, so two hundred customers meant two
 * hundred rows composed to show five. A `LazyColumn` builds only what is
 * visible, which removes the reason for the cap rather than moving it: no page
 * size, no button, just the list.
 *
 * Nothing to fetch, either. The roster is already in memory — `customers()` is a
 * walk over a snapshot — so there is no page to load, only rows to draw, and
 * drawing them on demand is the whole of it.
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
    var side by rememberSaveable { mutableStateOf(PeopleSide.CUSTOMERS) }

    /**
     * What has been typed into the search box.
     *
     * Cleared when the side changes: a query that found three customers means
     * nothing against the suppliers, and leaving it there shows an empty list
     * under a box the owner has to notice before the screen makes sense.
     */
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()

    val listState = rememberLazyListState()
    LaunchedEffect(side) {
        query = ""
        listState.scrollToItem(0)
    }

    // `customers()` and `suppliers()` are plain functions over a StateFlow
    // snapshot, so the read has to be keyed on `state` or the list will not move
    // when a bill is written or a payment taken.
    //
    // No `else` on purpose: a third kind of person has to break these and be
    // placed deliberately.
    val everybody = remember(state, side) {
        when (side) {
            PeopleSide.CUSTOMERS -> store.customers().map { it.row() }
            PeopleSide.SUPPLIERS -> store.suppliers().map { it.row() }
        }
    }
    val shown = remember(state, side, query) {
        if (!searching) everybody else when (side) {
            PeopleSide.CUSTOMERS -> store.customers(matching = query).map { it.row() }
            PeopleSide.SUPPLIERS -> store.suppliers(matching = query).map { it.row() }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
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
                selected = side == PeopleSide.CUSTOMERS,
                onClick = { side = PeopleSide.CUSTOMERS },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            ChoicePill(
                title = strings.suppliersTitle,
                icon = Icon.items,
                selected = side == PeopleSide.SUPPLIERS,
                onClick = { side = PeopleSide.SUPPLIERS },
                modifier = Modifier.weight(1f)
            )
        }

        // Weighted, not wrapped. A `Column` measures an unweighted child against
        // the *full* remaining height, so the list would run off the bottom by
        // exactly the height of the header and chips above it.
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Metrics.screenPadding,
                end = Metrics.screenPadding,
                bottom = 18.dp
            )
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Kicker(
                        if (side == PeopleSide.CUSTOMERS) strings.customersTitle else strings.suppliersTitle,
                        modifier = Modifier.weight(1f)
                    )
                    GhostButton(
                        if (side == PeopleSide.CUSTOMERS) strings.addACustomer else strings.addASupplier,
                        onClick = {
                            if (side == PeopleSide.CUSTOMERS) router.openNewCustomer()
                            else router.openNewSupplier()
                        },
                        fontSize = 12.0
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Offered only once the list is longer than it is worth reading
                // through. A shop with four customers does not need a way to
                // search four names.
                if (everybody.size > SEARCHABLE) {
                    NocturneField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = strings.search,
                        height = 40.dp,
                        fontSize = 13.5,
                        modifier = Modifier.padding(bottom = Metrics.rowGap)
                    )
                }
            }

            if (shown.isEmpty()) {
                item {
                    Text(
                        when {
                            searching -> strings.nobodyMatches
                            side == PeopleSide.CUSTOMERS -> strings.noCustomersYet
                            else -> strings.noSuppliersYet
                        },
                        style = NocturneType.meta,
                        color = Nocturne.neutral500
                    )
                }
            }

            items(shown, key = { it.key }) { row ->
                PartyRowView(
                    row = row,
                    currency = currency,
                    strings = strings,
                    onClick = {
                        if (side == PeopleSide.CUSTOMERS) router.openCustomerScreen(row.key)
                        else router.openSupplierScreen(row.key)
                    },
                    modifier = Modifier.padding(bottom = Metrics.rowGap)
                )
            }
        }
    }
}

/**
 * Above how many names a search box earns its place.
 *
 * Not a page size — the list shows everybody. This is only the point at which
 * scrolling stops being the quickest way to find one.
 */
private const val SEARCHABLE = 5

/**
 * Which side of the counter is showing.
 *
 * Not `Side`, which `BookScreen` already has in this package. A top-level
 * `private` in Kotlin hides the declaration from other *files* but still puts
 * the name in the package, so two of them collide — and the error it gives is
 * "Redeclaration" pointing at the innocent file.
 */
private enum class PeopleSide { CUSTOMERS, SUPPLIERS }

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
