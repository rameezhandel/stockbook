package com.stockbook.app.feature.book

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.ChoicePill
import com.stockbook.app.design.EmptyStateBox
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PeriodChoice
import com.stockbook.app.design.PeriodPicker
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.app.feature.bills.BillRow
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.model.Timestamps
import com.stockbook.core.money.Money
import com.stockbook.core.text.Dates
import com.stockbook.core.store.DayEntryKind
import com.stockbook.core.store.SearchHit
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import com.stockbook.core.text.SummaryDocument
import java.time.Instant

/**
 * The account book: every record the shop keeps, one span at a time.
 *
 * **One ledger, not three panes.** This screen used to be a switch between three
 * separate screens, each of which owned its own idea of which days it was
 * showing: the sales and purchase lists carried a four-chip period picker, and
 * expenses carried a *different* three-chip one buried inside its total card. So
 * "this month" was three pieces of state, the expenses side could not be asked
 * for two dates at all, and switching chips silently threw away the span the
 * owner had just chosen.
 *
 * Now the span is asked once, above everything, and every record type answers
 * over it. Changing what you are looking at no longer changes when.
 *
 * The chips pick the **kind of record**: what was sold, what arrived, what
 * actually changed hands, and what the owner spent. Sales and Purchases are
 * mirror images in the domain — one `Statement.make` serves both. Payments is
 * the one list this app had no screen for at all: a receipt could only be
 * reached through the customer it belonged to, which is no help to an owner
 * holding receipt 008455 and trying to remember who paid it. Expenses is the odd
 * one, tied to nobody and touching neither side's arithmetic; it sits here
 * anyway, because it is money leaving and it is written down for the same
 * reason.
 *
 * Chips rather than tabs, because the shop does not use these symmetrically: a
 * sale happens fifty times a day, a load of stock arrives once a week.
 */
@Composable
fun BookScreen(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    /**
     * Renders the page this screen built and hands it to the chooser.
     *
     * The document comes from here rather than from the caller, because which of
     * the four pages it is depends on which chip is showing — and that is a fact
     * this screen owns.
     */
    onSaveSummary: (SummaryDocument, fileName: String) -> Unit,
    /**
     * Renders every customer's whole history as one document.
     *
     * A hundred statements is a hundred pages and a second or two of drawing, so
     * it is a deliberate tap rather than anything this screen does on its own.
     */
    onSaveLedgerBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = state.settings.currency

    /**
     * Which kind of record is showing. `rememberSaveable` so it survives a
     * rotation and, more usefully, a trip into a document and back — an owner who
     * came here for purchases should not be handed bills again on the way out.
     */
    var side by rememberSaveable { mutableStateOf(Side.SALES) }

    // **The span, asked once for all four.** This month by default: the book is
    // a year of rows before long, and the reason to open it is almost always
    // something written recently — so it opens on the span that answers that, and
    // the other three chips are there for the question it does not.
    //
    // Saved for the same reason the side is: a list that quietly reset to this
    // month every time a document was closed would make a stretch of days
    // impossible to read through.
    var choice by rememberSaveable { mutableStateOf(PeriodChoice.THIS_MONTH) }
    // Kept as epoch millis rather than `Instant`, which a `Bundle` cannot hold
    // without a saver of its own. The two picked days are the only state here
    // that is not already a plain value.
    var fromMillis by rememberSaveable { mutableStateOf(Timestamps.now().toEpochMilli()) }
    var toMillis by rememberSaveable { mutableStateOf(Timestamps.now().toEpochMilli()) }
    val from = Instant.ofEpochMilli(fromMillis)
    val to = Instant.ofEpochMilli(toMillis)
    val period = remember(choice, fromMillis, toMillis) { choice.period(from, to) }

    // Keyed on `state` as well as the span: these are plain functions over a
    // StateFlow snapshot, so a bill written while this is on screen moves the
    // list only if the read is keyed on what changed.
    //
    // Only the side on screen is read. Four lists and four totals computed on
    // every recomposition would be three quarters of a walk over the whole book
    // for figures nobody is looking at.
    val bills = remember(state, side, period) {
        if (side == Side.SALES) store.billsIn(period) else emptyList()
    }
    val purchases = remember(state, side, period) {
        if (side == Side.PURCHASES) store.purchasesIn(period) else emptyList()
    }
    val expenses = remember(state, side, period) {
        if (side == Side.EXPENSES) store.expensesIn(period) else emptyList()
    }
    // Both directions, merged and ordered in the store rather than here — see
    // `paymentBook`, which also settles what happens to two slips written in the
    // same second.
    val slips = remember(state, side, period) {
        if (side == Side.PAYMENTS) store.paymentBook(period) else emptyList()
    }
    val total = remember(state, side, period) {
        when (side) {
            Side.SALES -> store.soldIn(period)
            Side.PURCHASES -> store.boughtIn(period)
            Side.PAYMENTS -> store.receivedIn(period)
            Side.EXPENSES -> store.spentIn(period)
        }
    }
    // The payments card's second figure. Read only on that chip, for the reason
    // the lists are.
    val paidOut = remember(state, side, period) {
        if (side == Side.PAYMENTS) store.paidOutIn(period) else 0.0
    }

    // **What was typed, and whether anything was.** Not saved across a trip into
    // a document: the owner who searched a receipt, opened it and came back has
    // finished with that search, and being handed the results again is one more
    // thing to clear.
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()

    // Read only while something is typed, and deliberately **without the span**:
    // the lists above answer "what happened in August", and this answers "where
    // is this piece of paper" — a question a month only gets in the way of.
    val hits = remember(state, query) { if (searching) store.search(query) else emptyList() }

    val listState = rememberLazyListState()
    // Back to the top when the kind of record changes, and when a search starts or
    // ends. Without it, switching from forty bills to five expenses lands the
    // owner at the bottom of a list they have not read a line of — the scroll
    // offset is the old list's, and the new one is only long enough to be clamped
    // to its end.
    LaunchedEffect(side, searching) { listState.scrollToItem(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            title = strings.bookTitle,
            bottomPadding = 10.dp,
            trailing = {
                // Every customer's whole history, printed once and filed. The
                // one thing this screen hands to a printer, so it lives here
                // rather than in Settings, which is where features go to be
                // forgotten.
                IconButton(
                    Icon.bills,
                    onClick = onSaveLedgerBook,
                    contentDescription = strings.ledgerBook,
                    tint = Nocturne.neutral400
                )
            }
        )

        NocturneField(
            value = query,
            onValueChange = { query = it },
            placeholder = strings.searchRecords,
            height = 40.dp,
            fontSize = 13.5,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.screenPadding)
                .padding(bottom = 10.dp)
        )

        // The chips go while a search is running, and so does everything else the
        // span controls. Leaving them on screen would have the owner reading
        // "Sales · This month" over a list of results that is neither.
        if (!searching) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.screenPadding)
                .padding(bottom = 10.dp)
        ) {
            // No icons on this row. Four pills across a phone leaves each about
            // 77dp, and "Purchases" with a glyph beside it needs more than that —
            // the label is what the owner is reading anyway.
            for (candidate in Side.entries) {
                ChoicePill(
                    title = when (candidate) {
                        Side.SALES -> strings.salesSide
                        Side.PURCHASES -> strings.purchasesSide
                        Side.PAYMENTS -> strings.paymentsSide
                        Side.EXPENSES -> strings.expensesTitle
                    },
                    selected = side == candidate,
                    onClick = { side = candidate },
                    modifier = Modifier.weight(1f)
                )
                if (candidate != Side.entries.last()) Spacer(Modifier.width(6.dp))
            }
        }

        // Weighted, not wrapped. A `Column` measures an unweighted child against
        // the *full* remaining height, so the list would have been given the whole
        // screen and run off the bottom by exactly the height of the header and
        // chips above it.
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Metrics.screenPadding,
                end = Metrics.screenPadding,
                bottom = 18.dp
            )
        ) {
            if (searching) {
                if (hits.isEmpty()) {
                    item {
                        EmptyStateBox(icon = Icon.bills, message = strings.nothingMatches)
                    }
                }

                // Keyed on the kind as well as the id: a bill's id here is its
                // number, which is a small integer and could collide with nothing
                // else — but only because nothing else is keyed the same way, and
                // that is not a thing to leave to luck in a mixed list.
                items(hits, key = { "${it.kind}-${it.id}" }) { hit ->
                    SearchRow(
                        hit = hit,
                        currency = currency,
                        strings = strings,
                        onClick = { open(hit, store, router) },
                        modifier = Modifier.padding(bottom = Metrics.rowGap)
                    )
                }

                return@LazyColumn
            }

            item {
                // Span first, then the figure it adds up to, then the rows behind
                // the figure. The picker used to sit below the total on the
                // expenses side and above it on the other two, which meant the
                // same page read in two directions depending on a chip.
                PeriodPicker(
                    choice = choice,
                    from = from,
                    to = to,
                    strings = strings,
                    onChoose = { choice = it },
                    onFrom = { fromMillis = it.toEpochMilli() },
                    onTo = { toMillis = it.toEpochMilli() }
                )
                Spacer(Modifier.height(12.dp))

                TotalCard(
                    label = when (side) {
                        Side.SALES -> strings.soldInPeriod
                        Side.PURCHASES -> strings.boughtInPeriod
                        Side.PAYMENTS -> strings.receivedInPeriod
                        Side.EXPENSES -> strings.expenseInPeriod
                    },
                    value = Money.text(total, currency),
                    // Two of the four have something to say under the figure.
                    //
                    // Spending needs saying out loud: a shopkeeper writing down
                    // their petrol deserves to know at a glance that it will not
                    // turn up on a customer's statement. Payments carry the other
                    // direction, because what came in is the headline and what
                    // went out is the fact beside it — netting the two into one
                    // number would give the owner a figure they cannot check
                    // against anything they are holding.
                    note = when (side) {
                        Side.EXPENSES -> strings.expensesArePrivate
                        Side.PAYMENTS -> strings.alsoPaidOut(Money.text(paidOut, currency))
                        else -> null
                    },
                    // The span the total covers is the span the page covers, so
                    // the button that makes it lives in the total's own corner.
                    // All four make one now; a page saying nothing happened is a
                    // page nobody needs, so it appears only where something did.
                    onShare = if (total > 0) ({
                        onSaveSummary(summaryPage(side, store, period, state, strings), summaryFileName(side, strings))
                    }) else null,
                    shareLabel = strings.sharePdf
                )
                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Kicker(
                        when (side) {
                            Side.SALES -> strings.billsTitle
                            Side.PURCHASES -> strings.purchasesSide
                            Side.PAYMENTS -> strings.paymentsSide
                            Side.EXPENSES -> strings.expensesTitle
                        },
                        modifier = Modifier.weight(1f)
                    )
                    // Expenses are the one record with no other way in. A bill
                    // starts on the Sell tab and a purchase from the shelf, but
                    // nothing else in the app writes down the owner's own
                    // spending, so the list carries its own pen.
                    if (side == Side.EXPENSES) {
                        GhostButton(
                            strings.addAnExpense,
                            onClick = { router.openNewExpense() },
                            fontSize = 12.0
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Two different nothings, and they need different words. A shop that
            // has never written a bill wants the button; a shop that wrote none in
            // August wants to be told that rather than invited to start one,
            // because the records it is looking for are on another span.
            //
            // No `else` on any of these: a fourth kind of record has to break the
            // `when` and be placed deliberately, not fall through to whichever
            // branch was last.
            item {
                when (side) {
                    Side.SALES -> if (bills.isEmpty()) {
                        if (state.bills.isEmpty()) {
                            EmptyStateBox(
                                icon = Icon.bills,
                                message = strings.noBillsEver,
                                actionTitle = strings.startABill,
                                onAction = { router.startBill() }
                            )
                        } else {
                            EmptyStateBox(icon = Icon.bills, message = strings.nothingInThisPeriod)
                        }
                    }

                    Side.PURCHASES -> if (purchases.isEmpty()) {
                        if (state.purchases.isEmpty()) {
                            EmptyStateBox(
                                icon = Icon.addStock,
                                message = strings.noPurchasesRecorded,
                                actionTitle = strings.recordDelivery,
                                onAction = { router.recordingDelivery = true }
                            )
                        } else {
                            EmptyStateBox(icon = Icon.addStock, message = strings.nothingInThisPeriod)
                        }
                    }

                    // No button on this one. A payment is taken against somebody's
                    // account, so it starts from the person — there is nothing
                    // sensible for a button here to open without asking who first.
                    Side.PAYMENTS -> if (slips.isEmpty()) {
                        EmptyStateBox(
                            icon = Icon.owed,
                            message = if (state.payments.isEmpty() && state.supplierPayments.isEmpty()) {
                                strings.noPaymentsEver
                            } else {
                                strings.nothingInThisPeriod
                            }
                        )
                    }

                    Side.EXPENSES -> if (expenses.isEmpty()) {
                        if (state.expenses.isEmpty()) {
                            EmptyStateBox(
                                icon = Icon.expenses,
                                message = strings.noExpensesYet,
                                actionTitle = strings.addAnExpense,
                                onAction = { router.openNewExpense() }
                            )
                        } else {
                            EmptyStateBox(icon = Icon.expenses, message = strings.nothingInThisPeriod)
                        }
                    }
                }
            }

            // Nothing on these lists corrects anything. A record entered wrong is
            // opened first, and edited or removed from inside the document — which
            // is the only place the owner can see what they are about to change.
            when (side) {
                Side.SALES -> items(bills, key = { it.number }) { bill ->
                    BillRow(
                        bill = bill,
                        currency = currency,
                        strings = strings,
                        onClick = { router.openBill(bill) },
                        modifier = Modifier.padding(bottom = Metrics.rowGap)
                    )
                }

                Side.PURCHASES -> items(purchases, key = { it.id }) { purchase ->
                    PurchaseRow(
                        purchase = purchase,
                        supplierName = remember(state, purchase.supplierKey) {
                            store.supplier(purchase.supplierKey)?.name ?: purchase.supplierKey
                        },
                        currency = currency,
                        strings = strings,
                        onClick = { router.purchaseDetail = purchase },
                        modifier = Modifier.padding(bottom = Metrics.rowGap)
                    )
                }

                Side.PAYMENTS -> items(slips, key = { it.id }) { slip ->
                    PaymentRow(
                        entry = slip,
                        currency = currency,
                        strings = strings,
                        // The receipt the slip was written on, which the app
                        // already draws — the same page the owner was shown the
                        // moment they took the money. Null when the record has
                        // gone, and then nothing opens, which is the honest
                        // outcome the receipt lookup already settled on.
                        onClick = {
                            val receipt =
                                if (slip.incoming) store.receiptForPayment(slip.id)
                                else store.receiptForSupplierPayment(slip.id)
                            receipt?.let { router.showReceipt(it, justSaved = false) }
                        },
                        modifier = Modifier.padding(bottom = Metrics.rowGap)
                    )
                }

                Side.EXPENSES -> items(expenses, key = { it.id }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        currency = currency,
                        strings = strings,
                        onClick = { router.openExpense(expense) },
                        modifier = Modifier.padding(bottom = Metrics.rowGap)
                    )
                }
            }
        }
    }
}

/**
 * The page behind the share button, whichever chip is showing.
 *
 * Built from the same store calls the card's own figure came from, so the total
 * on the page and the total above the list are one number read twice rather than
 * two answers to one question.
 *
 * A `when` with no `else`, so a fifth chip has to be given a page rather than
 * quietly handing out the last one's.
 */
private fun summaryPage(
    side: Side,
    store: StockbookStore,
    period: StatementPeriod,
    state: ShopState,
    strings: Strings
): SummaryDocument = when (side) {
    Side.SALES -> SummaryDocument.forSales(
        store.salesByCustomerIn(period), period.range(), state.settings, strings
    )
    Side.PURCHASES -> SummaryDocument.forPurchases(
        store.purchasesBySupplierIn(period), period.range(), state.settings, strings
    )
    Side.PAYMENTS -> SummaryDocument.forPayments(
        store.receiptsByCustomerIn(period),
        store.paidOutIn(period),
        period.range(),
        state.settings,
        strings
    )
    Side.EXPENSES -> SummaryDocument.forSpending(
        store.spendingIn(period), period.range(), state.settings, strings
    )
}

/** Named for what is on it, and dated so two months' pages do not overwrite. */
private fun summaryFileName(side: Side, strings: Strings): String {
    val date = Dates.fileDate(Timestamps.now())
    return when (side) {
        Side.SALES -> strings.salesFileName(date)
        Side.PURCHASES -> strings.purchasesFileName(date)
        Side.PAYMENTS -> strings.paymentsFileName(date)
        Side.EXPENSES -> strings.expenseFileName(date)
    }
}

/**
 * Opens whatever a result is.
 *
 * The one place in the app that turns a [SearchHit] back into a record. Routing
 * is the app's business rather than the store's, so the handle comes across as an
 * id and is looked up here — a bill by its number, which is what a bill's
 * identity is, and everything else by its own.
 *
 * Nothing opens when the record has gone, which is the honest outcome the receipt
 * lookup already settled on. A `when` with no `else`, so a seventh kind of record
 * has to be given a way in rather than silently doing nothing when tapped.
 */
private fun open(hit: SearchHit, store: StockbookStore, router: AppRouter) {
    when (hit.kind) {
        DayEntryKind.BILL ->
            store.bills.firstOrNull { it.number.toString() == hit.id }?.let { router.openBill(it) }

        DayEntryKind.PAYMENT ->
            store.receiptForPayment(hit.id)?.let { router.showReceipt(it, justSaved = false) }

        DayEntryKind.SUPPLIER_PAYMENT ->
            store.receiptForSupplierPayment(hit.id)?.let { router.showReceipt(it, justSaved = false) }

        DayEntryKind.CREDIT_NOTE ->
            store.creditNotes.firstOrNull { it.id == hit.id }?.let { router.editingCreditNote = it }

        DayEntryKind.PURCHASE ->
            store.purchases.firstOrNull { it.id == hit.id }?.let { router.purchaseDetail = it }

        DayEntryKind.EXPENSE ->
            store.expenses.firstOrNull { it.id == hit.id }?.let { router.openExpense(it) }
    }
}

/**
 * What the span came to, whatever is being counted.
 *
 * One card for all four sides rather than one the expenses pane kept to itself.
 * Its chips went with it: the span is chosen above this now, so the figure and
 * the rows under it can no longer be showing two different months — which is
 * exactly what they were doing before the expenses list learned to narrow.
 */
@Composable
private fun TotalCard(
    label: String,
    value: String,
    /** A word of warning under the figure, where one is owed. */
    note: String?,
    /** Makes a page of the span on screen. Absent where there is no page to make. */
    onShare: (() -> Unit)?,
    shareLabel: String
) {
    Box(modifier = Modifier.fillMaxWidth().card().hairline(radius = Metrics.cardRadius)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, style = NocturneType.inter(11.0), color = Nocturne.neutral500)
            Text(
                value,
                style = NocturneType.fittedNumber(value),
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (note != null) {
                Text(
                    note,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Drawn over the card rather than in the column, because a 44dp touch
        // target on the label's own row would push the figure a third of the card
        // down to make room for it.
        if (onShare != null) {
            IconButton(
                Icon.share,
                onClick = onShare,
                size = 15.dp,
                tint = Nocturne.accent,
                contentDescription = shareLabel,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp, top = 2.dp)
            )
        }
    }
}

/**
 * Which kind of record is showing.
 *
 * Not `PeopleSide`, which `PeopleScreen` has in this package — a top-level
 * `private` in Kotlin hides the declaration from other *files* but still puts the
 * name in the package, so two of them collide.
 */
private enum class Side { SALES, PURCHASES, PAYMENTS, EXPENSES }
