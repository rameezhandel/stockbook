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
import com.stockbook.core.store.MergePreview
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * Joining two accounts for one firm, from the one that will be the one to go.
 *
 * Two steps in one sheet: pick who to keep, then agree to the figures. The
 * second step is the point of the whole sheet. A merge rewrites history and
 * there is no undo in this app, so the owner is shown what will move and what
 * the survivor will owe **before** they agree, rather than being left to notice
 * a changed balance afterwards.
 *
 * The list is everybody else, searchable, because the account you are joining to
 * is by definition one you already have and may be nowhere near the top of any
 * order.
 */
@Composable
fun MergeSheet(
    /** The account being merged away, by key. */
    fromKey: String,
    isSupplier: Boolean,
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    /** Closed without joining anything. The account behind is still there. */
    onClose: () -> Unit,
    /**
     * Joined. Told apart from [onClose] because the account the owner was looking
     * at is the one that has gone — the screen behind this sheet is now about
     * somebody who no longer exists, and has to be closed with it.
     */
    onMerged: () -> Unit
) {
    var query by remember(fromKey) { mutableStateOf("") }
    var chosen by remember(fromKey) { mutableStateOf<String?>(null) }

    val fromName = remember(state, fromKey) {
        if (isSupplier) store.supplier(fromKey)?.name else store.customer(fromKey)?.name
    } ?: return

    // Everybody except the one going. Keyed on the state so a payment taken
    // elsewhere while this is open does not leave a stale figure on the
    // confirmation.
    val candidates = remember(state, fromKey, query) {
        val found = if (isSupplier) {
            if (query.isBlank()) store.suppliers() else store.suppliers(matching = query)
        } else {
            if (query.isBlank()) store.customers() else store.customers(matching = query)
        }
        found.filterNot { it.key == fromKey }.map { it.key to it.name }
    }

    val preview: MergePreview? = remember(state, fromKey, chosen) {
        chosen?.let {
            if (isSupplier) store.previewSupplierMerge(fromKey, it)
            else store.previewCustomerMerge(fromKey, it)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = strings.mergeAccounts,
            subtitle = fromName,
            // Back to the list rather than out of the sheet: the owner who
            // picked the wrong name wants the other name, not to start again.
            onClose = if (preview == null) onClose else ({ chosen = null })
        )

        if (preview == null) {
            Text(
                strings.mergeChoose(fromName),
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
                    if (query.isBlank()) strings.nobodyToMergeWith else strings.nobodyMatches,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
                return@Column
            }

            candidates.forEach { (key, name) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Metrics.rowGap)
                        .card(Metrics.controlRadius)
                        .clickable { chosen = key }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Glyph(Icon.customer, size = 13.dp, tint = Nocturne.neutral500)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        name,
                        style = NocturneType.rowPrimary,
                        color = Nocturne.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            return@Column
        }

        // Step two: the figures, then the button.
        Text(
            strings.mergeConfirm(preview.from, preview.into),
            style = NocturneType.rowPrimary,
            color = Nocturne.text,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .card(Metrics.controlRadius)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Only the lines with something on them. A supplier has no bills and
            // no credit notes, and a zero drawn for each would read as a figure
            // somebody checked.
            val moving = listOfNotNull(
                preview.bills.takeIf { it > 0 }?.let { strings.billsMoving(it) },
                preview.deliveries.takeIf { it > 0 }?.let { strings.deliveriesMoving(it) },
                preview.payments.takeIf { it > 0 }?.let { strings.paymentsMoving(it) },
                preview.creditNotes.takeIf { it > 0 }?.let { strings.creditNotesMoving(it) }
            )
            moving.forEach { line ->
                Text(line, style = NocturneType.meta, color = Nocturne.text)
            }

            // The figure the owner is really agreeing to, so it is the one drawn
            // largest and last.
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().padding(top = if (moving.isEmpty()) 0.dp else 8.dp)
            ) {
                Text(
                    strings.willOwe(preview.into),
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    Money.text(preview.owed, currency),
                    style = NocturneType.rowPrimary,
                    color = Nocturne.accent400
                )
            }
            Text(
                strings.willBeGone(preview.from),
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Text(
            strings.mergeCannotBeUndone,
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            title = strings.mergeAccounts,
            onClick = {
                val into = chosen ?: return@PrimaryButton
                val done = if (isSupplier) store.mergeSupplier(fromKey, into)
                else store.mergeCustomer(fromKey, into)
                if (done) onMerged() else onClose()
            },
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
