package com.stockbook.app.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.card
import com.stockbook.core.model.Currency
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * Moving what one account owes onto another, both of them real.
 *
 * Two steps: pick the other account, then agree to the figures. Nothing is
 * absorbed — both accounts survive and every invoice stays where it was issued,
 * which the sheet says in as many words, because the amount is the only thing
 * that moves and the owner is agreeing to it on two accounts at once.
 */
@Composable
fun MoveBalanceSheet(
    /** The account the balance leaves, by key. */
    fromKey: String,
    isSupplier: Boolean,
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    onClose: () -> Unit
) {
    var query by remember(fromKey) { mutableStateOf("") }
    var chosen by remember(fromKey) { mutableStateOf<String?>(null) }
    var amount by remember(fromKey) { mutableStateOf("") }
    var why by remember(fromKey) { mutableStateOf("") }

    // Reduced to name-and-owed inside each branch. `Customer` and `Supplier`
    // share no supertype, so a value holding either infers as `Any` and no field
    // on it resolves — which compiles nowhere and only CI would find.
    val leaving = remember(state, fromKey) {
        if (isSupplier) store.supplier(fromKey)?.let { it.name to it.owed }
        else store.customer(fromKey)?.let { it.name to it.owed }
    } ?: return

    val candidates: List<Triple<String, String, Double>> = remember(state, fromKey, query) {
        if (isSupplier) {
            (if (query.isBlank()) store.suppliers() else store.suppliers(matching = query))
                .filterNot { it.key == fromKey }
                .map { Triple(it.key, it.name, it.owed) }
        } else {
            (if (query.isBlank()) store.customers() else store.customers(matching = query))
                .filterNot { it.key == fromKey }
                .map { Triple(it.key, it.name, it.owed) }
        }
    }

    val target = remember(state, chosen) { chosen?.let { key -> candidates.firstOrNull { it.first == key } } }
    val typed = Money.parse(amount) ?: 0.0
    val canSave = typed > 0

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = strings.moveABalance,
            subtitle = leaving.first,
            // Back to the list rather than out of the sheet: the owner who picked
            // the wrong name wants the other name, not to start again.
            onClose = if (target == null) onClose else ({ chosen = null })
        )

        if (target == null) {
            Text(
                strings.moveBalanceChoose(leaving.first),
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            NocturneField(
                value = query,
                onValueChange = { query = it },
                placeholder = strings.search,
                height = 40.dp,
                fontSize = 13.5,
                modifier = Modifier.padding(bottom = Metrics.rowGap)
            )

            if (candidates.isEmpty()) {
                Text(
                    if (query.isBlank()) strings.nobodyToMoveTo else strings.nobodyMatches,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
                return@Column
            }

            candidates.forEach { (key, name, owed) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Metrics.rowGap)
                        .card(Metrics.controlRadius)
                        .clickable {
                            chosen = key
                            // The whole outstanding figure is what a
                            // consolidation moves, so it is offered rather than
                            // typed — and still editable, because a part
                            // transfer is a real thing.
                            if (amount.isBlank() && leaving.second > 0) {
                                amount = Money.amount(leaving.second, currency)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Glyph(Icon.customer, size = 13.dp, tint = Nocturne.neutral500)
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            name,
                            style = NocturneType.rowPrimary,
                            color = Nocturne.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            Money.text(owed, currency),
                            style = NocturneType.meta,
                            color = if (owed > 0) Nocturne.accent400 else Nocturne.neutral500
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            return@Column
        }

        // Step two: how much, why, and what each will owe once it is done.
        NocturneField(
            value = amount,
            onValueChange = { amount = it },
            label = strings.amountToMove,
            placeholder = Money.amount(0.0, currency),
            numeric = true,
            prefix = currency.symbol.trim(),
            isRequiredAndEmpty = amount.isBlank()
        )
        Spacer(Modifier.height(12.dp))
        NocturneField(
            value = why,
            onValueChange = { why = it },
            label = strings.whyMoved,
            placeholder = strings.whyMovedExample
        )
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .card(Metrics.controlRadius)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Both sides, because the owner is agreeing to two figures and only
            // one of them is on the screen they came from.
            AfterLine(strings.willOweAfter(leaving.first), leaving.second - typed, currency)
            Spacer(Modifier.height(4.dp))
            AfterLine(strings.willOweAfter(target.second), target.third + typed, currency)
        }

        Text(
            strings.movedBalanceIsAnEntry,
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            title = if (canSave) strings.moveABalance else strings.enterAnAmountToMove,
            onClick = {
                if (!canSave) return@PrimaryButton
                val moved = store.transferBalance(
                    fromKey = fromKey,
                    intoKey = target.first,
                    amount = typed,
                    isSupplier = isSupplier,
                    note = why
                )
                if (moved != null) onClose()
            },
            enabled = canSave,
            fullWidth = true,
            height = 46.dp,
            fontSize = 15.0
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            strings.cancel,
            onClick = { chosen = null },
            fullWidth = true,
            height = 40.dp,
            fontSize = 13.0
        )
    }
}

/** One side of "what each will owe", with the figure carrying its own sign. */
@Composable
private fun AfterLine(label: String, owed: Double, currency: Currency) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            Money.signed(owed, currency),
            style = NocturneType.rowPrimary,
            // A balance moved past zero is money held in advance, which is
            // allowed and worth looking different from a debt.
            color = if (owed >= 0) Nocturne.accent400 else Nocturne.neutral500
        )
    }
}
