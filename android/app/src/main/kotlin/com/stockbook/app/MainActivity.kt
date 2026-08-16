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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Motion
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.StockbookTabBar
import com.stockbook.app.feature.bills.BillsScreen
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
 * The keyboard never moves the layout: `windowSoftInputMode="adjustNothing"` in
 * the manifest says so once, for the whole app. The iOS build arrived at the
 * same rule the long way round, one screen at a time.
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
    val state by store.state.collectAsStateWithLifecycle()
    val router = remember { AppRouter() }
    val strings = remember(state.settings.language) { Strings(state.settings.language) }

    Box(modifier = Modifier.fillMaxSize().background(Nocturne.bg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
                        onExport = { /* wired with the backup screen */ }
                    )
                    AppTab.ITEMS -> Placeholder("Items")
                    AppTab.SELL -> Placeholder("Sell")
                    AppTab.BILLS -> BillsScreen(
                        state = state,
                        store = store,
                        router = router,
                        strings = strings
                    )
                }
            }

            StockbookTabBar(
                selected = router.tab,
                onSelect = { router.tab = it },
                strings = strings
            )
        }
    }
}

@Composable
private fun Placeholder(name: String) {
    androidx.compose.material3.Text(
        text = name,
        color = Nocturne.neutral500,
        modifier = Modifier.padding(Metrics.screenPadding)
    )
}
