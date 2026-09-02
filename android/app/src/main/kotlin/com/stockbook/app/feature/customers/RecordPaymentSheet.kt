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
import com.stockbook.app.design.DateField
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
    onClose: () -> Unit
) {
    PaymentSheet(
        name = customer.name,
        key = editing?.id ?: customer.key,
        owed = customer.owed,
        dateLabel = strings.receivedOn,
        footnote = strings.paymentNotAgainstOneBill,
        currency = currency,
        strings = strings,
        state = state,
        existingAmount = editing?.amount,
        existingNote = editing?.note,
        existingNo = editing?.paymentNo,
        existingDate = editing?.receivedAt,
        // Never counting the one being corrected, or opening 008455 to fix its
        // amount would be told 008455 is taken — by itself.
        clashDate = { store.paymentWithNo(it, exceptId = editing?.id)?.receivedAt },
        onSave = { amount, at, note, no ->
            val saved =
                if (editing != null) store.updatePayment(editing.id, amount, at, note, no)
                else store.recordPayment(customer.key, amount, at, note, no)
            // Read back through the store rather than built from what was typed:
            // the balance on the slip has to be the balance the statement will
            // show, and only the store knows what that is.
            saved?.id?.let { store.receiptForPayment(it) }?.let { onReceipt(it, true) }
        },
        onViewReceipt = editing?.let { payment ->
            {
                val slip = store.receiptForPayment(payment.id)
                if (slip != null) onReceipt(slip, false)
            }
        },
        onDelete = editing?.let { { store.deletePayment(it.id) } },
        onClose = onClose
    )
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
    val canSave = typed > 0 && paymentNo.isNotBlank() && clash == null
    /**
     * What will still be owed once this is saved.
     *
     * A payment being corrected is already inside `owed`, so its old amount is
     * added back before the new one comes off — otherwise correcting 300 to 350
     * would read as though 650 had been paid.
     */
    val remaining = owed + (existingAmount ?: 0.0) - typed

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = if (onDelete != null) strings.correctAPayment else strings.recordAPayment,
            subtitle = name,
            onClose = onClose
        )

        // The paper first: which receipt this is, and the day it was written.
        // Same row, same order as the credit note's, because it is the same act
        // of copying a slip into the book.
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
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
            label = strings.amountReceived,
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
                paymentNo.isBlank() -> strings.enterPaymentNumber
                typed <= 0 -> strings.enterAnAmount
                onDelete != null -> strings.saveChanges
                else -> strings.savePayment
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
