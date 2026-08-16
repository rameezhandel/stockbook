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
 * The billing flow: a product picker and a cart, sharing one search field and
 * one header.
 *
 * Which of the two is showing is **derived, never stored as a mode** — the
 * picker appears when the cart is empty, when there is text in the search box,
 * or when "Add another item" was tapped. Anything that empties all three
 * conditions drops you back to the cart, so there is no state to get stranded in.
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
    /** Set by "Add another item" — the one case where the picker shows over a full cart. */
    var browsing by remember { mutableStateOf(false) }

    val currency = state.settings.currency
    val showsPicker = cart.isEmpty || query.isNotBlank() || browsing
    val matches = remember(state.products, query) { store.productsMatching(query) }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(title = strings.newBill, bottomPadding = 10.dp) {
            Text(
                if (cart.isEmpty) strings.cartEmpty else strings.lines(cart.lines.size),
                style = NocturneType.inter(12.0),
                color = Nocturne.neutral500
            )
        }

        if (showsPicker && state.products.isNotEmpty()) {
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

        // Shared between both states: in the cart it sits empty, and typing into
        // it is what re-opens the picker.
        NocturneField(
            value = query,
            onValueChange = { query = it },
            placeholder = strings.addAProductPlaceholder,
            fontSize = 14.5,
            modifier = Modifier
                .padding(horizontal = Metrics.screenPadding)
                .padding(bottom = 10.dp)
        )

        Crossfade(targetState = showsPicker, animationSpec = Motion.screenSpec, label = "sell") { picking ->
            if (picking) {
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
                        browsing = false
                        query = ""
                    }
                )
            } else {
                CartView(
                    cart = cart,
                    state = state,
                    currency = currency,
                    strings = strings,
                    onBrowse = {
                        browsing = true
                        query = ""
                    },
                    onSave = {
                        val bill = store.saveBill(cart.draftLines, cart.customer, cart.paidForStorage)
                        if (bill != null) {
                            cart.clear()
                            browsing = false
                            query = ""
                            router.receipt = bill
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
    onDoneAdding: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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

        // With a cart in progress the tab bar is hidden, so this footer is the
        // only way back to it.
        if (!cart.isEmpty) {
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
                        Text(strings.lines(cart.lines.size), style = NocturneType.meta, color = Nocturne.neutral500)
                        Text(
                            Money.text(cart.total, currency),
                            style = NocturneType.bigNumber(19.0),
                            color = Nocturne.text
                        )
                    }
                    PrimaryButton(strings.doneAdding, onClick = onDoneAdding, height = 44.dp)
                }
            }
        }
    }
}
