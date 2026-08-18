package com.stockbook.app.feature.sell

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.EmptyStateBox
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Motion
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.design.card
import com.stockbook.core.model.Product
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * The billing flow: the bill form, with the product picker one tap behind it.
 *
 * The form is home now, not the picker. A bill in this shop is a number, a date,
 * somebody and a figure — the picker exists for the minority of bills that also
 * say what was sold, so it is reached from "Add items" and left again by "Done
 * adding". The form never disappears from under an owner who has typed into it:
 * everything they entered is still there when they come back.
 *
 * It is also where a bill gets **corrected**. `router.editingBill` is what says
 * so, and the form is the same one either way — a second screen for editing is a
 * second screen to keep in step with this one, and it would lose that race.
 */
@Composable
fun SellScreen(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    cart: Cart,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    // Set by "Add items", cleared by "Done adding". On the router rather than in
    // here because the shell draws the tab bar and must not stack it under the
    // picker's own bottom bar.
    val browsing = router.pickingProducts
    // Null for a new bill. The cart was filled from this one before the tab
    // changed, so everything below draws it without knowing where it came from.
    val editing = router.editingBill

    // Leaving Sell puts the picker away. It used to happen for free, when this
    // was a `remember` that died with the screen.
    DisposableEffect(Unit) { onDispose { router.pickingProducts = false } }

    val currency = state.settings.currency
    // An empty cart no longer means the picker: a bill with nothing on it is the
    // ordinary bill here, and dropping the owner into a product list to write one
    // would be the app asking a question the paper already answered.
    val showsPicker = browsing || query.isNotBlank()
    val matches = remember(state.products, query) { store.productsMatching(query) }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            // Which bill is being corrected, said in the heading: the form below
            // is identical to the one for a new bill, so this line is the whole
            // of what tells the owner they are changing 1024 rather than writing
            // 1025.
            kicker = if (editing != null) strings.editBill else null,
            title = editing?.reference(strings) ?: strings.newBill,
            bottomPadding = 10.dp
        ) {
            if (editing != null) {
                // The way out of a correction without making one. Without it an
                // owner who tapped Edit by mistake has no way back to a blank
                // form except by saving the bill they did not mean to touch.
                GhostButton(
                    strings.cancel,
                    onClick = {
                        cart.clear()
                        router.closeBillEditing()
                        query = ""
                    },
                    tint = Nocturne.neutral500
                )
            } else if (!cart.isEmpty) {
                // Only where there is a count to give. "Empty" beside a form with
                // a customer and a figure already in it describes the item list
                // and reads as a description of the bill.
                Text(
                    strings.lines(cart.lines.size),
                    style = NocturneType.inter(12.0),
                    color = Nocturne.neutral500
                )
            }
        }

        Crossfade(targetState = showsPicker, animationSpec = Motion.screenSpec, label = "sell") { picking ->
            if (picking) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.products.isNotEmpty()) {
                        Text(
                            if (query.isNotBlank()) strings.matchingQuery(query.trim())
                            else strings.allProductsHint(state.products.size),
                            style = NocturneType.meta,
                            color = Nocturne.neutral500,
                            modifier = Modifier
                                .padding(horizontal = Metrics.screenPadding)
                                .padding(bottom = 8.dp)
                        )
                    }

                    // The search box belongs to the picker rather than to the
                    // screen: a form for typing a total should not open with a
                    // box asking for a product name.
                    NocturneField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = strings.addAProductPlaceholder,
                        fontSize = 14.5,
                        modifier = Modifier
                            .padding(horizontal = Metrics.screenPadding)
                            .padding(bottom = 10.dp)
                    )

                    ProductPicker(
                        products = matches,
                        hasAnyProducts = state.products.isNotEmpty(),
                        query = query.trim(),
                        cart = cart,
                        currency = currency,
                        strings = strings,
                        onPick = { product ->
                            cart.add(product)
                            query = ""
                        },
                        onAddProduct = { router.openNewProduct() },
                        onDoneAdding = {
                            router.pickingProducts = false
                            query = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                CartView(
                    cart = cart,
                    state = state,
                    store = store,
                    currency = currency,
                    strings = strings,
                    onBrowse = {
                        router.pickingProducts = true
                        query = ""
                    },
                    editing = editing,
                    onSave = {
                        // The same figures either way, into whichever of the two
                        // this is. `updateBill` moves the shelf by the difference
                        // between the old bill and the new one, which is why an
                        // edit is one call rather than a removal and a save.
                        val bill = if (editing != null) {
                            store.updateBill(
                                number = editing.number,
                                lines = cart.draftLines,
                                customer = cart.customer,
                                paid = cart.paidForStorage,
                                amount = cart.typedAmount,
                                createdAt = cart.soldAt,
                                invoiceNo = cart.invoiceNo
                            )
                        } else {
                            store.saveBill(
                                lines = cart.draftLines,
                                customer = cart.customer,
                                paid = cart.paidForStorage,
                                // Passed whether or not there are lines. The store
                                // ignores it when there are — one rule, in one
                                // place, rather than a screen deciding which
                                // figure is real.
                                amount = cart.typedAmount,
                                createdAt = cart.soldAt,
                                invoiceNo = cart.invoiceNo
                            )
                        }
                        if (bill != null) {
                            cart.clear()
                            query = ""
                            if (editing != null) {
                                // Back to the list the bill was opened from, where
                                // the corrected row is the confirmation. A receipt
                                // here would announce a sale that did not happen.
                                router.closeBillEditing()
                            } else {
                                router.pickingProducts = false
                                router.receipt = bill
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * The browse-and-search list. Tapping a row adds one piece at the product's
 * current selling price; tapping one already on the bill increments it.
 */
@Composable
private fun ProductPicker(
    products: List<Product>,
    hasAnyProducts: Boolean,
    query: String,
    cart: Cart,
    currency: com.stockbook.core.model.Currency,
    strings: Strings,
    onPick: (Product) -> Unit,
    onAddProduct: () -> Unit,
    onDoneAdding: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Metrics.screenPadding,
                end = Metrics.screenPadding,
                bottom = 18.dp
            )
        ) {
            if (products.isEmpty()) {
                item {
                    EmptyStateBox(
                        message = if (hasAnyProducts) strings.noProductMatches(query) else strings.noProductsYet,
                        actionTitle = strings.addAProduct,
                        onAction = onAddProduct,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            items(products, key = { it.uid }) { product ->
                val onBill = cart.quantity(product.uid)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp)
                        .card(Metrics.controlRadius)
                        .clickable { onPick(product) }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Text(
                        product.name,
                        style = NocturneType.inter(14.0),
                        color = Nocturne.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // The list does not move when a row is tapped, so this mark
                    // is the whole of the feedback.
                    if (onBill > 0) {
                        Glyph(Icon.confirm, size = 11.dp, tint = Nocturne.accent)
                        Spacer(Modifier.width(3.dp))
                        Text("$onBill", style = NocturneType.inter(11.5), color = Nocturne.accent)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        strings.stockLabel(product.stock),
                        style = NocturneType.meta,
                        color = Nocturne.neutral500
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Money.text(product.price, currency),
                        style = NocturneType.inter(14.0),
                        color = Nocturne.accent400
                    )
                }
            }
        }

        // Always, not only with something on the bill: the picker is a place the
        // owner stepped into from the form, and an owner who steps in and changes
        // their mind must have the same way out as one who added six things.
        Column(modifier = Modifier.fillMaxWidth().background(Nocturne.surface)) {
            Box(Modifier.fillMaxWidth().height(Metrics.hairline).background(Nocturne.neutral800))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.screenPadding, vertical = 10.dp)
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (cart.isEmpty) strings.cartEmpty else strings.lines(cart.lines.size),
                        style = NocturneType.meta,
                        color = Nocturne.neutral500
                    )
                    // The line sum, and only where there are lines to sum. With
                    // none picked yet the figure here would be whatever was typed
                    // into the amount box, sitting under the word "empty".
                    if (!cart.isEmpty) {
                        Text(
                            Money.text(cart.total, currency),
                            style = NocturneType.bigNumber(19.0),
                            color = Nocturne.text
                        )
                    }
                }
                PrimaryButton(strings.doneAdding, onClick = onDoneAdding, height = 44.dp)
            }
        }
    }
}
