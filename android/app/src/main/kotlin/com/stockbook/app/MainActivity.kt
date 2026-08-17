package com.stockbook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stockbook.app.design.Motion
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.StockbookTabBar
import com.stockbook.app.design.BottomSheet
import com.stockbook.app.feature.bills.BillSheet
import com.stockbook.app.feature.bills.BillsScreen
import com.stockbook.app.feature.customers.CustomerEditorSheet
import com.stockbook.app.feature.customers.RecordPaymentSheet
import com.stockbook.app.feature.customers.StatementScreen
import com.stockbook.app.feature.items.AddStockSheet
import com.stockbook.app.feature.items.ItemsScreen
import com.stockbook.app.feature.items.ProductEditorSheet
import com.stockbook.app.feature.sell.Cart
import com.stockbook.app.feature.sell.ReceiptOverlay
import com.stockbook.app.feature.sell.SellScreen
import com.stockbook.app.feature.settings.BackupScreen
import com.stockbook.app.feature.settings.SettingsScreen
import com.stockbook.app.feature.setup.SetupFlow
import com.stockbook.app.feature.today.TodayScreen
import com.stockbook.core.store.JsonFileRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppTab
import com.stockbook.core.text.Strings
import java.io.File

/**
 * The one activity.
 *
 * The store is built here, over a file in the app's own directory, and handed
 * down. Nothing above this line knows what a repository is, and nothing below it
 * knows what an Activity is.
 *
 * The keyboard **does** move the layout, via `adjustResize` and `imePadding`.
 * An earlier version copied the iOS rule that the keyboard only overlays — which
 * holds on iOS because the system scrolls a focused field into view by itself.
 * Android does not, so that rule left fields sitting under the keyboard with no
 * way to reach them.
 */
class MainActivity : ComponentActivity() {

    private lateinit var store: StockbookStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A repository that cannot open its file is unrecoverable — there is no
        // server to fall back to — so this fails loudly rather than running
        // against a store the owner would type a day's bills into and lose.
        store = StockbookStore(JsonFileRepository(File(filesDir, "stockbook/shop.json")))

        setContent { Shell(store) }
    }
}

@Composable
private fun Shell(store: StockbookStore) {
    val context = LocalContext.current
    val state by store.state.collectAsStateWithLifecycle()
    val router = remember { AppRouter() }
    val cart = remember { Cart() }
    val strings = remember(state.settings.language) { Strings(state.settings.language) }

    // Setup is persisted state, not a route: `setupCompleted` decides which of
    // the two this is, which is also why "Start over" is a data operation.
    if (!state.settings.setupCompleted) {
        SetupFlow(store = store, strings = strings)
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Nocturne.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // Lifts the tab screens — the Sell cart's footer above all, which
                // carries the customer field — clear of the keyboard.
                .imePadding()
        ) {
            Crossfade(
                targetState = router.tab,
                animationSpec = Motion.screenSpec,
                label = "tab",
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    AppTab.TODAY -> TodayScreen(
                        state = state,
                        store = store,
                        router = router,
                        strings = strings,
                        onExport = { router.showingBackup = true }
                    )
                    AppTab.ITEMS -> ItemsScreen(
                        state = state,
                        store = store,
                        router = router,
                        strings = strings
                    )
                    AppTab.SELL -> SellScreen(
                        state = state,
                        store = store,
                        router = router,
                        cart = cart,
                        strings = strings
                    )
                    AppTab.BILLS -> BillsScreen(
                        state = state,
                        store = store,
                        router = router,
                        strings = strings
                    )
                }
            }

            if (router.tab != AppTab.SELL || cart.isEmpty) {
                StockbookTabBar(
                    selected = router.tab,
                    onSelect = { router.tab = it },
                    strings = strings
                )
            }
        }

        if (router.showingSettings) {
            SettingsScreen(
                state = state,
                store = store,
                strings = strings,
                onClose = { router.showingSettings = false },
                onOpenBackup = { router.showingBackup = true },
                onStartOver = {
                    store.startOver()
                    cart.clear()
                    router.closeOverlays()
                    router.showingSettings = false
                    router.tab = AppTab.TODAY
                }
            )
        }

        if (router.showingBackup) {
            BackupScreen(
                state = state,
                store = store,
                strings = strings,
                onClose = { router.showingBackup = false }
            )
        }

        // A document rather than a sheet: it runs to a page, and it is the one
        // screen here the owner may turn round and show a customer.
        router.statementFor?.let { key ->
            StatementScreen(
                customerKey = key,
                store = store,
                currency = state.settings.currency,
                strings = strings,
                onShare = { text -> shareText(context, text) },
                onClose = { router.statementFor = null }
            )
        }

        router.receipt?.let { bill ->
            ReceiptOverlay(
                bill = bill,
                state = state,
                strings = strings,
                onSeeBills = {
                    router.receipt = null
                    router.tab = AppTab.BILLS
                },
                onNextCustomer = {
                    router.receipt = null
                    router.tab = AppTab.SELL
                }
            )
        }

        // Overlays sit above every screen, which is the whole of this app's
        // navigation — there is no drill-down and no back stack anywhere.
        BottomSheet(
            visible = router.creatingProduct || router.productEditor != null,
            onDismiss = { router.creatingProduct = false; router.productEditor = null }
        ) {
            ProductEditorSheet(
                product = router.productEditor,
                store = store,
                currency = state.settings.currency,
                strings = strings,
                onClose = { router.creatingProduct = false; router.productEditor = null },
                onAddStock = { router.openAddStock(it) },
                onDeleted = { }
            )
        }

        BottomSheet(
            visible = router.addStock != null,
            onDismiss = { router.addStock = null }
        ) {
            router.addStock?.let { product ->
                AddStockSheet(
                    product = product,
                    store = store,
                    currency = state.settings.currency,
                    strings = strings,
                    onClose = { router.addStock = null }
                )
            }
        }

        BottomSheet(
            visible = router.creatingCustomer || router.customerEditor != null,
            onDismiss = { router.closeCustomerEditor() }
        ) {
            CustomerEditorSheet(
                existing = router.customerEditor,
                store = store,
                currency = state.settings.currency,
                strings = strings,
                onClose = { router.closeCustomerEditor() }
            )
        }

        BottomSheet(
            visible = router.paymentFor != null,
            onDismiss = { router.paymentFor = null }
        ) {
            router.paymentFor?.let { customer ->
                RecordPaymentSheet(
                    // Re-read from the store so the sheet's "what will be left"
                    // line is not a stale copy taken when it opened.
                    customer = store.customer(customer.key) ?: customer,
                    store = store,
                    currency = state.settings.currency,
                    strings = strings,
                    onClose = { router.paymentFor = null }
                )
            }
        }

        BottomSheet(
            visible = router.billDetail != null,
            onDismiss = { router.billDetail = null }
        ) {
            router.billDetail?.let { bill ->
                BillSheet(
                    bill = bill,
                    state = state,
                    store = store,
                    strings = strings,
                    onClose = { router.billDetail = null }
                )
            }
        }
    }
}

/**
 * Hands text to whatever the owner picks — a message, a note, a printer app.
 *
 * `ACTION_SEND` is a hand-off, not a network call: this app opens no socket and
 * has no permission to. What happens next belongs to the app the owner chose.
 */
private fun shareText(context: android.content.Context, text: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, null))
}
