package com.stockbook.app.feature.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.ChoicePill
import com.stockbook.app.design.DateField
import com.stockbook.app.design.Icon
import com.stockbook.app.design.FieldEmphasis
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Loan
import com.stockbook.core.model.Payment
import com.stockbook.core.model.PaymentReceipt
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.Supplier
import com.stockbook.core.model.SupplierPayment
import com.stockbook.core.model.Timestamps
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Money a customer has just handed over against what they owe.
 *
 * Deliberately not attached to a bill. A shop like this is settled by somebody
 * putting cash on the counter against their account, not against invoice #7, and
 * making the owner pick a bill would be asking them to maintain a fiction.
 *
 * The date is pickable, matching iOS, because somebody who settles up on Friday
 * and gets round to entering it on Monday would otherwise have a statement that
 * lies about when the money arrived.
 *
 * The picker is Material's own dialog, which is the one place in this app where
 * stock chrome shows through — everything else is hand-drawn to the design.
 * Reimplementing a calendar to avoid that is not a trade worth making.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentSheet(
    customer: Customer,
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    /** The payment being corrected, or null to take a new one. */
    editing: Payment? = null,
    /**
     * Hands back the slip for the payment, and whether it was **just taken**.
     *
     * That flag cannot be worked out from the router afterwards: both ways in
     * close this sheet on their way out, so by the time the receipt is on screen
     * the two look identical. Only the caller knows which it was.
     */
    onReceipt: (PaymentReceipt, justSaved: Boolean) -> Unit,
    /** The loan being corrected, where the sheet was opened on one. */
    editingLoan: Loan? = null,
    onClose: () -> Unit
) {
    /**
     * Which way the money is going.
     *
     * Money in is where the sheet opens, because taking money is what a shop does
     * fifty times for every once it lends any. A sheet opened to correct an
     * existing record starts on that record's own direction and stays there — the
     * two are different types, and a payment cannot become a loan by tapping a
     * pill any more than it can by having a sign put on it.
     */
    var giving by remember(customer.key, editing?.id, editingLoan?.id) {
        mutableStateOf(editingLoan != null)
    }
    val correcting = editing != null || editingLoan != null

    PaymentSheet(
        name = customer.name,
        key = editing?.id ?: editingLoan?.id ?: "${customer.key}-$giving",
        owed = customer.owed,
        dateLabel = strings.receivedOn,
        footnote = if (giving) strings.loanAddsToWhatTheyOwe else strings.paymentNotAgainstOneBill,
        amountLabel = if (giving) strings.amountLent else strings.amountReceived,
        // A loan comes out of no numbered book, and money handed over adds to
        // what is owed rather than taking off it. Those two are the whole of the
        // difference on this sheet.
        numbered = !giving,
        sign = if (giving) 1 else -1,
        header = if (correcting) null else ({ DirectionPills(giving, strings) { giving = it } }),
        title = when {
            giving && correcting -> strings.correctALoan
            giving -> strings.recordALoan
            correcting -> strings.correctAPayment
            else -> strings.recordAPayment
        },
        saveTitle = if (giving) strings.saveLoan else strings.savePayment,
        currency = currency,
        strings = strings,
        state = state,
        existingAmount = editing?.amount ?: editingLoan?.amount,
        existingNote = editing?.note ?: editingLoan?.note,
        existingNo = editing?.paymentNo,
        existingDate = editing?.receivedAt ?: editingLoan?.lentAt,
        // Never counting the one being corrected, or opening 008455 to fix its
        // amount would be told 008455 is taken — by itself.
        clashDate = { store.paymentWithNo(it, exceptId = editing?.id)?.receivedAt },
        onSave = { amount, at, note, no ->
            when {
                editingLoan != null -> store.updateLoan(editingLoan.id, amount, at, note)
                giving -> store.recordLoan(customer.key, amount, at, note)
                // Read back through the store rather than built from what was
                // typed: the balance on the slip has to be the balance the
                // statement will show, and only the store knows what that is.
                else -> {
                    val saved =
                        if (editing != null) store.updatePayment(editing.id, amount, at, note, no)
                        else store.recordPayment(customer.key, amount, at, note, no)
                    saved?.id?.let { store.receiptForPayment(it) }?.let { onReceipt(it, true) }
                }
            }
        },
        // A loan has no slip. There is no numbered receipt behind it to reprint,
        // and inventing one would be the app claiming paperwork the shop has not
        // got.
        onViewReceipt = editing?.let { payment ->
            {
                val slip = store.receiptForPayment(payment.id)
                if (slip != null) onReceipt(slip, false)
            }
        },
        onDelete = when {
            editingLoan != null -> ({ store.deleteLoan(editingLoan.id) })
            editing != null -> ({ store.deletePayment(editing.id) })
            else -> null
        },
        onClose = onClose
    )
}

/**
 * Received or given, above everything else on the sheet.
 *
 * At the top because it changes what every field below it means — the amount's
 * label, whether a receipt number is asked for, and which way the balance line
 * moves. A control that rewrites the form under it belongs before the form.
 *
 * Only when writing something new. Correcting a record cannot change which of
 * the two it is: money in and money out are separate types, and turning one into
 * the other would be a delete and a write, not an edit.
 */
@Composable
private fun DirectionPills(giving: Boolean, strings: Strings, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        ChoicePill(
            title = strings.moneyReceived,
            icon = Icon.confirm,
            selected = !giving,
            onClick = { onChange(false) },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        ChoicePill(
            title = strings.moneyGiven,
            icon = Icon.expenses,
            selected = giving,
            onClick = { onChange(true) },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * The same sheet, for money going the other way.
 *
 * One body, two entry points, exactly as with the editor: what a payment *is*
 * does not change with its direction — an amount, a date, a note, and a balance
 * that has to come down by it.
 */
@Composable
fun PaySupplierSheet(
    supplier: Supplier,
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    editing: SupplierPayment? = null,
    onReceipt: (PaymentReceipt, justSaved: Boolean) -> Unit,
    onClose: () -> Unit
) {
    PaymentSheet(
        name = supplier.name,
        key = editing?.id ?: supplier.key,
        owed = supplier.owed,
        dateLabel = strings.paidOn,
        footnote = strings.paymentNotAgainstOnePurchase,
        // Money the shop hands a supplier settles what it owes them, so it comes
        // off that balance exactly as a customer's payment comes off theirs —
        // the default sign, and no direction to choose between.
        amountLabel = strings.amountPaid,
        title = if (editing != null) strings.correctAPayment else strings.recordAPayment,
        saveTitle = strings.savePayment,
        currency = currency,
        strings = strings,
        state = state,
        existingAmount = editing?.amount,
        existingNote = editing?.note,
        existingNo = editing?.paymentNo,
        existingDate = editing?.paidAt,
        clashDate = { store.supplierPaymentWithNo(it, exceptId = editing?.id)?.paidAt },
        onSave = { amount, at, note, no ->
            val saved =
                if (editing != null) store.updateSupplierPayment(editing.id, amount, at, note, no)
                else store.recordSupplierPayment(supplier.key, amount, at, note, no)
            saved?.id?.let { store.receiptForSupplierPayment(it) }?.let { onReceipt(it, true) }
        },
        onViewReceipt = editing?.let { payment ->
            {
                val slip = store.receiptForSupplierPayment(payment.id)
                if (slip != null) onReceipt(slip, false)
            }
        },
        onDelete = editing?.let { { store.deleteSupplierPayment(it.id) } },
        onClose = onClose
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentSheet(
    name: String,
    key: String,
    owed: Double,
    dateLabel: String,
    footnote: String,
    currency: Currency,
    strings: Strings,
    state: ShopState,
    existingAmount: Double?,
    existingNote: String?,
    existingNo: String?,
    existingDate: Instant?,
    /**
     * What the amount box asks for, which is the one word that differs between
     * taking money and handing it over.
     */
    amountLabel: String,
    /**
     * Whether a number is asked for and required.
     *
     * False for a loan, and it is not a detail: an invoice, a receipt and a
     * credit note each come out of a numbered book the shop keeps, and a hand of
     * cash across a counter comes out of no book at all. Asking for a number
     * there would be the app inventing paperwork the shop does not have — and
     * `canSave` would then never let the owner past it.
     */
    numbered: Boolean = true,
    /**
     * Which way the money moves: −1 takes it off what is owed, +1 puts it on.
     *
     * The whole of what a loan changes in the arithmetic here. Everything else
     * on this sheet — the balance line, the clamp, the correction that adds the
     * old figure back before applying the new — reads the same either way.
     */
    sign: Int = -1,
    /** A pair of pills above everything, where the sheet records both directions. */
    header: (@Composable () -> Unit)? = null,
    /**
     * What the sheet calls itself and what its button promises.
     *
     * Passed in rather than derived, because the sheet is shared and the words
     * are not. A page headed "Record a payment" whose button says "Save payment"
     * is lying about the record it is about to write, and the owner has no other
     * way to tell which of the two they are making.
     */
    title: String,
    saveTitle: String,
    clashDate: (String) -> Instant?,
    onSave: (amount: Double, at: Instant, note: String, paymentNo: String) -> Unit,
    /**
     * Opens the slip for a payment that already exists.
     *
     * Present only when correcting, for the same reason [onDelete] is: a payment
     * being taken has no receipt until it is saved, and the save opens one
     * anyway. This is the way back to it — a customer who has lost their copy is
     * the whole reason to print a second.
     */
    onViewReceipt: (() -> Unit)?,
    /** Present only when correcting: a payment being taken has nothing to remove. */
    onDelete: (() -> Unit)?,
    onClose: () -> Unit
) {
    var paymentNo by remember(key) { mutableStateOf(existingNo.orEmpty()) }
    var amount by remember(key) {
        mutableStateOf(existingAmount?.let { Money.amount(it, currency) }.orEmpty())
    }
    var note by remember(key) { mutableStateOf(existingNote.orEmpty()) }
    var receivedAt by remember(key) { mutableStateOf(existingDate ?: Timestamps.now()) }
    var confirmingRemoval by remember(key) { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    // Recomputed against the whole state so a number freed by deleting the
    // receipt that held it stops being a clash immediately.
    val clash = remember(state, paymentNo) { clashDate(paymentNo) }

    val typed = Money.parse(amount) ?: 0.0
    val canSave = typed > 0 && (!numbered || paymentNo.isNotBlank()) && clash == null
    /**
     * What will still be owed once this is saved.
     *
     * A payment being corrected is already inside `owed`, so its old amount is
     * taken back out before the new one is applied — otherwise correcting 300 to
     * 350 would read as though 650 had been paid.
     *
     * [sign] is what makes this serve a loan too: money handed over adds to the
     * balance rather than taking off it, and every other line here is unchanged.
     */
    val remaining = owed - sign * (existingAmount ?: 0.0) + sign * typed

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = title,
            subtitle = name,
            onClose = onClose
        )

        header?.invoke()

        // The paper first: which receipt this is, and the day it was written.
        // Same row, same order as the credit note's, because it is the same act
        // of copying a slip into the book.
        //
        // A loan has no paper, so the date stands alone across the full width
        // rather than beside an empty half — a gap where a field was reads as a
        // field that failed to draw.
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            if (numbered) {
                NocturneField(
                    value = paymentNo,
                    onValueChange = { paymentNo = it },
                    label = strings.paymentNoField,
                    placeholder = strings.paymentNoHint,
                    isRequiredAndEmpty = paymentNo.isBlank(),
                    height = 40.dp,
                    fontSize = 13.5,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
            }
            DateField(
                label = dateLabel,
                value = strings.pickedDate(receivedAt),
                onClick = { pickingDate = true },
                height = 40.dp,
                modifier = Modifier.weight(1f)
            )
        }

        if (clash != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                strings.paymentNoAlreadyUsed(strings.longDate(clash)),
                style = NocturneType.meta,
                color = Nocturne.accent400
            )
        }

        Spacer(Modifier.height(12.dp))

        NocturneField(
            value = amount,
            onValueChange = { amount = it },
            label = amountLabel,
            height = Metrics.tallInputHeight,
            numeric = true,
            isRequiredAndEmpty = amount.isBlank(),
            emphasis = FieldEmphasis.SELLING_PRICE,
            prefix = currency.symbol.trim(),
            fontSize = 17.0
        )
        Spacer(Modifier.height(6.dp))

        // Owed before, and what is left after: a running total the owner can check
        // against the cash in their hand before committing.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.closingBalance,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
            Text(
                when {
                    remaining > 0 -> Money.text(remaining, currency)
                    remaining < 0 -> strings.inAdvance(Money.text(-remaining, currency))
                    else -> strings.settledUp
                },
                style = NocturneType.inter(13.0),
                color = if (remaining > 0) Nocturne.accent400 else Nocturne.neutral400
            )
        }
        Spacer(Modifier.height(12.dp))

        if (pickingDate) {
            val picker = rememberDatePickerState(initialSelectedDateMillis = receivedAt.toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { pickingDate = false },
                confirmButton = {
                    GhostButton(strings.done, onClick = {
                        picker.selectedDateMillis?.let { millis ->
                            // The picker hands back midnight UTC. Re-anchoring to
                            // midday in the phone's own zone keeps the payment on
                            // the day the owner tapped, whatever the offset —
                            // which is what the statement buckets by.
                            receivedAt = Instant.ofEpochMilli(millis)
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
        Spacer(Modifier.height(12.dp))

        NocturneField(
            value = note,
            onValueChange = { note = it },
            label = strings.paymentNote,
            placeholder = strings.paymentNoteExample
        )
        Spacer(Modifier.height(8.dp))

        Text(
            footnote,
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        PrimaryButton(
            title = when {
                clash != null -> strings.changeThePaymentNo
                // Gated on `numbered`, exactly as `canSave` is. Without that the
                // button read "Enter a receipt number" on a loan and saved
                // anyway when tapped — the label and the button disagreeing
                // about the same condition.
                numbered && paymentNo.isBlank() -> strings.enterPaymentNumber
                typed <= 0 -> strings.enterAnAmount
                onDelete != null -> strings.saveChanges
                else -> saveTitle
            },
            onClick = {
                if (!canSave) return@PrimaryButton
                onSave(typed, receivedAt, note, paymentNo)
                onClose()
            },
            enabled = canSave,
            fullWidth = true,
            height = 48.dp,
            fontSize = 15.0
        )

        onViewReceipt?.let { view ->
            Spacer(Modifier.height(6.dp))
            GhostButton(
                strings.viewReceipt,
                onClick = {
                    view()
                    onClose()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Removal lives inside the correction, exactly as the credit note's does.
        // Two taps, because it takes a figure out of somebody's account.
        if (onDelete != null) {
            Spacer(Modifier.height(6.dp))
            GhostButton(
                if (confirmingRemoval) strings.tapAgainToRemove else strings.deleteThisPayment,
                onClick = {
                    if (confirmingRemoval) {
                        onDelete()
                        onClose()
                    } else {
                        confirmingRemoval = true
                    }
                },
                tint = if (confirmingRemoval) Nocturne.accent400 else Nocturne.neutral500,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
