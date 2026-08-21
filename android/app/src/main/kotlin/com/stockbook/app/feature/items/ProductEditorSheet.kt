package com.stockbook.app.feature.items

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.FieldEmphasis
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Product
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * Create or edit one product.
 *
 * The reference implementation of the app's validation rule: **no error toasts,
 * no red text**. An incomplete draft leaves Save disabled and puts an accent
 * border on whatever is still empty.
 */
@Composable
fun ProductEditorSheet(
    product: Product?,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    onClose: () -> Unit,
    onAddStock: (Product) -> Unit,
    onDeleted: (Product) -> Unit
) {
    var name by remember(product) { mutableStateOf(product?.name ?: "") }
    var stock by remember(product) { mutableStateOf(product?.stock?.toString() ?: "") }
    var cost by remember(product) { mutableStateOf(product?.let { Money.amount(it.cost, currency) } ?: "") }
    var price by remember(product) { mutableStateOf(product?.let { Money.amount(it.price, currency) } ?: "") }

    val canSave = StockbookStore.isProductDraftComplete(name, stock, cost, price)

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = if (product == null) strings.newProduct else strings.editProduct,
            onClose = onClose
        )

        NocturneField(
            value = name,
            onValueChange = { name = it },
            label = strings.productName,
            placeholder = strings.productNameExample,
            height = Metrics.tallInputHeight,
            isRequiredAndEmpty = name.isBlank(),
            fontSize = 15.0,
            imeAction = ImeAction.Next
        )
        Spacer(Modifier.height(Metrics.cardGap))

        // The count is asked for once, when the product is created, and never
        // again from this sheet.
        //
        // It used to sit here on every edit too, which made it a second,
        // unlabelled "Set count" one keystroke from the price boxes: fixing a
        // miscount could rewrite a selling price, and "In stock" said nothing
        // about whether the number was absolute or something to add. Afterwards
        // the shelf moves for a stated reason — a delivery in, a bill out, or a
        // recount through Set count, which says what it is.
        Row(modifier = Modifier.fillMaxWidth()) {
            if (product == null) {
                NocturneField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = strings.openingStock,
                    numeric = true,
                    isRequiredAndEmpty = stock.isBlank(),
                    imeAction = ImeAction.Next,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
            }
            NocturneField(
                value = cost,
                onValueChange = { cost = it },
                label = strings.buyingPrice,
                numeric = true,
                isRequiredAndEmpty = cost.isBlank(),
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f)
            )
        }

        if (product == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                strings.openingStockNote,
                style = NocturneType.meta,
                color = Nocturne.neutral500
            )
        }
        Spacer(Modifier.height(Metrics.cardGap))

        NocturneField(
            value = price,
            onValueChange = { price = it },
            label = strings.sellingPrice,
            numeric = true,
            isRequiredAndEmpty = (Money.parse(price) ?: 0.0) <= 0,
            emphasis = FieldEmphasis.SELLING_PRICE
        )

        Text(
            marginNote(price, cost, currency, strings),
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(top = 10.dp)
        )

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            if (product != null) {
                SecondaryButton(strings.addStock, onClick = { onAddStock(product) })
                Spacer(Modifier.width(8.dp))
            }
            PrimaryButton(
                strings.save,
                onClick = {
                    if (!canSave) return@PrimaryButton
                    val stockValue = stock.trim().toIntOrNull() ?: (Money.parse(stock) ?: 0.0).toInt()
                    val costValue = Money.parse(cost) ?: 0.0
                    val priceValue = Money.parse(price) ?: 0.0
                    if (product == null) {
                        store.addProduct(name, stockValue, costValue, priceValue)
                    } else {
                        store.update(product, name, costValue, priceValue)
                    }
                    onClose()
                },
                enabled = canSave,
                fullWidth = true,
                modifier = Modifier.weight(1f)
            )
        }

        if (product != null) {
            Spacer(Modifier.height(6.dp))
            GhostButton(
                strings.removeThisProduct,
                onClick = {
                    store.delete(product)
                    onDeleted(product)
                    onClose()
                },
                tint = Nocturne.neutral500,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** `You make SAR 30 a piece.` — or a nudge when the sums do not work. */
private fun marginNote(price: String, cost: String, currency: Currency, strings: Strings): String {
    val sell = Money.parse(price) ?: 0.0
    val buy = Money.parse(cost) ?: 0.0
    if (sell <= buy) return strings.setPriceAboveCost
    return strings.youMakeAPiece(Money.text(sell - buy, currency))
}
