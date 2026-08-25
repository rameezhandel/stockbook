package com.stockbook.app.feature.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.EmptyStateBox
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.app.feature.bills.BillRow
import com.stockbook.core.model.Currency
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * One customer or one supplier: what they are worth, what is outstanding, the
 * things that can be done about it, and every document between them and the shop.
 *
 * This is where the party card that used to hide behind a dropdown at the top of
 * the Book now lives. The card itself is barely changed — what changed is that a
 * customer is a place you can go rather than an option you have to select. Before
 * this, editing somebody's phone number meant Book, chip, dropdown, pick, pencil;
 * and the only route from Today's "Ahmed still owes" banner to Ahmed was a sheet
 * built specially to work around the fact that Ahmed had no screen.
 *
 * One screen for both sides of the book, exactly as [com.stockbook.app.feature.customers.StatementScreen]
 * is one screen: the domain treats a customer and a supplier as the same shape
 * pointed in opposite directions, and two screens here would drift apart the
 * first time either was corrected.
 */
@Composable
fun PartyScreen(
    /** Whose account: a customer key, or a supplier key with [isSupplier] set. */
    partyKey: String,
    isSupplier: Boolean,
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    onClose: () -> Unit
) {
    val currency = state.settings.currency

    // Re-read from the store on every shop change rather than carried in: taking a
    // payment from this screen has to move the figure above it, and a copy handed
    // over when the screen opened would sit there saying what was owed a minute
    // ago.
    val customer = remember(state, partyKey) { if (isSupplier) null else store.customer(partyKey) }
    val supplier = remember(state, partyKey) { if (isSupplier) store.supplier(partyKey) else null }

    val name = customer?.name ?: supplier?.name ?: partyKey
    val contact = listOfNotNull(
        customer?.phone ?: supplier?.phone,
        customer?.place ?: supplier?.place
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    // Only ever asked whether there is more than one, for the merge button: an
    // account with nobody to be joined to should not offer to join it.
    val others = remember(state, partyKey) {
        (if (isSupplier) store.suppliers() else store.customers()).count { it.key != partyKey }
    }

    val bills = remember(state, partyKey) {
        if (isSupplier) emptyList() else store.billsForCustomer(partyKey)
    }
    val purchases = remember(state, partyKey) {
        if (isSupplier) store.purchasesForSupplier(partyKey) else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nocturne.bg)
            .statusBarsPadding()
    ) {
        ScreenHeader(title = name, subtitle = contact) {
            GhostButton(strings.done, onClick = onClose, fontSize = 12.5)
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = Metrics.screenPadding,
                end = Metrics.screenPadding,
                bottom = 24.dp
            )
        ) {
            item {
                AccountCard(
                    bought = Money.text(customer?.total ?: supplier?.total ?: 0.0, currency),
                    boughtLabel = if (isSupplier) strings.boughtFromThem else strings.transactions,
                    boughtDetail = if (isSupplier) {
                        strings.purchases(supplier?.purchaseCount ?: 0)
                    } else {
                        strings.bills(customer?.billCount ?: 0)
                    },
                    owedLabel = if (isSupplier) strings.youOwe else strings.pendingPayment,
                    owed = customer?.owed ?: supplier?.owed ?: 0.0,
                    currency = currency,
                    strings = strings,
                    onEdit = {
                        if (isSupplier) supplier?.let { router.openSupplier(it) }
                        else customer?.let { router.openCustomer(it) }
                    },
                    onStatement = {
                        if (isSupplier) supplier?.let { router.openSupplierStatement(it) }
                        else customer?.let { router.openStatement(it) }
                    },
                    onRecordPayment = {
                        if (isSupplier) router.supplierPaymentFor = supplier
                        else router.paymentFor = customer
                    },
                    // Null on the supplier side: the shop does not write itself a
                    // credit note, and the card leaves the button out rather than
                    // drawing one that does nothing.
                    onCreditNote = customer?.takeIf { !isSupplier }?.let { who ->
                        {
                            router.editingCreditNote = null
                            router.creditNoteFor = who
                        }
                    },
                    // Offered only where there is somebody to merge *with*. On a
                    // one-customer shop the button would open a sheet that could
                    // only say "there is nobody else".
                    onMerge = {
                        router.startMerge(partyKey, isSupplier)
                    }.takeIf { others > 0 },
                    mergeTitle = if (isSupplier) {
                        strings.mergeIntoAnotherSupplier
                    } else {
                        strings.mergeIntoAnotherCustomer
                    },
                    editLabel = if (isSupplier) strings.editSupplier else strings.editCustomer
                )
                Spacer(Modifier.height(20.dp))
                Kicker(if (isSupplier) strings.purchasesSide else strings.billsTitle)
                Spacer(Modifier.height(8.dp))
            }

            if (bills.isEmpty() && purchases.isEmpty()) {
                item {
                    EmptyStateBox(
                        icon = if (isSupplier) Icon.addStock else Icon.bills,
                        message = if (isSupplier) strings.noDeliveriesYet else strings.noBillsEver,
                        actionTitle = if (isSupplier) strings.recordDelivery else strings.startABill,
                        onAction = {
                            onClose()
                            if (isSupplier) router.recordingDelivery = true else router.startBill()
                        }
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

            items(purchases, key = { it.id }) { purchase ->
                DeliveryRow(
                    purchase = purchase,
                    supplierName = name,
                    currency = currency,
                    strings = strings,
                    onClick = { router.purchaseDetail = purchase },
                    modifier = Modifier.padding(bottom = Metrics.rowGap)
                )
            }
        }
    }
}

/**
 * The two figures and the three things that can be done about them.
 *
 * `onCreditNote` is null on the supplier side — the shop does not write itself a
 * credit note — and the button is simply absent rather than present and dead.
 */
@Composable
private fun AccountCard(
    bought: String,
    boughtLabel: String,
    boughtDetail: String,
    owedLabel: String,
    owed: Double,
    currency: Currency,
    strings: Strings,
    onEdit: () -> Unit,
    onStatement: () -> Unit,
    onRecordPayment: () -> Unit,
    onCreditNote: (() -> Unit)?,
    /**
     * Joining this account into another. Null where there is nobody else on this
     * side of the book to join it to.
     */
    onMerge: (() -> Unit)?,
    mergeTitle: String,
    editLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .hairline(Nocturne.neutral800, Metrics.cardRadius)
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Figure(
                    label = boughtLabel,
                    value = bought,
                    detail = boughtDetail
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Figure(
                    label = owedLabel,
                    value = when {
                        owed > 0 -> Money.text(owed, currency)
                        owed < 0 -> strings.inAdvance(Money.text(-owed, currency))
                        else -> strings.nothingPending
                    },
                    detail = null,
                    tint = if (owed > 0) Nocturne.accent400 else Nocturne.neutral500
                )
            }
            // Editing is where a phone number gets added to somebody who has only
            // ever been a name on a bill.
            IconButton(
                Icon.edit,
                onClick = onEdit,
                size = 13.dp,
                tint = Nocturne.neutral500,
                contentDescription = editLabel
            )
        }

        // The statement across the whole width, and the things that write to the
        // account beneath it. The statement is the one that only *reads*.
        SecondaryButton(
            strings.statement,
            onClick = onStatement,
            fullWidth = true,
            height = 38.dp,
            fontSize = 12.5,
            modifier = Modifier.fillMaxWidth().padding(top = 11.dp)
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            // Always offered, including to somebody who is owed nothing. Money
            // goes over a counter in instalments and sometimes ahead of the bill,
            // and the payment sheet has always said "SAR 200 in advance" when it
            // does. This was gated on `owed > 0` on both sides of the book, which
            // meant settling up in full took the button away.
            PrimaryButton(
                strings.recordAPayment,
                onClick = onRecordPayment,
                fullWidth = true,
                height = 38.dp,
                fontSize = 12.5,
                modifier = Modifier.weight(1f)
            )
            if (onCreditNote != null) {
                Spacer(Modifier.width(6.dp))
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

        // Last and quietest. Merging is rare, it rewrites history, and it is not
        // something anybody should reach for while a customer waits — but it
        // belongs on the account it would remove, which is where the owner is
        // standing when they notice the duplicate.
        if (onMerge != null) {
            GhostButton(
                mergeTitle,
                onClick = onMerge,
                fontSize = 12.0,
                tint = Nocturne.neutral500,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun Figure(
    label: String,
    value: String,
    detail: String?,
    tint: Color = Nocturne.text
) {
    Column {
        Text(label, style = NocturneType.inter(11.0), color = Nocturne.neutral500)
        Text(
            value,
            style = NocturneType.inter(17.0),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        detail?.let { Text(it, style = NocturneType.meta, color = Nocturne.neutral500) }
    }
}
