package com.stockbook.app.feature.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.EmptyStateBox
import com.stockbook.app.design.Icon
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import com.stockbook.app.design.DropdownField
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.hairline
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Supplier
import com.stockbook.app.design.Metrics
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

/** The catalogue: what is on the shelf, what it cost, what it sells for. */
@Composable
fun ItemsScreen(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val currency = state.settings.currency
    val lowStockAt = state.settings.lowStockAt
    val filtered = remember(state.products, query) { store.productsMatching(query) }

    val subtitle = if (state.products.isEmpty()) {
        strings.nothingAddedYet
    } else {
        strings.itemsSubtitle(
            total = state.products.size,
            low = state.products.count { it.isLow(lowStockAt) }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(title = strings.itemsTitle, subtitle = subtitle, bottomPadding = 10.dp) {
            // Recording a delivery used to be three taps down inside a product,
            // which is nobody's idea of discoverable. It starts here now and asks
            // which product arrived, rather than making the owner find it first.
            SecondaryButton(
                strings.recordDelivery,
                onClick = { router.recordingDelivery = true },
                height = Metrics.compactControlHeight,
                fontSize = 12.5,
                leading = Icon.addStock
            )
            Spacer(Modifier.width(6.dp))
            PrimaryButton(strings.add, onClick = { router.openNewProduct() }, compact = true, leading = Icon.add)
        }

        NocturneField(
            value = query,
            onValueChange = { query = it },
            placeholder = strings.search,
            fontSize = 14.5,
            modifier = Modifier
                .padding(horizontal = Metrics.screenPadding)
                .padding(bottom = 10.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = Metrics.screenPadding,
                end = Metrics.screenPadding,
                bottom = 18.dp
            )
        ) {
            if (filtered.isEmpty()) {
                item {
                    EmptyStateBox(
                        icon = Icon.items,
                        message = if (state.products.isEmpty()) {
                            strings.shelfEmpty
                        } else {
                            strings.nothingMatches(query.trim())
                        },
                        actionTitle = strings.addAProduct,
                        onAction = { router.openNewProduct() },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            items(filtered, key = { it.uid }) { product ->
                ProductRow(
                    product = product,
                    lowStockAt = lowStockAt,
                    currency = currency,
                    strings = strings,
                    onClick = { router.openProduct(product) },
                    modifier = Modifier.padding(bottom = Metrics.rowGap)
                )
            }
        }
    }
}

@Composable
private fun ProductRow(
    product: Product,
    lowStockAt: Int,
    currency: com.stockbook.core.model.Currency,
    strings: Strings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .card(Metrics.rowRadius)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                product.name,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                strings.buyAndMargin(
                    cost = Money.text(product.cost, currency),
                    margin = Money.text(product.marginPerPiece, currency)
                ),
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.text(product.price, currency), style = NocturneType.inter(15.0), color = Nocturne.text)
            Text(
                strings.stockLabel(product.stock),
                style = NocturneType.meta,
                // Out of stock and running low share a colour — both are "look
                // at me", and the design does not distinguish them.
                color = if (product.isLow(lowStockAt)) Nocturne.accent400 else Nocturne.neutral500,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
