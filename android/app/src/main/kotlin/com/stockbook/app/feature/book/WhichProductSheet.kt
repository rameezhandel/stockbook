package com.stockbook.app.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.card
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.text.Strings

/**
 * Which product arrived.
 *
 * The one step a delivery needs that a bill does not: a purchase carries a single
 * product, so it has to be named before anything else can be typed. Searchable,
 * because a shop with two hundred lines cannot scroll to the one on the pallet.
 *
 * Choosing a row hands straight over to the purchase sheet — the owner never
 * returns here, which is why it closes rather than staying open behind.
 */
@Composable
fun WhichProductSheet(
    state: ShopState,
    router: AppRouter,
    strings: Strings,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val currency = state.settings.currency
    val matches = remember(state.products, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) state.products
        else state.products.filter { it.name.lowercase().contains(needle) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(title = strings.whichProductArrived, onClose = onClose)

        NocturneField(
            value = query,
            onValueChange = { query = it },
            placeholder = strings.search,
            fontSize = 14.5
        )
        Spacer(Modifier.height(10.dp))

        if (matches.isEmpty()) {
            EmptyStateBox(
                icon = Icon.items,
                message = if (state.products.isEmpty()) strings.shelfEmpty
                else strings.nothingMatches(query.trim())
            )
            return@Column
        }

        // Capped rather than filling the sheet: the list is the sheet's whole
        // content, and a bottom sheet that reaches the status bar reads as a
        // screen the owner has to find their way out of.
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            items(matches, key = { it.uid }) { product ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Metrics.rowGap)
                        .card(Metrics.controlRadius)
                        .clickable { router.openDelivery(product) }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Text(
                        product.name,
                        style = NocturneType.rowPrimary,
                        color = Nocturne.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        strings.stockLabel(product.stock),
                        style = NocturneType.meta,
                        color = Nocturne.neutral500
                    )
                    Spacer(Modifier.width(10.dp))
                    // The buying price, not the selling one: this list exists to
                    // start a purchase, and that is the figure about to be typed
                    // over.
                    Text(
                        Money.text(product.cost, currency),
                        style = NocturneType.inter(14.0),
                        color = Nocturne.accent400
                    )
                }
            }
        }
    }
}
