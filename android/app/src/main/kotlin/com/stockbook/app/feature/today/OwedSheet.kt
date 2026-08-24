package com.stockbook.app.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.card
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.Supplier
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * Everybody who owes the shop money, from the banner that says how many there
 * are.
 *
 * The banner is where the owner notices the debt and the payment sheet is where
 * it gets collected; before this there was no route between the two, and the way
 * to take Ahmed's cash was to remember to go and find Ahmed in the Book. One tap
 * on the thing you just read is the shortest that route can be.
 */
@Composable
fun WhoOwesYouSheet(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    currency: Currency,
    strings: Strings,
    /** Renders this list as a page and hands it to the chooser. */
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    // The whole roster is read and only the *list* is about debt. A customer who
    // owes nothing still walks in to pay a deposit or to settle a bill the
    // moment it is written, so the search box behind this list reaches
    // everybody — and the roster's size is what decides whether the box appears.
    //
    // Keyed on the state rather than read off the store bare: `customers()` is a
    // plain function over a StateFlow's current value and subscribes to nothing,
    // so a payment taken from inside this sheet would leave the row it settled
    // sitting here saying the old figure.
    val roster = remember(state) { store.customers() }
    val owing = remember(roster) { roster.filter { it.owed > 0 } }

    fun row(customer: Customer) =
        OwedRow(customer.name, customer.owed) {
            router.paymentFor = customer
            onClose()
        }

    OwedList(
        // The card that opens this sheet draws the same string. One word, one
        // string: a sheet with a title of its own is a title that drifts from
        // the card the thumb just touched.
        title = strings.receivableStat,
        rows = owing.map { row(it) },
        total = owing.sumOf { it.owed },
        rosterSize = roster.size,
        search = { query -> store.customers(matching = query).map { row(it) } },
        // The list is the document, so the button that makes it belongs here.
        // Only where there is something to chase: a page saying nobody owes
        // anything is a page nobody needs. Rendering and handing the file over is
        // the activity's, which is where every other share in this app is done.
        onSave = if (owing.isEmpty()) null else onSave,
        action = strings.takePayment,
        currency = currency,
        strings = strings,
        onClose = onClose
    )
}

/**
 * The same sheet for money going the other way. One body, two entry points, as
 * with the payment sheets themselves: what a debt *is* does not change with its
 * direction.
 */
@Composable
fun WhoYouOweSheet(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    currency: Currency,
    strings: Strings,
    /** Renders this list as a page and hands it to the chooser. */
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val roster = remember(state) { store.suppliers() }
    val owed = remember(roster) { roster.filter { it.owed > 0 } }

    fun row(supplier: Supplier) =
        OwedRow(supplier.name, supplier.owed) {
            router.supplierPaymentFor = supplier
            onClose()
        }

    OwedList(
        title = strings.payableStat,
        rows = owed.map { row(it) },
        total = owed.sumOf { it.owed },
        rosterSize = roster.size,
        search = { query -> store.suppliers(matching = query).map { row(it) } },
        onSave = if (owed.isEmpty()) null else onSave,
        // Money leaving, not arriving. "Take payment" beside a supplier the shop
        // owes describes the wrong direction entirely, and it is the one word on
        // this sheet a hurried thumb reads before tapping.
        action = strings.makePayment,
        currency = currency,
        strings = strings,
        onClose = onClose
    )
}

/** One name, what is outstanding against it, and the way to settle it. */
private data class OwedRow(val name: String, val amount: Double, val onTake: () -> Unit)

@Composable
private fun OwedList(
    title: String,
    rows: List<OwedRow>,
    /**
     * What is outstanding altogether, passed in rather than summed from [rows].
     *
     * The subtitle answers "how much is out there", and a search that narrowed
     * it to one name would leave the sheet's headline figure quietly following
     * the typing.
     */
    total: Double,
    /** How many names there are in all — what decides whether searching is offered. */
    rosterSize: Int,
    /** Everybody, by name or phone, matching what has been typed. */
    search: (String) -> List<OwedRow>,
    /** Makes a page of this list. Absent where there is no list worth making. */
    onSave: (() -> Unit)?,
    /**
     * What the row's button says. One body serves both directions, and the
     * direction is the whole of what this word carries.
     */
    action: String,
    currency: Currency,
    strings: Strings,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()
    // Searching leaves the debt list behind entirely: it answers from the whole
    // roster, so somebody who owes nothing today can still be found and paid.
    val shown = if (searching) search(query) else rows

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = title,
            subtitle = Money.text(total, currency),
            onClose = onClose
        )

        if (onSave != null) {
            SecondaryButton(
                strings.sharePdf,
                onClick = onSave,
                fullWidth = true,
                height = 40.dp,
                fontSize = 13.0
            )
            Spacer(Modifier.height(12.dp))
        }

        // Offered only once there are more names than are worth reading through,
        // and counted against the whole roster rather than the debt list: a shop
        // with three debtors and two hundred customers is exactly the shop that
        // needs to be able to find the other hundred and ninety-seven.
        if (rosterSize > SEARCHABLE) {
            NocturneField(
                value = query,
                onValueChange = { query = it },
                placeholder = strings.search,
                height = 40.dp,
                fontSize = 13.5,
                modifier = Modifier.padding(bottom = Metrics.rowGap)
            )
        }

        // The banner that opens this sheet only appears when somebody owes, so an
        // empty list here means the last of it was settled while the sheet was
        // open. Worth saying rather than leaving a blank sheet behind — and while
        // searching the answer is about the typing, not about the debt.
        if (shown.isEmpty()) {
            Text(
                if (searching) strings.nobodyMatches else strings.settledUp,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(vertical = 14.dp)
            )
            return@Column
        }

        // A plain Column rather than a lazy list: the sheet already scrolls, and a
        // shop with more debtors than fit in it has a bigger problem than this
        // screen. Sorted by what is owed — `customers()` and `suppliers()` both
        // hand them over that way.
        shown.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Metrics.rowGap)
                    .card(Metrics.controlRadius)
                    .clickable(onClick = row.onTake)
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Glyph(Icon.customer, size = 13.dp, tint = Nocturne.neutral500)
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.name,
                        style = NocturneType.rowPrimary,
                        color = Nocturne.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        Money.text(row.amount, currency),
                        style = NocturneType.meta,
                        // Accent is the colour of an outstanding figure. A name
                        // the search turned up who owes nothing reads in the
                        // ordinary grey, so nothing on the row says "debt" when
                        // there is none.
                        color = if (row.amount > 0) Nocturne.accent400 else Nocturne.neutral500
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Named rather than a chevron: the row goes somewhere specific,
                // and "Take payment" is the sentence the owner is already halfway
                // through when they tap it.
                GhostButton(action, onClick = row.onTake, fontSize = 12.0)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * How many names there must be before the sheet offers a way to search them.
 *
 * The same figure `PartyList` uses, for the same reason: a shop with four
 * customers does not need a box to find four names.
 */
private const val SEARCHABLE = 5
