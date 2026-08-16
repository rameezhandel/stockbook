package com.stockbook.app.feature.bills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.core.model.Bill
import com.stockbook.core.model.ShopState
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * Opening a bill from history.
 *
 * The bill is looked up **live from the state** rather than rendered from the
 * value that opened the sheet, so voiding from in here redraws the document in
 * place — which is the only way to see that the tap did what it said.
 *
 * Voiding lives here rather than on the list row: it is the app's one
 * destructive action on history, and needing a tap to open the bill first is the
 * cheapest possible confirmation step.
 */
@Composable
fun BillSheet(
    bill: Bill,
    state: ShopState,
    store: StockbookStore,
    strings: Strings,
    onClose: () -> Unit
) {
    // Falls back to the value it was opened with, which matters after a database
    // replace has removed it from under the sheet.
    val live = state.bills.firstOrNull { it.number == bill.number } ?: bill

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = strings.billDetailTitle,
            subtitle = strings.items(live.lines.size),
            onClose = onClose
        )

        BillTemplate(
            bill = live,
            currency = state.settings.currency,
            strings = strings,
            shopName = state.settings.ownerName
        )

        if (!live.voided) {
            Spacer(Modifier.height(14.dp))
            SecondaryButton(
                strings.voidAndRestock,
                onClick = { store.void(live) },
                fullWidth = true,
                height = 44.dp,
                fontSize = 13.5
            )
        }
    }
}
