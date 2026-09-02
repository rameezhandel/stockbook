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
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.feature.bills.BillsScreen
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * The account book: every direction money moves.
 *
 * **Sales** is what was sold and to whom; **Purchases** is what arrived and from
 * whom. Those two are mirror images in the domain — one `Statement.make` serves
 * both — so they belong beside each other rather than in two tabs.
 *
 * **Expenses** is the odd one and sits here anyway. It is the owner's own
 * spending, tied to nobody, and it touches none of the arithmetic on the other
 * two chips. But it is money leaving, it is written down for the same reason the
 * others are, and the alternative was a fourth tab for something recorded once a
 * day — or Settings, which is where features go to be forgotten.
 *
 * Chips rather than tabs, because the shop does not use these symmetrically: a
 * sale happens fifty times a day, a delivery arrives once a week. A tab bar is
 * weighted by how often a thumb goes there, not by how tidy the model is.
 */
@Composable
fun BookScreen(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    /** Renders the spending on screen as a page and hands it to the chooser. */
    onSaveExpenses: (StatementPeriod) -> Unit,
    /**
     * Renders every customer's whole history as one document.
     *
     * A hundred statements is a hundred pages and a second or two of drawing, so
     * it is a deliberate tap rather than anything this screen does on its own.
     */
    onSaveLedgerBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    /**
     * Which half is showing. `rememberSaveable` so it survives a rotation and,
     * more usefully, a trip into a sheet and back — an owner who came here for
     * suppliers should not be handed bills again on the way back.
     */
    var side by rememberSaveable { mutableStateOf(Side.SALES) }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            title = strings.bookTitle,
            bottomPadding = 10.dp,
            // Every customer's position on one day. It belongs on this tab
            // rather than beside the day's transactions on Home: it is a list of
            // people, read down, and this is the screen the owner comes to when
            // the question is about people rather than about today.
            trailing = {
                // Every customer's whole history, printed once and filed. The
                // one thing this screen hands to a printer, so it lives here
                // rather than in Settings, which is where features go to be
                // forgotten.
                IconButton(
                    Icon.bills,
                    onClick = onSaveLedgerBook,
                    contentDescription = strings.ledgerBook,
                    tint = Nocturne.neutral400
                )
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.screenPadding)
                .padding(bottom = 10.dp)
        ) {
            ChoicePill(
                title = strings.salesSide,
                icon = Icon.bills,
                selected = side == Side.SALES,
                onClick = { side = Side.SALES },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            ChoicePill(
                title = strings.purchasesSide,
                icon = Icon.items,
                selected = side == Side.PURCHASES,
                onClick = { side = Side.PURCHASES },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            ChoicePill(
                title = strings.expensesTitle,
                icon = Icon.expenses,
                selected = side == Side.EXPENSES,
                onClick = { side = Side.EXPENSES },
                modifier = Modifier.weight(1f)
            )
        }

        // No `else` on purpose: a fourth side has to break this and be placed
        // deliberately, not fall through to whichever branch was last.
        when (side) {
            // The Bills screen exactly as it was, minus the header this one now
            // carries. Nothing about sales moved; it gained neighbours.
            //
            // Weighted, not wrapped. A `Column` measures an unweighted child
            // against the *full* remaining height, so the list inside would have
            // been given the whole screen and run off the bottom by exactly the
            // height of the header and chips above it.
            Side.SALES -> BillsScreen(
                state = state,
                store = store,
                router = router,
                strings = strings,
                showHeader = false,
                modifier = Modifier.weight(1f)
            )

            Side.PURCHASES -> PurchasesPane(
                state = state,
                store = store,
                router = router,
                strings = strings,
                modifier = Modifier.weight(1f)
            )

            Side.EXPENSES -> ExpensesPane(
                state = state,
                store = store,
                router = router,
                strings = strings,
                onSave = onSaveExpenses,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Which chip is on. Saved across a trip into a sheet and back. */
private enum class Side { SALES, PURCHASES, EXPENSES }
