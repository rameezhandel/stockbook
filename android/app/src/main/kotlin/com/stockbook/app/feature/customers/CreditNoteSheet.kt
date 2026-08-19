package com.stockbook.app.feature.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.DateField
import com.stockbook.app.design.FieldEmphasis
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.CreditNote
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Product
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.Timestamps
import com.stockbook.core.money.Money
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One line of returned goods while the note is being typed.
 *
 * Both numbers are held as **text**, so a half-typed value ("1." or "") is
 * representable and the field is never re-formatted under the thumb that is
 * typing into it — the lesson the bill's own line card records.
 */
private class ReturnLine(val productUid: String, val name: String, priceText: String) {
    var qtyText by mutableStateOf("1")
    var priceText by mutableStateOf(priceText)

    val qty: Int get() = qtyText.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
    val price: Double get() = Money.parse(priceText) ?: 0.0
    val lineTotal: Double get() = qty * price
}

/**
 * Issuing — or correcting — a credit note against one customer.
 *
 * The payment sheet's shape, because the two acts rhyme: an amount, a date, a
 * note, and a balance that has to come down by it. What differs is the one thing
 * the footnote says out loud — **no money changes hands** — and the optional list
 * of goods, which is what decides whether the shelf moves.
 *
 * The number is required and typed. Its own series, so it is checked against
 * other credit notes and not against the bill book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditNoteSheet(
    customer: Customer,
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    editing: CreditNote? = null,
    onClose: () -> Unit
) {
    val key = editing?.id ?: customer.key

    var noteNo by remember(key) { mutableStateOf(editing?.noteNo.orEmpty()) }
    var amount by remember(key) {
        mutableStateOf(
            // Only where there is nothing to add up. A typed amount sitting
            // behind lines is the second answer this form refuses to hold, the
            // same as the bill's.
            if (editing != null && editing.lines.isEmpty()) Money.amount(editing.total, currency) else ""
        )
    }
    var reason by remember(key) { mutableStateOf(editing?.reason.orEmpty()) }
    var issuedAt by remember(key) { mutableStateOf(editing?.issuedAt ?: Timestamps.now()) }
    var pickingDate by remember { mutableStateOf(false) }
    var productQuery by remember(key) { mutableStateOf("") }
    var adding by remember(key) { mutableStateOf(false) }

    val lines = remember(key) {
        mutableStateListOf<ReturnLine>().also { list ->
            editing?.lines?.forEach { line ->
                val uid = line.productUid ?: return@forEach
                list.add(
                    ReturnLine(uid, line.name, Money.amount(line.price, currency))
                        .also { it.qtyText = line.qty.toString() }
                )
            }
        }
    }

    val total = if (lines.isEmpty()) Money.parse(amount) ?: 0.0 else lines.sumOf { it.lineTotal }

    // Recomputed against the whole state so a number freed by removing the note
    // that held it stops being a clash immediately — and never counting the note
    // being corrected, or opening 00130 to fix its date would be told 00130 is
    // taken, by itself.
    val clash = remember(state, noteNo, editing) {
        store.creditNoteWithNo(noteNo, exceptId = editing?.id)
    }

    val canSave = noteNo.isNotBlank() && total > 0 && clash == null
    /** What will still be owed once this is saved. */
    val remaining = customer.owed - total

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = if (editing != null) strings.editCreditNote else strings.issueACreditNote,
            subtitle = customer.name,
            onClose = onClose
        )

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            NocturneField(
                value = noteNo,
                onValueChange = { noteNo = it },
                label = strings.creditNoteNo,
                placeholder = strings.creditNoteNoHint,
                isRequiredAndEmpty = noteNo.isBlank(),
                height = 40.dp,
                fontSize = 13.5,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            DateField(
                label = strings.creditedOn,
                value = strings.pickedDate(issuedAt),
                onClick = { pickingDate = true },
                height = 40.dp,
                modifier = Modifier.weight(1f)
            )
        }

        if (clash != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                strings.creditNoteAlreadyUsed(strings.longDate(clash.issuedAt)),
                style = NocturneType.meta,
                color = Nocturne.accent400
            )
        }

        if (pickingDate) {
            val picker = rememberDatePickerState(initialSelectedDateMillis = issuedAt.toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { pickingDate = false },
                confirmButton = {
                    GhostButton(strings.done, onClick = {
                        picker.selectedDateMillis?.let { millis ->
                            // The picker hands back midnight UTC. Re-anchored to
                            // midday in the phone's own zone, so the note lands on
                            // the day the owner tapped whatever the offset — which
                            // is what the statement buckets by.
                            issuedAt = Instant.ofEpochMilli(millis)
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

        // The figure, however it was arrived at. Never both at once: a typed
        // amount beside a line sum is two answers to one question.
        if (lines.isEmpty()) {
            NocturneField(
                value = amount,
                onValueChange = { amount = it },
                label = strings.amountCredited,
                height = Metrics.tallInputHeight,
                numeric = true,
                isRequiredAndEmpty = total <= 0,
                emphasis = FieldEmphasis.SELLING_PRICE,
                prefix = currency.symbol.trim(),
                fontSize = 17.0
            )
        } else {
            ReturnedTotal(
                total = total,
                count = lines.size,
                currency = currency,
                strings = strings,
                onClear = { lines.clear() }
            )
        }

        lines.forEach { line ->
            Spacer(Modifier.height(8.dp))
            ReturnedLineCard(
                line = line,
                currency = currency,
                strings = strings,
                onRemove = { lines.remove(line) }
            )
        }

        Spacer(Modifier.height(10.dp))

        // Quiet, and last: most credit notes here are a figure agreed across a
        // counter rather than a pile of goods coming back.
        if (adding) {
            ReturnedItemPicker(
                typed = productQuery,
                state = state,
                currency = currency,
                strings = strings,
                onType = { productQuery = it },
                onChoose = { product ->
                    val existing = lines.firstOrNull { it.productUid == product.uid }
                    if (existing != null) existing.qtyText = (existing.qty + 1).toString()
                    else lines.add(
                        ReturnLine(product.uid, product.name, Money.amount(product.price, currency))
                    )
                    productQuery = ""
                    adding = false
                }
            )
        } else {
            GhostButton(
                if (lines.isEmpty()) strings.addReturnedItems else strings.addAnotherItem,
                onClick = { adding = true },
                fontSize = 12.5
            )
        }

        Spacer(Modifier.height(12.dp))

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

        NocturneField(
            value = reason,
            onValueChange = { reason = it },
            label = strings.creditReason,
            placeholder = strings.creditReasonExample
        )

        Spacer(Modifier.height(8.dp))

        Text(
            strings.creditNoteNotAPayment,
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        PrimaryButton(
            title = when {
                clash != null -> strings.changeTheCreditNoteNo
                noteNo.isBlank() -> strings.enterCreditNoteNumber
                total <= 0 -> strings.enterAnAmount
                else -> strings.saveCreditNote
            },
            onClick = {
                if (!canSave) return@PrimaryButton
                val drafts = lines.map { DraftLine(it.productUid, it.qty, it.price) }
                if (editing != null) {
                    store.updateCreditNote(
                        id = editing.id,
                        customerKey = customer.key,
                        lines = drafts,
                        amount = Money.parse(amount),
                        noteNo = noteNo,
                        reason = reason,
                        issuedAt = issuedAt
                    )
                } else {
                    store.addCreditNote(
                        customerKey = customer.key,
                        lines = drafts,
                        amount = Money.parse(amount),
                        noteNo = noteNo,
                        reason = reason,
                        issuedAt = issuedAt
                    )
                }
                onClose()
            },
            enabled = canSave,
            fullWidth = true,
            height = 48.dp,
            fontSize = 15.0
        )

        if (editing != null) {
            Spacer(Modifier.height(6.dp))
            GhostButton(
                strings.removeCreditNote,
                onClick = {
                    store.deleteCreditNote(editing.id)
                    onClose()
                },
                tint = Nocturne.neutral500,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The total once goods have been named, and the way back to typing a figure.
 *
 * Takes the amount box's place rather than sitting beside it, exactly as the
 * bill's does.
 */
@Composable
private fun ReturnedTotal(
    total: Double,
    count: Int,
    currency: Currency,
    strings: Strings,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card(Metrics.controlRadius)
            .hairline(Nocturne.accent700, Metrics.controlRadius)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.itemsReturned,
                style = NocturneType.inter(13.0),
                color = Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
            Text(Money.text(total, currency), style = NocturneType.bigNumber(26.0), color = Nocturne.text)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.fromItems(count),
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
            GhostButton(strings.removeItems, onClick = onClear, fontSize = 12.0)
        }
    }
}

/** One returned line: how many, at what they were charged. */
@Composable
private fun ReturnedLineCard(
    line: ReturnLine,
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
                height = Metrics.compactControlHeight,
                fontSize = 13.5,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            NocturneField(
                value = line.priceText,
                onValueChange = { line.priceText = it },
                label = strings.paidPerPiece,
                numeric = true,
                prefix = currency.symbol.trim(),
                height = Metrics.compactControlHeight,
                fontSize = 13.5,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Which goods came back.
 *
 * The delivery sheet's product picker with the selling price shown rather than
 * the buying one: this list exists to credit what somebody was charged.
 */
@Composable
private fun ReturnedItemPicker(
    typed: String,
    state: ShopState,
    currency: Currency,
    strings: Strings,
    onType: (String) -> Unit,
    onChoose: (Product) -> Unit
) {
    val needle = typed.trim().lowercase()
    val matches = remember(state.products, needle) {
        if (needle.isEmpty()) state.products else state.products.filter { it.name.lowercase().contains(needle) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        NocturneField(
            value = typed,
            onValueChange = onType,
            label = strings.itemsReturned,
            placeholder = strings.search
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
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(matches, key = { it.uid }) { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChoose(candidate) }
                                .padding(horizontal = 11.dp, vertical = 9.dp)
                        ) {
                            Glyph(Icon.items, size = 13.dp, tint = Nocturne.neutral500)
                            Spacer(Modifier.width(9.dp))
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
                                Money.text(candidate.price, currency),
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
