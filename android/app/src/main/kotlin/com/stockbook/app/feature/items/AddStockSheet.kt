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
import com.stockbook.app.design.FieldEmphasis
import com.stockbook.app.design.Glyph
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import com.stockbook.app.design.GhostButton
import com.stockbook.core.model.Timestamps
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * The two things that move a shelf count by hand, in one sheet.
 *
 * **Set count** is the owner looking at the shelf and telling the app what is
 * actually on it. It has to exist because most bills here are entered as a total,
 * and a total moves no stock: the count is a running tally that drifts, and this
 * is how it gets told the truth.
 *
 * **A supplier bill** is the mirror of a sale — somebody, a number on a piece of
 * paper, a date and a figure — and saying which product arrived is optional in
 * exactly the way saying what was sold is. Name a product *and* a quantity and
 * the stock arrives and the buying price takes over; name neither and the bill is
 * a figure against the supplier's account, which is what a mixed load or a
 * delivery charge actually is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStockSheet(
    /**
     * The product this was opened from, or null when it was opened from the
     * Delivery button with nothing named yet. Null is also what says which of the
     * two halves this sheet can show: there is no shelf to count without one.
     */
    product: Product?,
    /** Passed so the supplier and product lists below recompose when one is added. */
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    onClose: () -> Unit
) {
    var supplierBill by remember { mutableStateOf(product == null) }
    var count by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    /** What was typed into the supplier box, and who was actually chosen. */
    var supplier by remember { mutableStateOf("") }
    var supplierKey by remember { mutableStateOf<String?>(null) }
    /** What was typed into the product box, and which product was actually chosen. */
    var productText by remember { mutableStateOf(product?.name.orEmpty()) }
    var chosenProduct by remember { mutableStateOf(product) }
    var amount by remember { mutableStateOf("") }
    var settledNow by remember { mutableStateOf(true) }
    var paidText by remember { mutableStateOf("") }
    /** The number on the supplier's invoice, and the day it arrived. */
    var invoiceNo by remember { mutableStateOf("") }
    var arrivedAt by remember { mutableStateOf(Timestamps.now()) }
    var pickingDate by remember { mutableStateOf(false) }

    val countValue = count.trim().toIntOrNull()
    val quantityValue = quantity.trim().toIntOrNull() ?: 0
    // What the delivery is actually costed at, which is what `recordPurchase`
    // will use: the figure typed here where there is one, and otherwise the price
    // already on the product. Worked out the same way on both sides of the call,
    // or the sheet shows a total the store does not save.
    val costValue = Money.parse(unitCost)?.takeIf { it > 0 } ?: chosenProduct?.cost ?: 0.0
    // Itemised means a product *and* a count of it. A product with no quantity is
    // half an answer, and guessing the other half would put stock on the shelf
    // nobody said arrived.
    val itemised = chosenProduct != null && quantityValue > 0
    val totalValue = if (itemised) quantityValue * costValue else Money.parse(amount) ?: 0.0

    // The delivery already filed under this number, whoever it came from. Across
    // the whole book rather than per supplier: one number, one piece of paper.
    val clash = remember(state, invoiceNo) { store.purchaseWithInvoiceNo(invoiceNo) }

    // A supplier bill is a record against an account and against a piece of paper,
    // so it needs the supplier, the number and a figure. Counting a shelf is none
    // of those: it is one number, and demanding a supplier would be asking who
    // delivered the stock you have just finished counting.
    val canSave = if (supplierBill) {
        supplierKey != null && invoiceNo.isNotBlank() && totalValue > 0 && clash == null
    } else {
        countValue != null
    }

    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = arrivedAt.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                GhostButton(strings.done, onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        arrivedAt = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .atTime(12, 0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                    }
                    pickingDate = false
                })
            }
        ) {
            DatePicker(state = picker)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            // The title says which of the two this is rather than what opened it.
            // "Add stock" over a box asking what you counted is the exact sentence
            // that would make somebody type the number they are adding.
            title = if (supplierBill) strings.supplierBillTitle else strings.setCount,
            subtitle = product?.let { strings.onShelfNow(it.name, it.stock) },
            onClose = onClose
        )

        // Only where there is a shelf to count. Opened from the Delivery button
        // there is no product yet, so there is nothing to offer a count of and the
        // sheet is a supplier bill and says so.
        if (product != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                ModePill(
                    strings.setCount,
                    active = !supplierBill,
                    onClick = { supplierBill = false },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                ModePill(
                    strings.supplierBillTitle,
                    active = supplierBill,
                    onClick = { supplierBill = true },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Metrics.cardGap))
        }

        if (!supplierBill && product != null) {
            NocturneField(
                value = count,
                onValueChange = { count = it },
                label = strings.inStock,
                numeric = true,
                isRequiredAndEmpty = countValue == null
            )
            // The whole of the difference between this and what it replaced, said
            // where the mistake would be made: "add 5" and "there are 5" are the
            // same keystrokes and different shelves.
            Text(
                strings.setCountNote,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                title = strings.setCount,
                onClick = {
                    // A count is *set*, never added to. Nothing here reaches
                    // `restock`, which is the function that would quietly turn
                    // "there are twelve" into twelve more.
                    countValue?.let { store.setStock(product, it) }
                    onClose()
                },
                enabled = canSave,
                fullWidth = true
            )
            return@Column
        }

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

        // The paper that came with the stock, and the day it came. The number is
        // required here as it is on a bill: a delivery filed under no number
        // cannot be matched to the invoice in the drawer, which is the one thing
        // the supplier will quote on the phone.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            NocturneField(
                value = invoiceNo,
                onValueChange = { invoiceNo = it },
                label = strings.invoiceNoField,
                placeholder = strings.invoiceNoHint,
                isRequiredAndEmpty = invoiceNo.isBlank(),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.billDate, style = NocturneType.fieldLabel, color = Nocturne.neutral500)
                Spacer(Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Metrics.inputHeight)
                        .clip(RoundedCornerShape(Metrics.controlRadius))
                        .hairline(Nocturne.neutral800, Metrics.controlRadius)
                        .clickable { pickingDate = true }
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        strings.longDate(arrivedAt),
                        style = NocturneType.inter(13.0),
                        color = Nocturne.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (clash != null) {
            Text(
                strings.invoiceNoAlreadyUsed(
                    store.supplier(clash.supplierKey)?.name ?: clash.supplierKey,
                    strings.longDate(clash.createdAt)
                ),
                style = NocturneType.meta,
                color = Nocturne.accent400,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(Metrics.cardGap))

        // What the bill came to. Typed until a product and a quantity say what it
        // is made of, computed from then on — never both at once, or the sheet is
        // showing one figure and about to save another.
        if (itemised) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .card(Metrics.controlRadius)
                    .hairline(Nocturne.accent700, Metrics.controlRadius)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        strings.total,
                        style = NocturneType.inter(13.0),
                        color = Nocturne.neutral500,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        Money.text(totalValue, currency),
                        style = NocturneType.bigNumber(24.0),
                        color = Nocturne.text
                    )
                }
                Text(
                    strings.perPiece(quantityValue, Money.text(costValue, currency)),
                    style = NocturneType.meta,
                    color = Nocturne.neutral500
                )
            }
        } else {
            NocturneField(
                value = amount,
                onValueChange = { amount = it },
                label = strings.amountField,
                height = Metrics.tallInputHeight,
                numeric = true,
                isRequiredAndEmpty = totalValue <= 0,
                emphasis = FieldEmphasis.SELLING_PRICE,
                prefix = currency.symbol.trim(),
                fontSize = 17.0
            )
        }
        Spacer(Modifier.height(Metrics.cardGap))

        // Which product arrived, where the shop keeps a count of it. Optional, and
        // labelled so: a bill for a mixed load, or for something that never sits
        // on a shelf, names nothing and still owes money.
        if (product == null) {
            ProductPicker(
                typed = productText,
                chosen = chosenProduct,
                state = state,
                currency = currency,
                strings = strings,
                onType = { productText = it; chosenProduct = null },
                onChoose = { productText = it.name; chosenProduct = it }
            )
            Spacer(Modifier.height(Metrics.cardGap))
        }

        if (chosenProduct != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                NocturneField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = strings.howMany,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                NocturneField(
                    value = unitCost,
                    onValueChange = { unitCost = it },
                    label = strings.paidPerPiece,
                    numeric = true,
                    // Marked only once it is the thing standing between the owner
                    // and a saved bill: with a quantity typed and no price on
                    // either the box or the product, there is no figure at all.
                    isRequiredAndEmpty = itemised && totalValue <= 0,
                    prefix = currency.symbol.trim(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Metrics.cardGap))
        }

        // Was it paid for at the door? Usually yes — the driver waits — so that is
        // the default, and the alternative is one tap away rather than a number
        // the owner has to type to mean "nothing owed".
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

        // Said only where it is true: the buying price takes over when stock
        // actually arrived, and a bill naming no product changes no price.
        if (itemised) {
            Text(
                strings.purchaseNote(Money.text(totalValue, currency)),
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            title = when {
                clash != null -> strings.changeTheInvoiceNo
                supplierKey == null && supplier.isNotBlank() -> strings.chooseSupplierFromTheList
                supplierKey == null -> strings.whoDeliveredIt
                invoiceNo.isBlank() -> strings.enterBillNumber
                totalValue <= 0 -> strings.enterAnAmount
                else -> strings.recordPurchase
            },
            onClick = {
                val key = supplierKey ?: return@PrimaryButton
                val paid = if (settledNow) null else (Money.parse(paidText) ?: 0.0)
                if (itemised) {
                    // Stock arriving, against an account and a piece of paper —
                    // which is why this is not `restock` with a supplier string
                    // that went nowhere.
                    store.recordPurchase(
                        product = chosenProduct,
                        supplierKey = key,
                        quantity = quantityValue,
                        unitCost = costValue,
                        paid = paid,
                        createdAt = arrivedAt,
                        invoiceNo = invoiceNo
                    )
                } else {
                    // Money owed and nothing on the shelf to show for it. The same
                    // record either way, deliberately: a statement should not care
                    // which way a supplier's bill was entered.
                    store.recordSupplierBill(
                        supplierKey = key,
                        amount = totalValue,
                        paid = paid,
                        createdAt = arrivedAt,
                        invoiceNo = invoiceNo
                    )
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
 * The bill's customer picker, on the other side of the counter and for the same
 * reason — a typed name is not an account, and a delivery filed against one that
 * does not exist is money the shop cannot see it owes. It cannot block the work
 * either: a name matching nobody offers to become a supplier in the same tap.
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
                LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
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

/**
 * What arrived, where anything did.
 *
 * The supplier picker's shape with one difference that matters: this one is
 * **optional**, and says so in the box rather than in a note. Nothing here can
 * create a product — a delivery is the wrong moment to be deciding what a new
 * line is called, what it costs and what it sells for, and a supplier bill that
 * names nothing is a real record rather than a fallback.
 */
@Composable
private fun ProductPicker(
    typed: String,
    chosen: Product?,
    state: ShopState,
    currency: Currency,
    strings: Strings,
    onType: (String) -> Unit,
    onChoose: (Product) -> Unit
) {
    val needle = typed.trim().lowercase()
    val matches = remember(state.products, needle, chosen) {
        when {
            chosen != null -> emptyList()
            needle.isEmpty() -> state.products
            else -> state.products.filter { it.name.lowercase().contains(needle) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        NocturneField(
            value = typed,
            onValueChange = onType,
            label = strings.whichProductArrived,
            placeholder = strings.optionalField
        )

        if (matches.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .card(Metrics.controlRadius)
                    .hairline(Nocturne.accent, Metrics.controlRadius)
                    .padding(vertical = 3.dp)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
                    items(matches, key = { it.uid }) { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChoose(candidate) }
                                .padding(horizontal = 11.dp, vertical = 9.dp)
                        ) {
                            Text(
                                candidate.name,
                                style = NocturneType.inter(13.5),
                                color = Nocturne.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                strings.stockLabel(candidate.stock),
                                style = NocturneType.meta,
                                color = Nocturne.neutral500
                            )
                            Spacer(Modifier.width(8.dp))
                            // The buying price, not the selling one: this list
                            // exists to start a purchase, and that is the figure
                            // about to be typed over.
                            Text(
                                Money.text(candidate.cost, currency),
                                style = NocturneType.inter(13.0),
                                color = Nocturne.accent400
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Four rows and a sliver of the fifth, the same as the bill's customer list. */
private val LIST_MAX_HEIGHT = 150.dp
