package com.stockbook.app.feature.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import com.stockbook.app.design.ChoicePill
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.card
import com.stockbook.core.model.Supplier
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.hairline
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Product
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.store.RestockMode
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * Putting stock back on the shelf, two ways.
 *
 * **Quick add** is the common case — you tipped a bag into the bin and the count
 * is now higher. **Purchase entry** is a supplier delivery, and it is the only
 * path that changes the buying price: cost here is "latest paid", not a weighted
 * average, so the new figure simply takes over.
 */
@Composable
fun AddStockSheet(
    product: Product,
    /** Passed so the supplier list below recomposes when one is added. */
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    onClose: () -> Unit
) {
    var purchase by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    /** What was typed into the supplier box, and who was actually chosen. */
    var supplier by remember { mutableStateOf("") }
    var supplierKey by remember { mutableStateOf<String?>(null) }
    var settledNow by remember { mutableStateOf(true) }
    var paidText by remember { mutableStateOf("") }

    val quantityValue = quantity.trim().toIntOrNull() ?: (Money.parse(quantity) ?: 0.0).toInt()
    val costValue = Money.parse(unitCost) ?: 0.0
    val totalValue = maxOf(0, quantityValue) * costValue

    // A purchase is a record against an account, so it needs the account. Quick
    // add is not: it is a correction to a number on a shelf, and demanding a
    // supplier for it would be asking who delivered the bag you just tipped in.
    val canSave = if (purchase) quantityValue > 0 && supplierKey != null else quantityValue > 0

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = strings.addStock,
            subtitle = strings.onShelfNow(product.name, product.stock),
            onClose = onClose
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            ModePill(strings.quickAdd, active = !purchase, onClick = { purchase = false }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            ModePill(strings.purchaseEntry, active = purchase, onClick = { purchase = true }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Metrics.cardGap))

        if (purchase) {
            SupplierPicker(
                typed = supplier,
                chosenKey = supplierKey,
                state = state,
                store = store,
                currency = currency,
                strings = strings,
                onType = { supplier = it; supplierKey = null },
                onChoose = { supplier = it.name; supplierKey = it.key }
            )
            Spacer(Modifier.height(Metrics.cardGap))
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            NocturneField(
                value = quantity,
                onValueChange = { quantity = it },
                label = strings.howMany,
                numeric = true,
                modifier = Modifier.weight(1f)
            )
            if (purchase) {
                Spacer(Modifier.width(8.dp))
                NocturneField(
                    value = unitCost,
                    onValueChange = { unitCost = it },
                    label = strings.paidPerPiece,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Was it paid for at the door? Usually yes — the driver waits — so that is
        // the default, and the alternative is one tap away rather than a number
        // the owner has to type to mean "nothing owed".
        if (purchase) {
            Spacer(Modifier.height(Metrics.cardGap))
            Row(modifier = Modifier.fillMaxWidth()) {
                ChoicePill(
                    title = strings.paidInFull,
                    icon = Icon.confirm,
                    selected = settledNow,
                    onClick = { settledNow = true },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                ChoicePill(
                    title = strings.partPayment,
                    icon = Icon.edit,
                    selected = !settledNow,
                    onClick = { settledNow = false },
                    modifier = Modifier.weight(1f)
                )
            }
            if (!settledNow) {
                Spacer(Modifier.height(Metrics.cardGap))
                NocturneField(
                    value = paidText,
                    onValueChange = { paidText = it },
                    label = strings.paidNow,
                    numeric = true,
                    prefix = currency.symbol.trim()
                )
            }
        }

        // The note is the only place the two modes explain themselves, so it
        // states the consequence rather than restating the mode.
        Text(
            if (purchase) {
                strings.purchaseNote(Money.text(totalValue, currency))
            } else {
                strings.quickAddNote(Money.text(product.cost, currency))
            },
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(top = 10.dp)
        )

        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            title = when {
                !purchase -> strings.addToStock(maxOf(0, quantityValue))
                supplierKey == null && supplier.isNotBlank() -> strings.chooseSupplierFromTheList
                supplierKey == null -> strings.whoDeliveredIt
                else -> strings.recordPurchase
            },
            onClick = {
                if (purchase) {
                    val key = supplierKey ?: return@PrimaryButton
                    // A delivery is a record with an account behind it, not a
                    // number added to a shelf — which is why this is no longer
                    // `restock` with a supplier string that went nowhere.
                    store.recordPurchase(
                        product = product,
                        supplierKey = key,
                        quantity = quantityValue,
                        unitCost = costValue,
                        paid = if (settledNow) null else (Money.parse(paidText) ?: 0.0)
                    )
                } else {
                    // Zero or empty quantity just closes the sheet — the owner
                    // opened it, then thought better of it, and that should not
                    // need a Cancel button.
                    store.restock(product, quantity = quantityValue, mode = RestockMode.QUICK_ADD)
                }
                onClose()
            },
            enabled = canSave,
            fullWidth = true
        )
    }
}

@Composable
private fun ModePill(
    title: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(Metrics.compactControlHeight)
            .clip(RoundedCornerShape(Metrics.controlRadius))
            .background(Nocturne.bg)
            .hairline(if (active) Nocturne.accent else Nocturne.neutral800, Metrics.controlRadius)
            .clickable(onClick = onClick)
    ) {
        Text(
            title,
            style = NocturneType.inter(13.0, FontWeight.Medium),
            color = if (active) Nocturne.accent else Nocturne.neutral500
        )
    }
}

/**
 * Who delivered it: type to filter the roster, then **choose**.
 *
 * The cart's customer picker, on the other side of the counter and for the same
 * reason — a typed name is not an account, and a delivery filed against one that
 * does not exist is money the shop cannot see it owes. It cannot block the work
 * either: a name matching nobody offers to become a supplier in the same tap.
 *
 * The list is drawn **below** the field here, unlike the cart's. This sheet grows
 * downwards from the top of a bottom sheet with room under it; the cart's field
 * sits on the bottom edge of the screen with the keyboard beneath it.
 */
@Composable
private fun SupplierPicker(
    typed: String,
    chosenKey: String?,
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    onType: (String) -> Unit,
    onChoose: (Supplier) -> Unit
) {
    val query = Supplier.key(typed)
    // Keyed on the state, not read off the store bare: `suppliers()` is a plain
    // function over a StateFlow's current value and subscribes to nothing.
    val everyone = remember(state) { store.suppliers() }
    val matches = if (chosenKey != null) emptyList() else everyone.filter { query.isEmpty() || it.key.contains(query) }
    val canCreate = chosenKey == null && query.isNotEmpty() && everyone.none { it.key == query }

    Column(modifier = Modifier.fillMaxWidth()) {
        NocturneField(
            value = typed,
            onValueChange = onType,
            label = strings.supplier,
            placeholder = strings.whoDeliveredIt,
            // Marked until somebody is actually chosen, not merely until the box
            // has characters in it.
            isRequiredAndEmpty = chosenKey == null
        )

        if (matches.isNotEmpty() || canCreate) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .card(Metrics.controlRadius)
                    .hairline(Nocturne.accent, Metrics.controlRadius)
                    .padding(vertical = 3.dp)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = SUPPLIER_LIST_MAX_HEIGHT)) {
                    items(matches, key = { it.key }) { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChoose(candidate) }
                                .padding(horizontal = 11.dp, vertical = 9.dp)
                        ) {
                            Glyph(Icon.customer, size = 12.dp, tint = Nocturne.neutral500)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                candidate.name,
                                style = NocturneType.inter(13.5),
                                color = Nocturne.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                candidate.meta(currency, strings),
                                style = NocturneType.meta,
                                color = if (candidate.owed > 0) Nocturne.accent400 else Nocturne.neutral500
                            )
                        }
                    }
                }

                // Outside the scrolling part: it is the way out when nobody
                // matches, and must never be something to scroll for.
                if (canCreate) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                store.addSupplier(typed)?.let { record ->
                                    store.supplier(record.key)?.let { onChoose(it) }
                                }
                            }
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    ) {
                        Glyph(Icon.add, size = 12.dp, tint = Nocturne.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            strings.addAsSupplier(typed),
                            style = NocturneType.inter(13.5),
                            color = Nocturne.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** Four rows and a sliver of the fifth, the same as the cart's list. */
private val SUPPLIER_LIST_MAX_HEIGHT = 150.dp
