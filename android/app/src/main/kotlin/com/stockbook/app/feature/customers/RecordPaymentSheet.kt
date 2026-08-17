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
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    onClose: () -> Unit
) {
    var amount by remember(customer.key) { mutableStateOf("") }
    var note by remember(customer.key) { mutableStateOf("") }
    var receivedAt by remember(customer.key) { mutableStateOf(Timestamps.now()) }
    var pickingDate by remember { mutableStateOf(false) }

    val typed = Money.parse(amount) ?: 0.0
    val canSave = typed > 0
    /** What they will still owe once this is saved — usually the target is zero. */
    val remaining = customer.owed - typed

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(title = strings.recordAPayment, subtitle = customer.name, onClose = onClose)

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

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.receivedOn,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
            Text(
                strings.longDate(receivedAt),
                style = NocturneType.inter(13.0),
                color = Nocturne.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(Metrics.controlRadius))
                    .clickable { pickingDate = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

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
            strings.paymentNotAgainstOneBill,
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        PrimaryButton(
            title = if (canSave) strings.savePayment else strings.enterAnAmount,
            onClick = {
                if (!canSave) return@PrimaryButton
                store.recordPayment(
                    customerKey = customer.key,
                    amount = typed,
                    receivedAt = receivedAt,
                    note = note
                )
                onClose()
            },
            enabled = canSave,
            fullWidth = true,
            height = 48.dp,
            fontSize = 15.0
        )
    }
}
