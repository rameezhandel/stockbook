package com.stockbook.app.feature.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.FadedRule
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.core.model.Purchase
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * One delivery, opened from the book.
 *
 * The bill sheet's mirror, including where correcting and removing live: on the
 * document rather than on the list row, so the thing being changed is on screen
 * while it is changed. Removing a delivery takes its stock back **off** the
 * shelf, which the note says out loud — that is the part that surprises people.
 */
@Composable
fun PurchaseSheet(
    purchase: Purchase,
    state: ShopState,
    store: StockbookStore,
    strings: Strings,
    /** Hands the delivery back to the sheet it was entered on, filled in. */
    onEdit: (Purchase) -> Unit,
    onClose: () -> Unit
) {
    // Falls back to what opened the sheet, which matters after a database replace
    // has removed it from under the sheet.
    val live = state.purchases.firstOrNull { it.id == purchase.id } ?: purchase
    val currency = state.settings.currency
    val supplierName = remember(state, live.supplierKey) {
        store.supplier(live.supplierKey)?.name ?: live.supplierKey
    }
    // Keyed on the delivery, so a sheet reopened on another one does not arrive
    // with the first tap already spent.
    var confirmingRemoval by remember(live.id) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = strings.deliveryDetail,
            subtitle = strings.longDate(live.createdAt),
            onClose = onClose
        )

        Line(strings.supplier, supplierName)
        // Only when there was one. An empty row headed "Invoice no." reads as a
        // number the app lost rather than one the delivery never had.
        live.invoiceNo?.let { Line(strings.invoiceNoField, it) }
        // Only where stock actually arrived. A supplier's bill for a mixed load
        // names no product, and a line with an empty label and `0 × SAR 0`
        // against it is the app inventing a delivery nobody described — the total
        // below is what that bill has to say, and it says it.
        live.name?.let { Line(it, strings.perPiece(live.qty, Money.text(live.unitCost, currency))) }

        FadedRule(modifier = Modifier.padding(vertical = 10.dp))

        Line(strings.total, Money.text(live.total, currency), strong = true)
        Line(
            strings.youOwe,
            if (live.balance > 0) Money.text(live.balance, currency) else strings.settledUp,
            tint = if (live.balance > 0) Nocturne.accent400 else Nocturne.neutral400
        )

        Spacer(Modifier.height(14.dp))
        SecondaryButton(
            strings.editBill,
            onClick = { onEdit(live) },
            fullWidth = true,
            height = 44.dp,
            fontSize = 13.5,
            leading = Icon.edit
        )

        // Two taps, because this one moves stock as well as money, and the note
        // under it says which way the stock moves.
        Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            GhostButton(
                if (confirmingRemoval) strings.tapAgainToRemove else strings.removeSupplierBill,
                onClick = {
                    if (confirmingRemoval) {
                        store.deletePurchase(live.id)
                        onClose()
                    } else {
                        confirmingRemoval = true
                    }
                },
                fontSize = 12.0,
                tint = Nocturne.neutral500
            )
            Text(
                strings.removeSupplierBillNote,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun Line(label: String, value: String, strong: Boolean = false, tint: Color? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Text(
            label,
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = if (strong) NocturneType.inter(15.0) else NocturneType.inter(13.0),
            color = tint ?: Nocturne.text
        )
    }
}
