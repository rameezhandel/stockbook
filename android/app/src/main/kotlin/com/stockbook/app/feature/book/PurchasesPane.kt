package com.stockbook.app.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.EmptyStateBox
import com.stockbook.app.design.card
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Purchase
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.Supplier
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * What arrived, and from whom.
 *
 * The sales half's mirror: the supplier panel on top — pick one and see what is
 * owed to them — and every delivery underneath, newest first. A wrong delivery is
 * opened first and then corrected or taken out, exactly as a wrong bill is.
 */
@Composable
fun PurchasesPane(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    val currency = state.settings.currency

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Metrics.screenPadding,
            end = Metrics.screenPadding,
            bottom = 18.dp
        )
    ) {
        item {
            // `suppliers()` is a plain function over a StateFlow's current value,
            // so read bare in a composable it subscribes to nothing. Keying the
            // read on `state` is what makes this recompose when a delivery is
            // recorded or a payment made.
            val suppliers = remember(state) { store.suppliers().map { it.row() } }
            PartyList(
                title = strings.suppliersTitle,
                rows = suppliers,
                search = { query -> store.suppliers(matching = query).map { it.row() } },
                addTitle = strings.addASupplier,
                emptyMessage = strings.noSuppliersYet,
                currency = currency,
                strings = strings,
                onAdd = { router.openNewSupplier() },
                onOpen = { key -> router.openSupplierScreen(key) },
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }

        item {
            Kicker(strings.purchasesSide, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (state.purchases.isEmpty()) {
            item {
                EmptyStateBox(
                    icon = Icon.addStock,
                    message = strings.noDeliveriesYet,
                    actionTitle = strings.recordDelivery,
                    onAction = { router.recordingDelivery = true }
                )
            }
        }

        items(state.purchases, key = { it.id }) { purchase ->
            DeliveryRow(
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
    }
}

/**
 * One delivery. Tapping it opens the document, which is where it can be changed.
 *
 * Not private: `PartyScreen` draws the same row under one supplier, and a second
 * copy of it would be two rows to keep in step.
 */
@Composable
internal fun DeliveryRow(
    purchase: Purchase,
    supplierName: String,
    currency: Currency,
    strings: Strings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .card(Metrics.controlRadius)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // What arrived, and otherwise what the shop calls the piece of
                // paper: a supplier's bill for a mixed load names nothing, and a
                // row headed by an empty string reads as a delivery whose product
                // was lost rather than one that never had a product.
                purchase.summary.ifBlank { purchase.reference(strings) },
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // `12 × SAR 60` is the arithmetic behind a delivery of one thing,
                // and there is none to show behind a bill entered as a figure —
                // who it was from is the whole of what the second line has to say
                // then. Several lines say how many rather than repeating the
                // arithmetic of each: the sheet has room for that, a row does not.
                buildString {
                    append(supplierName)
                    val lines = purchase.items
                    when (lines.size) {
                        0 -> Unit
                        1 -> append(
                            " · ${strings.perPiece(lines.single().qty, Money.text(lines.single().unitCost, currency))}"
                        )
                        else -> append(" · ${strings.items(lines.size)}")
                    }
                },
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.text(purchase.total, currency),
                style = NocturneType.inter(14.0),
                color = Nocturne.text
            )
            Text(
                if (purchase.balance > 0) strings.owes(Money.text(purchase.balance, currency))
                else strings.longDate(purchase.createdAt),
                style = NocturneType.meta,
                color = if (purchase.balance > 0) Nocturne.accent400 else Nocturne.neutral500
            )
        }
    }
}

/** `Supplier` as the directory draws it. */
private fun Supplier.row() = PartyRow(
    key = key,
    name = name,
    contact = listOfNotNull(phone, place).takeIf { it.isNotEmpty() }?.joinToString(" · "),
    owed = owed
)
