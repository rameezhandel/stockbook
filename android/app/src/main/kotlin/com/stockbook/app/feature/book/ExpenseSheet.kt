package com.stockbook.app.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.DateField
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.Expense
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.Timestamps
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Writing down money the owner spent, and correcting it later.
 *
 * Two fields and a date. There is no number to type, unlike every other document
 * in this app: an invoice, a receipt and a credit note all have a number because
 * there is a slip in a drawer carrying the same one, and there is no such slip
 * behind a tank of petrol.
 *
 * Removing is a plain ghost button with no confirmation, where removing a bill
 * asks twice. That is not carelessness about the owner's data — it is that
 * nothing else moves. A deleted bill puts stock back and frees a number; this is
 * a line leaving a private list, and it can be typed again in ten seconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseSheet(
    /** The expense being corrected, or null for a new one. */
    editing: Expense?,
    state: ShopState,
    store: StockbookStore,
    strings: Strings,
    onClose: () -> Unit
) {
    val currency = state.settings.currency

    var amount by remember(editing) {
        mutableStateOf(editing?.let { Money.amount(it.amount, currency) } ?: "")
    }
    var note by remember(editing) { mutableStateOf(editing?.note ?: "") }
    var spentAt by remember(editing) { mutableStateOf(editing?.spentAt ?: Timestamps.now()) }
    var pickingDate by remember { mutableStateOf(false) }

    val typed = Money.parse(amount) ?: 0.0
    val canSave = typed > 0 && note.isNotBlank()

    // What the shop has called an expense before, filtered by what is typed.
    // Keyed on `state` as well as the text: a plain call over a StateFlow's
    // current value subscribes to nothing, so the list would sit still after the
    // expense that should have joined it was saved.
    //
    // Dropped entirely once the box matches one exactly — offering somebody the
    // word they have just finished typing is a row that can only be in the way.
    val suggestions = remember(state, note) {
        store.expenseNotes(note).let { found ->
            if (found.size == 1 && found.single().equals(note.trim(), ignoreCase = true)) emptyList()
            else found
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = if (editing == null) strings.newExpense else strings.editExpense,
            onClose = onClose
        )

        NocturneField(
            value = note,
            onValueChange = { note = it },
            label = strings.expenseWhatFor,
            placeholder = strings.expenseWhatForHint,
            isRequiredAndEmpty = note.isBlank(),
            modifier = Modifier.fillMaxWidth()
        )

        // A shortcut and never a requirement: unlike the customer picker, where a
        // typed name has no account behind it, anything typed here is a perfectly
        // good expense — so this list only ever saves keystrokes.
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .card(Metrics.controlRadius)
                    .hairline(Nocturne.accent, Metrics.controlRadius)
                    .padding(vertical = 3.dp)
            ) {
                suggestions.forEach { suggestion ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { note = suggestion }
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Glyph(Icon.expenses, size = 12.dp, tint = Nocturne.neutral500)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            suggestion,
                            style = NocturneType.inter(13.5),
                            color = Nocturne.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            NocturneField(
                value = amount,
                onValueChange = { amount = it },
                label = strings.amountField,
                placeholder = "0",
                numeric = true,
                prefix = currency.symbol.trim(),
                isRequiredAndEmpty = typed <= 0,
                height = 40.dp,
                fontSize = 13.5,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            DateField(
                label = strings.expenseSpentOn,
                value = strings.pickedDate(spentAt),
                onClick = { pickingDate = true },
                height = 40.dp,
                modifier = Modifier.weight(1f)
            )
        }

        if (pickingDate) {
            val picker = rememberDatePickerState(initialSelectedDateMillis = spentAt.toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { pickingDate = false },
                confirmButton = {
                    GhostButton(strings.done, onClick = {
                        picker.selectedDateMillis?.let { millis ->
                            // The picker hands back midnight UTC. Re-anchored to
                            // midday in the phone's own zone, so the expense lands
                            // on the day the owner tapped whatever the offset —
                            // which is what `spentIn` buckets by.
                            spentAt = Instant.ofEpochMilli(millis)
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

        Spacer(Modifier.height(8.dp))
        Text(
            strings.expensesArePrivate,
            style = NocturneType.meta,
            color = Nocturne.neutral500
        )

        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            // The button says what is missing rather than going dead and silent —
            // the same rule every other sheet here follows.
            title = when {
                note.isBlank() -> strings.enterWhatItWasFor
                typed <= 0 -> strings.enterAnAmount
                editing == null -> strings.saveExpense
                else -> strings.saveChanges
            },
            onClick = {
                if (editing == null) {
                    store.addExpense(typed, note, spentAt)
                } else {
                    store.updateExpense(editing.id, typed, note, spentAt)
                }
                onClose()
            },
            enabled = canSave,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (editing != null) {
            Spacer(Modifier.height(6.dp))
            GhostButton(
                strings.removeExpense,
                onClick = {
                    store.deleteExpense(editing.id)
                    onClose()
                },
                tint = Nocturne.neutral500,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                strings.removeExpenseNote,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            )
        }
    }
}
