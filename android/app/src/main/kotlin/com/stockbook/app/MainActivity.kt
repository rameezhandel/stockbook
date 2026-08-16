package com.stockbook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stockbook.app.design.Nocturne
import com.stockbook.core.money.Money
import com.stockbook.core.store.JsonFileRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import java.io.File

/**
 * The one activity.
 *
 * The store is built here, over a file in the app's own directory, and handed
 * down. Nothing above this line knows what a repository is and nothing below it
 * knows what an Activity is.
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

/**
 * The tab shell and the four screens go here.
 *
 * Deliberately a stub: the domain underneath it is finished and covered by 66
 * tests that run without an emulator, and the screens are being built against a
 * toolchain proven to compile rather than written blind and hoped over.
 */
@Composable
private fun Shell(store: StockbookStore) {
    val state by store.state.collectAsStateWithLifecycle()
    val strings = Strings(state.settings.language)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nocturne.bg)
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Text(
            text = strings.today,
            color = Nocturne.text,
            fontSize = 28.sp
        )
        Text(
            text = strings.itemsSubtitle(
                total = state.products.size,
                low = state.products.count { it.isLow(state.settings.lowStockAt) }
            ),
            color = Nocturne.neutral500,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = Money.text(
                state.bills.filterNot { it.voided }.sumOf { it.total },
                state.settings.currency
            ),
            color = Nocturne.accent400,
            fontSize = 22.sp,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}
