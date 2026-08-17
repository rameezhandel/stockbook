package com.stockbook.app.feature.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.ChoicePill
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.feature.bills.BillsScreen
import com.stockbook.core.model.ShopState
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * The account book, both halves of it.
 *
 * **Sales** is what was sold and to whom; **Purchases** is what arrived and from
 * whom. The two are mirror images in the domain — one `Statement.make` serves
 * both — so they belong beside each other rather than in two tabs.
 *
 * Two chips rather than two tabs, because the shop does not use the halves
 * symmetrically: a sale happens fifty times a day and a delivery arrives once a
 * week. A tab bar is weighted by how often a thumb goes there, not by how tidy
 * the model is.
 */
@Composable
fun BookScreen(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    /**
     * Which half is showing. `rememberSaveable` so it survives a rotation and,
     * more usefully, a trip into a sheet and back — an owner who came here for
     * suppliers should not be handed bills again on the way back.
     */
    var showingSales by rememberSaveable { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(title = strings.bookTitle, bottomPadding = 10.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.screenPadding)
                .padding(bottom = 10.dp)
        ) {
            ChoicePill(
                title = strings.salesSide,
                icon = Icon.bills,
                selected = showingSales,
                onClick = { showingSales = true },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            ChoicePill(
                title = strings.purchasesSide,
                icon = Icon.items,
                selected = !showingSales,
                onClick = { showingSales = false },
                modifier = Modifier.weight(1f)
            )
        }

        if (showingSales) {
            // The Bills screen exactly as it was, minus the header this one now
            // carries. Nothing about sales moved; it gained a neighbour.
            BillsScreen(
                state = state,
                store = store,
                router = router,
                strings = strings,
                showHeader = false
            )
        } else {
            PurchasesPane(
                state = state,
                store = store,
                router = router,
                strings = strings
            )
        }
    }
}
