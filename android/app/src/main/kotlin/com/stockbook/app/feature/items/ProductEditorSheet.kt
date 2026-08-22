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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.FieldEmphasis
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.RequiredMarking
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.card
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Product
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * One line of a catalogue being entered, held as text while it is being typed.
 *
 * The setup wizard's own draft, which is where this flow comes from — step 3
 * takes the names and step 4 takes the three numbers. A sheet has no steps, so
 * it does both at once: a name becomes a card, and the card carries its numbers.
 */
private class ProductDraft(val name: String) {
    var stock by mutableStateOf("")
    var cost by mutableStateOf("")
    var price by mutableStateOf("")

    val isComplete: Boolean
        get() = StockbookStore.isProductDraftComplete(name, stock, cost, price)
}

/**
 * Enter products, or correct one.
 *
 * **Creating takes as many as the owner has to hand**; correcting takes exactly
 * one. That is not a fork for its own sake: a shop enters a catalogue in
 * handfuls — a new supplier's line, a delivery of things never carried before —
 * and one sheet per product was one Save, one close and one re-open each time.
 * Correcting is the opposite errand: one product, whose name is already known,
 * whose price is being changed on purpose.
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
    // Correcting one. No stock: the opening count is asked once, when the
    // product is created, and creating happens on the other half of this sheet.
    var name by remember(product) { mutableStateOf(product?.name ?: "") }
    var cost by remember(product) { mutableStateOf(product?.let { Money.amount(it.cost, currency) } ?: "") }
    var price by remember(product) { mutableStateOf(product?.let { Money.amount(it.price, currency) } ?: "") }

    // Entering several.
    val drafts = remember { mutableStateListOf<ProductDraft>() }
    var draftName by remember { mutableStateOf("") }

    // A correction has no count to check, so "0" stands in as one already there.
    // The rule itself stays in the store, where both platforms read it.
    val canSave = StockbookStore.isProductDraftComplete(name, "0", cost, price)
    // Nothing half-written gets saved. A card with a name and no price is a
    // product the shop cannot sell, so it holds the Save rather than slipping
    // through — and its own empty box is already wearing the accent border that
    // says which one.
    val canSaveDrafts = drafts.isNotEmpty() && drafts.all { it.isComplete }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = if (product == null) strings.newProduct else strings.editProduct,
            onClose = onClose
        )

        if (product == null) {
            NewProducts(
                drafts = drafts,
                draftName = draftName,
                onDraftName = { draftName = it },
                onAdd = {
                    val cleaned = draftName.trim()
                    draftName = ""
                    // A name already on the list is not a mistake worth
                    // interrupting anybody for — the same rule the setup wizard
                    // follows, and the same rule `addProduct` follows against the
                    // catalogue itself.
                    if (cleaned.isNotEmpty() && drafts.none { it.name.equals(cleaned, ignoreCase = true) }) {
                        drafts.add(ProductDraft(cleaned))
                    }
                },
                onRemove = { drafts.remove(it) },
                canSave = canSaveDrafts,
                onSave = {
                    for (draft in drafts) {
                        store.addProduct(
                            draft.name,
                            draft.stock.trim().toIntOrNull() ?: (Money.parse(draft.stock) ?: 0.0).toInt(),
                            Money.parse(draft.cost) ?: 0.0,
                            Money.parse(draft.price) ?: 0.0
                        )
                    }
                    onClose()
                },
                currency = currency,
                strings = strings
            )
            return@Column
        }

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
        NocturneField(
            value = cost,
            onValueChange = { cost = it },
            label = strings.buyingPrice,
            numeric = true,
            isRequiredAndEmpty = cost.isBlank(),
            imeAction = ImeAction.Next
        )
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
            SecondaryButton(strings.addStock, onClick = { onAddStock(product) })
            Spacer(Modifier.width(8.dp))
            PrimaryButton(
                strings.save,
                onClick = {
                    if (!canSave) return@PrimaryButton
                    store.update(product, name, Money.parse(cost) ?: 0.0, Money.parse(price) ?: 0.0)
                    onClose()
                },
                enabled = canSave,
                fullWidth = true,
                modifier = Modifier.weight(1f)
            )
        }

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

/** `You make SAR 30 a piece.` — or a nudge when the sums do not work. */
private fun marginNote(price: String, cost: String, currency: Currency, strings: Strings): String {
    val sell = Money.parse(price) ?: 0.0
    val buy = Money.parse(cost) ?: 0.0
    if (sell <= buy) return strings.setPriceAboveCost
    return strings.youMakeAPiece(Money.text(sell - buy, currency))
}

/**
 * Entering a catalogue: a name becomes a card, the card carries its three
 * numbers, and one Save writes the lot.
 *
 * The name box stays at the top rather than travelling to the end of the list,
 * so the rhythm is type-enter-type-enter without the thumb moving.
 */
@Composable
private fun NewProducts(
    drafts: List<ProductDraft>,
    draftName: String,
    onDraftName: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (ProductDraft) -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    currency: Currency,
    strings: Strings
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        NocturneField(
            value = draftName,
            onValueChange = onDraftName,
            placeholder = strings.productNameExample,
            height = Metrics.tallInputHeight,
            fontSize = 15.0,
            imeAction = ImeAction.Done,
            onImeAction = onAdd,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        PrimaryButton(
            title = "+",
            onClick = onAdd,
            enabled = draftName.isNotBlank(),
            height = Metrics.tallInputHeight
        )
    }

    Spacer(Modifier.height(Metrics.cardGap))
    Kicker(if (drafts.isEmpty()) strings.nothingAddedYetKicker else strings.addedCount(drafts.size))
    Spacer(Modifier.height(8.dp))

    drafts.forEach { draft ->
        DraftCard(draft, currency, strings, onRemove = { onRemove(draft) })
        Spacer(Modifier.height(8.dp))
    }

    if (drafts.isEmpty()) {
        Text(strings.openingStockNote, style = NocturneType.meta, color = Nocturne.neutral500)
        Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(6.dp))
    PrimaryButton(strings.save, onClick = onSave, enabled = canSave, fullWidth = true)
}

@Composable
private fun DraftCard(
    draft: ProductDraft,
    currency: Currency,
    strings: Strings,
    onRemove: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().card().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                draft.name,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                Icon.close,
                onClick = onRemove,
                size = 15.dp,
                tint = Nocturne.neutral500,
                contentDescription = strings.remove(draft.name)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            NocturneField(
                value = draft.stock,
                onValueChange = { draft.stock = it },
                label = strings.openingStock,
                numeric = true,
                isRequiredAndEmpty = draft.stock.isBlank(),
                // Marked once the box has been visited and left empty rather than
                // on arrival: a handful of cards is a dozen outlined boxes
                // otherwise, which reads as a dozen mistakes before the owner has
                // made one.
                requiredMarking = RequiredMarking.AFTER_TOUCH,
                height = Metrics.compactControlHeight,
                fontSize = 13.5,
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            NocturneField(
                value = draft.cost,
                onValueChange = { draft.cost = it },
                label = strings.buyingPrice,
                numeric = true,
                isRequiredAndEmpty = draft.cost.isBlank(),
                requiredMarking = RequiredMarking.AFTER_TOUCH,
                height = Metrics.compactControlHeight,
                fontSize = 13.5,
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            NocturneField(
                value = draft.price,
                onValueChange = { draft.price = it },
                label = strings.sellingPrice,
                numeric = true,
                isRequiredAndEmpty = (Money.parse(draft.price) ?: 0.0) <= 0,
                requiredMarking = RequiredMarking.AFTER_TOUCH,
                emphasis = FieldEmphasis.SELLING_PRICE,
                height = Metrics.compactControlHeight,
                fontSize = 13.5,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
