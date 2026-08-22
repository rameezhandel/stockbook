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
import androidx.compose.runtime.mutableStateListOf
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
import com.stockbook.app.design.DateField
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
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.card
import com.stockbook.core.model.Supplier
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.hairline
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Product
import com.stockbook.core.model.Purchase
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.store.DraftPurchaseLine
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * One line of the delivery being entered, held as text while it is being typed.
 *
 * Text rather than numbers for the reason the cart holds its price that way: a
 * half-typed "1" on the way to "12" is not a quantity of one, and reading it as
 * one is how the total on screen disagrees with the total about to be saved.
 *
 * The box is seeded with what the product already costs, because most deliveries
 * arrive at the price the last one did and retyping it is work for nothing. What
 * an emptied box means is decided by [fallbackCost] below.
 */
private class DeliveryLine(
    val productUid: String,
    val name: String,
    qty: Int,
    cost: Double,
    /**
     * What the product already costs, which is what the store falls back to when
     * the box is empty. Held here so the sheet works the figure out the same way
     * the store will: without it, clearing the box would show a total of nothing
     * over a Save that was about to write the old price.
     */
    val fallbackCost: Double,
    currency: Currency
) {
    var qtyText by mutableStateOf(qty.toString())
    var costText by mutableStateOf(if (cost > 0) Money.amount(cost, currency) else "")

    val qty: Int get() = qtyText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
    val cost: Double get() = Money.parse(costText)?.takeIf { it > 0 } ?: fallbackCost
    val lineTotal: Double get() = qty * cost
}

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
 *
 * A delivery entered wrongly comes **back here** filled in, rather than to an
 * editor of its own: the two would have to be kept saying the same things about
 * quantities, costs and what counts as itemised, and one of them would lose.
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
    /**
     * The delivery being corrected, where this sheet was opened from one. Null is
     * a delivery being recorded for the first time, which is the ordinary case.
     */
    editing: Purchase? = null,
    onClose: () -> Unit
) {
    // What the delivery said arrived, for as many of those products as are still
    // on the books. A purchase can outlive what it bought, and the sheet cannot
    // offer a quantity of something that no longer exists — a delivery whose
    // every product has gone comes back as a bill for a figure, which is what
    // saving it again would make it anyway.
    //
    // Seeded once. Keyed on the correction rather than on `state.products`, or
    // adding a product mid-edit would rebuild the list and throw away whatever
    // had been typed into it.
    val lines = remember(editing) {
        mutableStateListOf<DeliveryLine>().apply {
            val seed = editing?.items.orEmpty().mapNotNull { line ->
                val uid = line.productUid ?: return@mapNotNull null
                if (state.products.none { it.uid == uid }) return@mapNotNull null
                DeliveryLine(
                    uid,
                    line.name,
                    line.qty,
                    line.unitCost,
                    state.products.first { it.uid == uid }.cost,
                    currency
                )
            }
            addAll(seed)
            // Opened from a product with the Delivery button: that product is
            // what the owner is looking at, so it is line one already.
            if (editing == null && product != null) {
                add(DeliveryLine(product.uid, product.name, 1, product.cost, product.cost, currency))
            }
        }
    }

    // A correction is a supplier's bill and never a shelf count: there is one
    // document open, and it is not the shelf.
    var supplierBill by remember { mutableStateOf(product == null || editing != null) }
    var count by remember { mutableStateOf("") }
    /**
     * What was typed into the supplier box, and who was actually chosen.
     *
     * The name is read off the store once, to seed the box. Unkeyed on purpose:
     * re-reading it on every recomposition is what would throw away whatever the
     * owner had typed over it.
     */
    var supplier by remember {
        mutableStateOf(
            editing?.let { store.supplier(it.supplierKey)?.name ?: it.supplierKey }.orEmpty()
        )
    }
    var supplierKey by remember { mutableStateOf(editing?.supplierKey) }
    /** What has been typed into the product box while looking for the next line. */
    var productQuery by remember { mutableStateOf("") }
    var addingLine by remember { mutableStateOf(false) }
    // Only where there is no arithmetic to show instead — an itemised delivery's
    // total is what its lines add up to, and a figure typed beside that is a
    // second answer to a question already answered.
    var amount by remember {
        mutableStateOf(
            if (editing != null && editing.items.isEmpty()) Money.amount(editing.total, currency) else ""
        )
    }
    var settledNow by remember { mutableStateOf(editing?.paid == null) }
    var paidText by remember {
        mutableStateOf(editing?.paid?.let { Money.amount(it, currency) }.orEmpty())
    }
    /** The number on the supplier's invoice, and the day it arrived. */
    var invoiceNo by remember { mutableStateOf(editing?.invoiceNo.orEmpty()) }
    var arrivedAt by remember { mutableStateOf(editing?.createdAt ?: Timestamps.now()) }
    var pickingDate by remember { mutableStateOf(false) }

    val countValue = count.trim().toIntOrNull()
    // Itemised means at least one line with a count on it. A product with no
    // quantity is half an answer, and guessing the other half would put stock on
    // the shelf nobody said arrived.
    val liveLines = lines.filter { it.qty > 0 }
    val itemised = liveLines.isNotEmpty()
    // What the delivery is costed at, which is what `recordPurchase` will use.
    // Worked out the same way on both sides of the call, or the sheet shows a
    // total the store does not save.
    val totalValue = if (itemised) liveLines.sumOf { it.lineTotal } else Money.parse(amount) ?: 0.0

    // The delivery already filed under this number, whoever it came from. Across
    // the whole book rather than per supplier: one number, one piece of paper —
    // and never the delivery being corrected, which is already filed under its
    // own number and must not be told so.
    val clash = remember(state, invoiceNo, editing) {
        store.purchaseWithInvoiceNo(invoiceNo, exceptId = editing?.id)
    }

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
            // Which document this is, on a correction: the sheet is otherwise
            // identical to the one that files a new delivery, and the date is what
            // tells the owner which one they opened.
            subtitle = if (editing != null) {
                strings.longDate(editing.createdAt)
            } else {
                product?.let { strings.onShelfNow(it.name, it.stock) }
            },
            onClose = onClose
        )

        // Only where there is a shelf to count. Opened from the Delivery button,
        // or on a delivery being corrected, there is no product to offer a count
        // of and the sheet is a supplier bill and says so.
        if (product != null && editing == null) {
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
            DateField(
                label = strings.billDate,
                value = strings.pickedDate(arrivedAt),
                onClick = { pickingDate = true },
                modifier = Modifier.weight(1f)
            )
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
                        style = NocturneType.bigNumber(26.0),
                        color = Nocturne.text
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        strings.fromItems(liveLines.size),
                        style = NocturneType.meta,
                        color = Nocturne.neutral500,
                        modifier = Modifier.weight(1f)
                    )
                    GhostButton(strings.removeItems, onClick = { lines.clear() }, fontSize = 12.0)
                }
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

        // What arrived, one card per line on the paper. Optional, all of it: a bill
        // for a mixed load, or for something that never sits on a shelf, names
        // nothing and still owes money.
        lines.forEach { line ->
            DeliveryLineCard(
                line = line,
                currency = currency,
                strings = strings,
                onRemove = { lines.remove(line) }
            )
            Spacer(Modifier.height(8.dp))
        }

        if (addingLine) {
            ProductPicker(
                typed = productQuery,
                state = state,
                store = store,
                currency = currency,
                strings = strings,
                onType = { productQuery = it },
                onChoose = { chosen ->
                    // Choosing something already on the note adds one more of it
                    // rather than a second card saying the same name — the same
                    // rule the cart and the credit note follow.
                    val existing = lines.firstOrNull { it.productUid == chosen.uid }
                    if (existing != null) {
                        existing.qtyText = (existing.qty + 1).toString()
                    } else {
                        lines.add(DeliveryLine(chosen.uid, chosen.name, 1, chosen.cost, chosen.cost, currency))
                    }
                    productQuery = ""
                    addingLine = false
                }
            )
            Spacer(Modifier.height(Metrics.cardGap))
        } else {
            SecondaryButton(
                if (lines.isEmpty()) strings.addItems else strings.addAnotherItem,
                onClick = { addingLine = true },
                fullWidth = true,
                height = 42.dp,
                fontSize = 13.5,
                leading = Icon.add
            )
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
                // A correction says so, because "Record purchase" over a delivery
                // that already happened reads as a second one about to be filed.
                editing != null -> strings.saveChanges
                else -> strings.recordPurchase
            },
            onClick = {
                val key = supplierKey ?: return@PrimaryButton
                val paid = if (settledNow) null else (Money.parse(paidText) ?: 0.0)
                // The same figures into whichever of the two this is, and one
                // shape for both: the store applies the same rule to what it is
                // handed — lines are stock arriving, no lines is a figure — and it
                // ignores the amount where there is arithmetic instead. Money owed
                // with nothing on the shelf to show for it is the same record
                // deliberately, because a statement should not care which way a
                // supplier's bill was entered.
                val drafts = liveLines.map { DraftPurchaseLine(it.productUid, it.qty, it.cost) }
                if (editing != null) {
                    store.updatePurchase(
                        id = editing.id,
                        lines = drafts,
                        supplierKey = key,
                        paid = paid,
                        amount = totalValue,
                        createdAt = arrivedAt,
                        invoiceNo = invoiceNo
                    )
                } else {
                    store.recordPurchase(
                        lines = drafts,
                        supplierKey = key,
                        paid = paid,
                        amount = totalValue,
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
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    onType: (String) -> Unit,
    onChoose: (Product) -> Unit
) {
    val needle = typed.trim().lowercase()
    val matches = remember(state.products, needle) {
        if (needle.isEmpty()) state.products
        else state.products.filter { it.name.lowercase().contains(needle) }
    }
    // The way out when nothing matches, which on a delivery note is often: a
    // supplier's paper is where stock the shop has never carried turns up. Without
    // it, a new line means leaving the sheet, adding the product, coming back and
    // finding your place again — and the half-typed delivery does not survive it.
    //
    // Only on an exact-name miss, so it never offers to create a second "Cisa
    // lock" while the first one is sitting in the list above it.
    val canCreate = remember(state.products, needle) {
        needle.isNotEmpty() && state.products.none { it.name.trim().lowercase() == needle }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        NocturneField(
            value = typed,
            onValueChange = onType,
            label = strings.whichProductArrived,
            placeholder = strings.optionalField
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

                // Outside the scrolling part: it is the way out when nobody
                // matches, and must never be something to scroll for.
                if (canCreate) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // No cost and no price: the line below is where
                                // what it cost gets typed, and what it sells for
                                // is not a question a delivery note answers. The
                                // Items screen is where that gets filled in, and
                                // a product with no selling price shows there as
                                // one that needs it.
                                onChoose(store.addProduct(typed, stock = 0, cost = 0.0, price = 0.0))
                            }
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    ) {
                        Glyph(Icon.add, size = 12.dp, tint = Nocturne.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            strings.addAsProduct(typed.trim()),
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
 * One line of the delivery: what it was, how many, and what each cost.
 *
 * `ReturnedLineCard`'s shape on the credit note sheet, deliberately — the two are
 * the same act pointed in opposite directions, and a shopkeeper who has entered
 * one should recognise the other.
 */
@Composable
private fun DeliveryLineCard(
    line: DeliveryLine,
    currency: Currency,
    strings: Strings,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                line.name,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(Money.text(line.lineTotal, currency), style = NocturneType.inter(15.0), color = Nocturne.text)
            Spacer(Modifier.width(4.dp))
            IconButton(
                Icon.delete,
                onClick = onRemove,
                size = 15.dp,
                tint = Nocturne.neutral500,
                contentDescription = strings.remove(line.name)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            NocturneField(
                value = line.qtyText,
                onValueChange = { line.qtyText = it },
                label = strings.howMany,
                numeric = true,
                // Marked while it is the thing standing between this line and the
                // shelf: a line with no count on it is not saved at all, and the
                // owner should see which one it is rather than wonder why the
                // total is short.
                isRequiredAndEmpty = line.qty <= 0,
                height = Metrics.compactControlHeight,
                fontSize = 13.5,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            NocturneField(
                value = line.costText,
                onValueChange = { line.costText = it },
                label = strings.paidPerPiece,
                numeric = true,
                // Only where leaving it empty would leave no figure at all: an
                // emptied box on a product that already has a cost means "the same
                // as last time", which is a real answer.
                isRequiredAndEmpty = line.qty > 0 && line.cost <= 0,
                prefix = currency.symbol.trim(),
                height = Metrics.compactControlHeight,
                fontSize = 13.5,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Four rows and a sliver of the fifth, the same as the bill's customer list. */
private val LIST_MAX_HEIGHT = 150.dp
