package com.stockbook.app.feature.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.hairline
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Product
import com.stockbook.core.money.Money
import com.stockbook.core.store.RestockMode
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * Putting stock back on the shelf, two ways.
 *
 * **Quick add** is the common case — you tipped a bag into the bin and the count
 * is now higher. **Purchase entry** is a supplier delivery, and it is the only
 * path that changes the buying price: cost here is "latest paid", not a weighted
 * average, so the new figure simply takes over.
 */
@Composable
fun AddStockSheet(
    product: Product,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    onClose: () -> Unit
) {
    var purchase by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }

    val quantityValue = quantity.trim().toIntOrNull() ?: (Money.parse(quantity) ?: 0.0).toInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = strings.addStock,
            subtitle = strings.onShelfNow(product.name, product.stock),
            onClose = onClose
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            ModePill(strings.quickAdd, active = !purchase, onClick = { purchase = false }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            ModePill(strings.purchaseEntry, active = purchase, onClick = { purchase = true }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Metrics.cardGap))

        if (purchase) {
            NocturneField(
                value = supplier,
                onValueChange = { supplier = it },
                label = strings.supplier,
                placeholder = strings.whoDeliveredIt
            )
            Spacer(Modifier.height(Metrics.cardGap))
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            NocturneField(
                value = quantity,
                onValueChange = { quantity = it },
                label = strings.howMany,
                numeric = true,
                modifier = Modifier.weight(1f)
            )
            if (purchase) {
                Spacer(Modifier.width(8.dp))
                NocturneField(
                    value = unitCost,
                    onValueChange = { unitCost = it },
                    label = strings.paidPerPiece,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // The note is the only place the two modes explain themselves, so it
        // states the consequence rather than restating the mode.
        Text(
            if (purchase) {
                val cost = Money.parse(unitCost) ?: 0.0
                strings.purchaseNote(Money.text(maxOf(0, quantityValue) * cost, currency))
            } else {
                strings.quickAddNote(Money.text(product.cost, currency))
            },
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(top = 10.dp)
        )

        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            title = if (purchase) strings.recordPurchase else strings.addToStock(maxOf(0, quantityValue)),
            onClick = {
                // Zero or empty quantity just closes the sheet — the owner
                // opened it, then thought better of it, and that should not
                // need a Cancel button.
                store.restock(
                    product,
                    quantity = quantityValue,
                    mode = if (purchase) RestockMode.PURCHASE else RestockMode.QUICK_ADD,
                    unitCost = if (purchase) Money.parse(unitCost) else null
                )
                onClose()
            },
            fullWidth = true
        )
    }
}

@Composable
private fun ModePill(
    title: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(Metrics.compactControlHeight)
            .clip(RoundedCornerShape(Metrics.controlRadius))
            .background(Nocturne.bg)
            .hairline(if (active) Nocturne.accent else Nocturne.neutral800, Metrics.controlRadius)
            .clickable(onClick = onClick)
    ) {
        Text(
            title,
            style = NocturneType.inter(13.0, FontWeight.Medium),
            color = if (active) Nocturne.accent else Nocturne.neutral500
        )
    }
}
