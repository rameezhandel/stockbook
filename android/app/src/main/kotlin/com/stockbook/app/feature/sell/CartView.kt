package com.stockbook.app.feature.sell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.DateField
import com.stockbook.app.design.FieldEmphasis
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.draw.clip
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.ChoicePill
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.stockbook.core.model.Bill
import com.stockbook.core.model.Customer
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.model.Currency
import com.stockbook.app.photos.PhotoFailure
import com.stockbook.app.photos.PhotoStore
import com.stockbook.app.photos.PhotoStrip
import com.stockbook.app.photos.rememberPhotoCapture
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.text.Strings

/**
 * The bill being written. The most important screen in the app: it is what the
 * owner is looking at while a customer waits.
 *
 * **A form, not a cart.** A bill here is a number, a date, somebody and a
 * figure — the paper book was written first, so the total is already known and
 * rebuilding it product by product to arrive at it is work for nothing. Saying
 * what was sold is one optional button below the figure, and the only thing it
 * buys is the shelf moving.
 *
 * Read top to bottom it is the order the owner already has the answers in: the
 * number on the paper, the day, who it is for, what it came to.
 */
@Composable
fun CartView(
    cart: Cart,
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    onBrowse: () -> Unit,
    onSave: () -> Unit,
    /**
     * The bill being corrected, where this is a correction rather than a new
     * bill. It changes two things and nothing else: what the button says, and
     * which bill the number check is allowed to ignore.
     */
    editing: Bill? = null,
    modifier: Modifier = Modifier
) {
    var pickingDate by remember { mutableStateOf(false) }

    // The bill already carrying this number, if the shop has written it twice.
    // Recomputed against `state` as well as the text, so a number freed by
    // removing the bill that held it stops being a clash immediately — and never
    // counting the bill being edited, or opening 1024 to fix its date would be
    // told 1024 is taken, by itself.
    val clash = remember(state, cart.invoiceNo, editing) {
        store.billWithInvoiceNo(cart.invoiceNo, exceptNumber = editing?.number)
    }

    if (pickingDate) {
        DateDialog(
            current = cart.soldAt,
            strings = strings,
            onPicked = { cart.soldAt = it },
            onDismiss = { pickingDate = false }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // The form scrolls and the Save button does not: with a long bill and the
        // keyboard up, a Save that scrolled away is a Save the owner hunts for
        // with a customer waiting.
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Metrics.screenPadding,
                end = Metrics.screenPadding,
                bottom = 12.dp
            )
        ) {
            item {
                PaperRow(
                    cart = cart,
                    strings = strings,
                    currency = currency,
                    clash = clash,
                    onPickDate = { pickingDate = true }
                )
            }

            item {
                CustomerPicker(
                    cart = cart,
                    state = state,
                    store = store,
                    currency = currency,
                    strings = strings,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            // The figure, however it was arrived at. Never both at once: a typed
            // amount beside a line sum is two answers to one question, and the
            // owner has no way to tell which one is about to be saved.
            item {
                if (cart.isEmpty) {
                    NocturneField(
                        value = cart.amountText,
                        onValueChange = { cart.amountText = it },
                        label = strings.amountField,
                        height = Metrics.tallInputHeight,
                        numeric = true,
                        isRequiredAndEmpty = cart.subtotal <= 0,
                        emphasis = FieldEmphasis.SELLING_PRICE,
                        prefix = currency.symbol.trim(),
                        fontSize = 17.0,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    ItemisedTotal(
                        cart = cart,
                        currency = currency,
                        strings = strings,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            items(cart.lines, key = { it.productUid }) { line ->
                CartLineCard(
                    line = line,
                    stock = state.products.firstOrNull { it.uid == line.productUid }?.stock ?: 0,
                    currency = currency,
                    strings = strings,
                    cart = cart,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Quiet on purpose. Most bills here never touch it, and a button that
            // shouts is a button an owner in a hurry taps by mistake.
            item {
                SecondaryButton(
                    if (cart.isEmpty) strings.addItems else strings.addAnotherItem,
                    onClick = onBrowse,
                    fullWidth = true,
                    height = 42.dp,
                    fontSize = 13.5,
                    leading = Icon.add,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            item {
                PaymentBlock(
                    cart = cart,
                    currency = currency,
                    strings = strings,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            // Last on the form, and the last thing before Save. It is the one
            // box here the owner writes for themselves rather than for the
            // paper, so it waits until the number, the money and who owes it are
            // all settled. Optional: most bills never touch it.
            item {
                NocturneField(
                    value = cart.note,
                    onValueChange = { cart.note = it },
                    label = strings.billNote,
                    placeholder = strings.billNoteHint,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                )
            }
        }

        SaveBar(cart = cart, clash = clash, editing = editing, strings = strings, onSave = onSave)
    }
}

/**
 * The paper's number and the day it happened, side by side.
 *
 * First on the form because they describe *the bill* rather than the money, and
 * because a shop entering yesterday's book needs the date before it thinks about
 * anything else. The number is required; the date has today in it already.
 */
@Composable
private fun PaperRow(
    cart: Cart,
    strings: Strings,
    /** The shop's own, because this row shows what a discount comes to. */
    currency: Currency,
    clash: Bill?,
    onPickDate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            NocturneField(
                value = cart.invoiceNo,
                onValueChange = { cart.invoiceNo = it },
                placeholder = strings.invoiceNoHint,
                label = strings.invoiceNoField,
                // Marked, and it means it: a bill cannot be saved without a
                // number, and nothing puts one in the box but the owner.
                isRequiredAndEmpty = cart.invoiceNo.isBlank(),
                height = 40.dp,
                fontSize = 13.5,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            DateField(
                label = strings.billDate,
                value = strings.pickedDate(cart.soldAt),
                onClick = onPickDate,
                height = 40.dp,
                modifier = Modifier.weight(1f)
            )
        }

        // Named, not merely reported: "already used" leaves the owner hunting,
        // "already used — Ahmed, 18 Aug" points at the bill.
        if (clash != null) {
            Text(
                strings.invoiceNoAlreadyUsed(clash.who, strings.longDate(clash.createdAt)),
                style = NocturneType.meta,
                color = Nocturne.accent400,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // A percentage off the whole bill, and what it comes to said back
        // immediately — a shopkeeper typing "10" wants to see "SAR 25 off"
        // before they hand the paper over, not after.
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            NocturneField(
                value = cart.discountText,
                onValueChange = { cart.discountText = it },
                label = strings.discountField,
                placeholder = strings.discountHint,
                numeric = true,
                height = 40.dp,
                fontSize = 13.5,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (cart.discountValue(currency) > 0) {
                    strings.discountComesTo(Money.text(cart.discountValue(currency), currency))
                } else {
                    ""
                },
                style = NocturneType.meta,
                color = Nocturne.accent400,
                modifier = Modifier.weight(1f).padding(bottom = 12.dp)
            )
        }

        // The paper, photographed while it is being written — which is when the
        // owner is holding it. Directly under its number, because that is the
        // other thing on this form that describes the document rather than the
        // money.
        BillPhotoRow(cart = cart, strings = strings, modifier = Modifier.padding(top = 12.dp))
    }
}

/**
 * Taking a photograph of the bill from the form it is being written on.
 *
 * The strip is what has been taken so far, tappable to take one back off. There
 * is no viewer here: the form is for writing the bill, and looking closely at the
 * paper is what opening the saved bill is for.
 */
@Composable
private fun BillPhotoRow(cart: Cart, strings: Strings, modifier: Modifier = Modifier) {
    var trouble by remember { mutableStateOf<String?>(null) }

    val capture = rememberPhotoCapture(
        onSaved = { id -> cart.addPhoto(id); trouble = null },
        onFailed = { reason ->
            trouble = when (reason) {
                PhotoFailure.NO_CAMERA -> strings.noCameraOnThisPhone
                else -> strings.couldNotReadThatPhoto
            }
        }
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.billPhotos,
                style = NocturneType.fieldLabel,
                color = Nocturne.neutral400,
                modifier = Modifier.weight(1f)
            )
            GhostButton(strings.takePhoto, onClick = capture.takePhoto, fontSize = 12.0)
            Spacer(Modifier.width(10.dp))
            GhostButton(strings.chooseFromPhotos, onClick = capture.chooseFromPhotos, fontSize = 12.0)
        }

        if (cart.photoIds.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PhotoStrip(
                ids = cart.photoIds,
                strings = strings,
                // Tapping takes it off the form — and only off the form. The
                // file stays until a save reconciles it or the next launch
                // sweeps it, because a correction the owner then abandons must
                // not have already destroyed a picture the bill still names.
                onOpen = { id -> cart.removePhoto(id) }
            )
        }

        trouble?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = NocturneType.meta, color = Nocturne.accent400)
        }
    }
}

/**
 * The total, once the bill has been itemised.
 *
 * It takes the amount box's place rather than sitting beside it, and says where
 * the figure came from: it stopped being something typed the moment there were
 * lines to add up. "Remove items" is the way back to typing, and it empties the
 * bill rather than merely hiding the sum — a hidden line would still move the
 * shelf on save.
 */
@Composable
private fun ItemisedTotal(
    cart: Cart,
    currency: Currency,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .card(Metrics.controlRadius)
            .hairline(Nocturne.accent700, Metrics.controlRadius)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Shown only when there is one: three lines where a shop gives no
        // discount would be two lines of ceremony above the figure that matters.
        if (cart.discountValue(currency) > 0) {
            DeductionLine(strings.subtotalLabel, Money.text(cart.subtotal, currency), Nocturne.neutral500)
            DeductionLine(
                strings.discountOf(Money.amount(cart.discountPercent ?: 0.0, currency)),
                "− ${Money.text(cart.discountValue(currency), currency)}",
                Nocturne.accent400
            )
            Spacer(Modifier.height(4.dp))
        }

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.total,
                style = NocturneType.inter(13.0),
                color = Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
            Text(
                Money.text(cart.total(currency), currency),
                style = NocturneType.bigNumber(26.0),
                color = Nocturne.text
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.fromItems(cart.lines.size),
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
            GhostButton(strings.removeItems, onClick = { cart.removeLines() }, fontSize = 12.0)
        }
    }
}

/** Paid in full or part, and what is left if part. */
@Composable
private fun PaymentBlock(
    cart: Cart,
    currency: Currency,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ChoicePill(
                strings.paidInFull,
                Icon.confirm,
                selected = cart.payMode == PayMode.FULL,
                onClick = { cart.payMode = PayMode.FULL },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            ChoicePill(
                strings.partPayment,
                Icon.edit,
                selected = cart.payMode == PayMode.PART,
                onClick = { cart.payMode = PayMode.PART },
                modifier = Modifier.weight(1f)
            )
        }

        if (cart.payMode == PayMode.PART) {
            Spacer(Modifier.height(10.dp))
            NocturneField(
                value = cart.paidText,
                onValueChange = { cart.paidText = it },
                label = strings.paidNow,
                height = 40.dp,
                numeric = true,
                prefix = currency.symbol.trim()
            )
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    strings.balance,
                    style = NocturneType.inter(12.5),
                    color = Nocturne.neutral500,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    Money.text(cart.balance(currency), currency),
                    style = NocturneType.inter(15.0),
                    color = Nocturne.accent400
                )
            }
        }
    }
}

/**
 * The one thing that leaves the screen, pinned to the bottom of it.
 *
 * Validation is the button's label, never a toast: it says what is missing and
 * stays disabled until it isn't.
 */
@Composable
private fun SaveBar(
    cart: Cart,
    clash: Bill?,
    editing: Bill?,
    strings: Strings,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(Nocturne.surface)) {
        Box(Modifier.fillMaxWidth().height(Metrics.hairline).background(Nocturne.neutral800))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.screenPadding)
                .padding(top = 10.dp)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        ) {
            PrimaryButton(
                title = when {
                    // A number on two bills is two records the shop cannot tell
                    // apart later, so this one is a refusal rather than a warning.
                    clash != null -> strings.changeTheInvoiceNo
                    // A correction says so, because "Save bill" over a bill that
                    // already exists reads as a second one about to be written.
                    cart.canSave -> if (editing != null) strings.saveChanges else strings.saveBill
                    // Whatever is missing, the button names it: an empty name box
                    // needs a name, a typed one needs a choice from the list, a
                    // cleared number box needs a number, and a bill with neither
                    // a figure nor a line on it needs one of the two.
                    cart.customer.isBlank() -> strings.enterCustomerName
                    cart.customerKey == null -> strings.chooseFromTheList
                    cart.invoiceNo.isBlank() -> strings.enterBillNumber
                    else -> strings.enterAnAmount
                },
                onClick = onSave,
                enabled = cart.canSave && clash == null,
                fullWidth = true,
                height = 48.dp,
                fontSize = 15.0
            )
        }
    }
}

/**
 * The day the sale happened, which is not always the day it is being typed.
 *
 * Material's own dialog, which is the one place in this app where stock chrome
 * shows through. Reimplementing a calendar to avoid that is not a trade worth
 * making.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    current: Instant,
    strings: Strings,
    onPicked: (Instant) -> Unit,
    onDismiss: () -> Unit
) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = current.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            GhostButton(strings.done, onClick = {
                picker.selectedDateMillis?.let { millis ->
                    // The picker hands back midnight UTC. Re-anchoring to midday in
                    // the phone's own zone keeps the bill on the day the owner
                    // tapped, whatever the offset — which is what the statement
                    // buckets by.
                    onPicked(
                        Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .atTime(12, 0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                    )
                }
                onDismiss()
            })
        }
    ) {
        DatePicker(state = picker)
    }
}

/** One product on the bill: quantity, live stock, and the editable price. */
@Composable
private fun CartLineCard(
    line: Cart.Line,
    stock: Int,
    currency: Currency,
    strings: Strings,
    cart: Cart,
    modifier: Modifier = Modifier
) {
    var priceText by remember(line.productUid, line.price) {
        mutableStateOf(Money.amount(line.price, currency))
    }

    Column(modifier = modifier.fillMaxWidth().card().padding(horizontal = 12.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                line.name,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(Money.text(line.lineTotal, currency), style = NocturneType.inter(15.0), color = Nocturne.text)
            IconButton(
                Icon.delete,
                onClick = { cart.remove(line.productUid) },
                size = 15.dp,
                tint = Nocturne.neutral500,
                contentDescription = strings.remove(line.name)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Stepper(
                quantity = line.qty,
                onChange = { cart.setQuantity(it, line.productUid) },
                strings = strings
            )
            Spacer(Modifier.width(8.dp))
            // Wraps rather than truncates: "only 3 in stock" is the warning that
            // stops a wrong bill going out.
            Text(
                if (line.qty > stock) strings.onlyInStock(stock) else strings.piecesInStock(stock),
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            PriceBox(
                symbol = currency.symbol.trim(),
                text = priceText,
                overridden = line.isPriceOverridden,
                onChange = {
                    priceText = it
                    Money.parse(it)?.let { value -> cart.setPrice(value, line.productUid) }
                }
            )
        }

        if (line.isPriceOverridden) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Glyph(Icon.edit, size = 11.dp, tint = Nocturne.accent400)
                Spacer(Modifier.width(5.dp))
                Text(
                    strings.usualPriceNote(Money.text(line.basePrice, currency)),
                    style = NocturneType.inter(11.0),
                    color = Nocturne.accent400,
                    modifier = Modifier.weight(1f)
                )
                GhostButton(
                    strings.reset,
                    onClick = {
                        cart.resetPrice(line.productUid)
                        priceText = Money.amount(line.basePrice, currency)
                    },
                    fontSize = 11.0
                )
            }
        }
    }
}

@Composable
private fun Stepper(quantity: Int, onChange: (Int) -> Unit, strings: Strings) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(Metrics.compactControlHeight)
            .background(Nocturne.bg)
            .hairline(radius = Metrics.controlRadius)
    ) {
        Box(modifier = Modifier.size(34.dp).clickable { onChange(quantity - 1) }, contentAlignment = Alignment.Center) {
            Glyph(Icon.remove, size = 15.dp, tint = Nocturne.text)
        }
        Text(
            "$quantity",
            style = NocturneType.inter(14.0),
            color = Nocturne.text,
            modifier = Modifier.width(34.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Box(modifier = Modifier.size(34.dp).clickable { onChange(quantity + 1) }, contentAlignment = Alignment.Center) {
            Glyph(Icon.add, size = 15.dp, tint = Nocturne.text)
        }
    }
}

@Composable
private fun PriceBox(symbol: String, text: String, overridden: Boolean, onChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(112.dp)
    ) {
        NocturneField(
            value = text,
            onValueChange = onChange,
            height = Metrics.compactControlHeight,
            numeric = true,
            prefix = symbol,
            emphasis = if (overridden) FieldEmphasis.CHANGED else FieldEmphasis.NONE,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            fontSize = 13.5
        )
    }
}

/**
 * The customer on the bill: type to filter, then **choose**.
 *
 * A typed name is not a customer. Free text is how "Ahmed", "ahmed " and "Ahmd"
 * become three people with three balances, and once statements and payments hang
 * off a customer that stops being cosmetic. So the field filters the roster and
 * nothing is settled until a row is tapped — `Cart.canSave` refuses until then.
 *
 * It must never be able to block a sale, though, so a name matching nobody offers
 * to become a customer on the spot. That is one tap more than typing used to be,
 * and it leaves a real account behind rather than a string.
 *
 * The list is drawn **below** the field, which is the opposite of what this
 * screen used to do. It was above while the field sat on the bottom edge with the
 * keyboard under it, where a dropdown would have opened off-screen; the field is
 * near the top of a form now, and a list that pushed the bill number upwards to
 * appear would move the two things the owner had just read.
 */
@Composable
private fun CustomerPicker(
    cart: Cart,
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val typed = cart.customer.trim()

    // Keyed on `state`, which is the observable value: reading the store's own
    // getters during composition subscribes to nothing, so the list would go stale
    // the moment a customer was added from inside it.
    val everyone = remember(state) { store.customers() }
    val query = Customer.key(typed)

    // Not `store.customerSuggestions`, which deliberately drops an exact match —
    // sensible when the field also accepted free text, fatal now that a choice is
    // compulsory: typing a name in full would remove the only row that could be
    // tapped, and offer no way to create it either, because it already exists.
    // Every match, not the first four. The list scrolls instead — a cap looks
    // identical to "no such customer" for anyone who happens to sort fifth, and on
    // a roster of any size that is a name the owner cannot reach at all.
    val matches = remember(everyone, query, cart.customerKey) {
        if (cart.customerKey != null) emptyList()
        else everyone.filter { query.isEmpty() || it.key.contains(query) }
    }

    // Offered when what was typed is nobody yet.
    val canCreate = cart.customerKey == null &&
        typed.isNotEmpty() &&
        everyone.none { it.key == query }

    fun choose(customer: Customer) {
        cart.selectCustomer(customer)
        keyboard?.hide()
        focusManager.clearFocus()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        NocturneField(
            value = cart.customer,
            onValueChange = { cart.typeCustomer(it) },
            label = strings.customerLabel,
            placeholder = strings.customerName,
            height = 40.dp,
            // Marked until somebody is actually chosen, not merely until the box
            // has characters in it. Accent means "this still needs something", so a
            // chosen customer drops back to the neutral border — the two states
            // have to look different or the gate is invisible.
            isRequiredAndEmpty = cart.customerKey == null
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
                // Lazy and bounded. The maximum is deliberately not a whole number
                // of rows: a sliver of the next one showing is what tells the
                // owner there is more below, without a scrollbar to draw.
                LazyColumn(modifier = Modifier.heightIn(max = CUSTOMER_LIST_MAX_HEIGHT)) {
                    items(matches, key = { it.key }) { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { choose(candidate) }
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

                // Outside the scrolling part on purpose: this is the way out when
                // nobody matches, and it must never be something to scroll for.
                if (canCreate) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                store.addCustomer(typed)?.let { record ->
                                    store.customer(record.key)?.let { choose(it) }
                                }
                            }
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    ) {
                        Glyph(Icon.add, size = 12.dp, tint = Nocturne.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            strings.addAsCustomer(typed),
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
 * How tall the customer list may grow before it scrolls.
 *
 * Rows are about 34dp, so this shows four and a sliver of the fifth — enough to
 * read as "there is more" while leaving the rest of the form on screen with the
 * keyboard up.
 */
private val CUSTOMER_LIST_MAX_HEIGHT = 150.dp

/** One `label … value` line above the total, in the total card's own type. */
@Composable
private fun DeductionLine(label: String, value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = NocturneType.meta, color = Nocturne.neutral500, modifier = Modifier.weight(1f))
        Text(value, style = NocturneType.inter(12.5), color = tint)
    }
}
