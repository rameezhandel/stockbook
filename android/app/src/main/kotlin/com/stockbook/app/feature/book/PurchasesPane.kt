package com.stockbook.app.feature.book

import androidx.compose.foundation.clickable
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
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
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
            SupplierSection(
                // Passed rather than read off the store inside: `suppliers()` is a
                // plain function over a StateFlow's current value, so it
                // subscribes to nothing. Taking the state as a parameter is what
                // makes this recompose when a delivery is recorded.
                state = state,
                store = store,
                router = router,
                currency = currency,
                strings = strings,
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

/** One delivery. Tapping it opens the document, which is where it can be changed. */
@Composable
private fun DeliveryRow(
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
                // The product where one arrived, and otherwise what the shop calls
                // the piece of paper: a supplier's bill for a mixed load names
                // nothing, and a row headed by an empty string reads as a delivery
                // whose product was lost rather than one that never had a product.
                purchase.name ?: purchase.reference(strings),
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // `12 × SAR 60` is the arithmetic behind a delivery, and there is
                // none behind a bill entered as a figure. Who it was from is the
                // whole of what the second line has to say then.
                if (purchase.isItemised) {
                    "$supplierName · ${strings.perPiece(purchase.qty, Money.text(purchase.unitCost, currency))}"
                } else {
                    supplierName
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

/**
 * The supplier panel: a mirror of the customer panel on the sales side. Pick one,
 * see what the shop has bought from them and what it still owes, and go on to a
 * statement or a payment.
 */
@Composable
private fun SupplierSection(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    currency: Currency,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    val suppliers = remember(state) { store.suppliers() }
    var chosen by remember { mutableStateOf<String?>(null) }
    val selected = chosen?.let { key -> suppliers.firstOrNull { it.key == key } }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Kicker(strings.suppliersTitle, modifier = Modifier.weight(1f))
            GhostButton(strings.addASupplier, onClick = { router.openNewSupplier() }, fontSize = 12.0)
        }
        Spacer(Modifier.height(8.dp))

        if (suppliers.isEmpty()) {
            // Not an empty state with a call to action: a shop can run for weeks
            // before anybody records a delivery, and the way most suppliers get
            // added is the picker inside the purchase sheet.
            Text(
                strings.noPurchasesYet,
                style = NocturneType.meta,
                color = Nocturne.neutral500
            )
            return@Column
        }

        // Null is "all suppliers", the same way the Bills filter spells it.
        val options = remember(suppliers) { listOf<Supplier?>(null) + suppliers }
        DropdownField(
            options = options,
            selected = selected,
            onSelect = { chosen = it?.key },
            title = { it?.name ?: strings.allSuppliers }
        )

        if (selected == null) {
            Spacer(Modifier.height(8.dp))
            val (names, total) = remember(state) { store.payable() }
            Text(
                if (names.isEmpty()) strings.nothingOwedOut
                else "${strings.owedToSuppliers}: ${Money.text(total, currency)}",
                style = NocturneType.meta,
                color = if (names.isEmpty()) Nocturne.neutral500 else Nocturne.accent400
            )
            return@Column
        }

        Spacer(Modifier.height(10.dp))
        Column(modifier = Modifier.fillMaxWidth().card().hairline(Nocturne.neutral800, Metrics.cardRadius).padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Glyph(Icon.customer, size = 16.dp, tint = Nocturne.accent)
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(selected.name, style = NocturneType.rowPrimary, color = Nocturne.text, maxLines = 1)
                    listOfNotNull(selected.phone, selected.place).takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it.joinToString(" · "),
                            style = NocturneType.meta,
                            color = Nocturne.neutral500,
                            maxLines = 1
                        )
                    }
                }
                IconButton(
                    Icon.edit,
                    onClick = { router.openSupplier(selected) },
                    size = 13.dp,
                    tint = Nocturne.neutral500,
                    contentDescription = strings.editSupplier
                )
            }
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Figure(
                    label = strings.boughtFromThem,
                    value = Money.text(selected.total, currency),
                    detail = strings.purchases(selected.purchaseCount),
                    modifier = Modifier.weight(1f)
                )
                Figure(
                    label = strings.youOwe,
                    value = when {
                        selected.owed > 0 -> Money.text(selected.owed, currency)
                        selected.owed < 0 -> strings.inAdvance(Money.text(-selected.owed, currency))
                        else -> strings.nothingPending
                    },
                    detail = null,
                    tint = if (selected.owed > 0) Nocturne.accent400 else Nocturne.neutral500,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(11.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    strings.statement,
                    onClick = { router.openSupplierStatement(selected) },
                    fullWidth = true,
                    height = 38.dp,
                    fontSize = 12.5,
                    modifier = Modifier.weight(1f)
                )
                // Offered only when there is something to settle. Paying a supplier
                // who is owed nothing is an advance — real, but not what this
                // button is for.
                if (selected.owed > 0) {
                    Spacer(Modifier.width(6.dp))
                    PrimaryButton(
                        strings.recordAPayment,
                        onClick = { router.supplierPaymentFor = selected },
                        fullWidth = true,
                        height = 38.dp,
                        fontSize = 12.5,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
        Text(value, style = NocturneType.inter(15.0), color = tint, maxLines = 1)
        detail?.let { Text(it, style = NocturneType.meta, color = Nocturne.neutral500) }
    }
}
