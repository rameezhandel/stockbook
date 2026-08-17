package com.stockbook.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.stockbook.app.BuildConfig
import com.stockbook.app.design.DropdownField
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.card
import com.stockbook.core.model.Currency
import com.stockbook.core.model.ShopState
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.Strings

/**
 * Settings: the shop's name, the two things it reads and bills in, and a way
 * through to the backup handoff.
 *
 * Short on purpose. The two controls an owner touches occasionally sit above a
 * single row for the two they use once a year.
 */
@Composable
fun SettingsScreen(
    state: ShopState,
    store: StockbookStore,
    strings: Strings,
    onClose: () -> Unit,
    onOpenBackup: () -> Unit,
    onStartOver: () -> Unit
) {
    var ownerName by remember(state.settings.ownerName) { mutableStateOf(state.settings.ownerName) }

    // Drawn as a sibling of the tab content inside the shell's Box, so without a
    // ground of its own the screen behind shows straight through it — and without
    // `statusBarsPadding` its header sits under the status bar.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nocturne.bg)
            .statusBarsPadding()
            .imePadding()
    ) {
        ScreenHeader(title = strings.settings) {
            GhostButton(strings.done, onClick = onClose)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.screenPadding)
                .padding(bottom = 18.dp)
        ) {
            Kicker(strings.thisPhone, modifier = Modifier.padding(bottom = 8.dp))
            Column(modifier = Modifier.fillMaxWidth().card().padding(12.dp)) {
                NocturneField(
                    value = ownerName,
                    onValueChange = {
                        ownerName = it
                        store.setOwnerName(it)
                    },
                    label = strings.businessOwner,
                    placeholder = strings.businessOwnerName
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Stat(strings.productsStat, state.products.size, Modifier.weight(1f))
                    Stat(strings.billsStat, state.bills.count { !it.voided }, Modifier.weight(1f))
                    Stat(strings.customersStat, store.customers().size, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(20.dp))
            Kicker(strings.languageAndCurrency, modifier = Modifier.padding(bottom = 8.dp))
            Column(modifier = Modifier.fillMaxWidth().card().padding(12.dp)) {
                DropdownField(
                    options = AppLanguage.entries.toList(),
                    selected = state.settings.language,
                    onSelect = { store.setLanguage(it) },
                    title = { it.endonym },
                    mark = { it.code.uppercase() },
                    label = strings.languageSection
                )
                Spacer(Modifier.height(10.dp))
                DropdownField(
                    options = Currency.supported,
                    selected = state.settings.currency,
                    onSelect = { store.setCurrency(it) },
                    title = { strings.currencyName(it) },
                    rowTitle = { strings.currencyRow(it) },
                    mark = { it.symbol.trim() },
                    label = strings.currencySection
                )
            }
            // The one caveat that cannot be discovered by trying it: the numbers
            // do not move when the symbol does.
            Text(
                strings.currencyNote,
                style = NocturneType.inter(12.0),
                color = Nocturne.neutral500,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(20.dp))
            // A row, not a section. The subtitle carries the backup state,
            // because the standing reminder has to survive being folded away.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .card()
                    .clickable(onClick = onOpenBackup)
                    .padding(13.dp)
            ) {
                Glyph(
                    if (state.settings.hasBackup) Icon.confirm else Icon.edit,
                    size = 20.dp,
                    tint = if (state.settings.hasBackup) Nocturne.accent else Nocturne.accent400
                )
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.moveToAnotherPhone, style = NocturneType.rowPrimary, color = Nocturne.text)
                    Text(
                        state.settings.lastExportAt?.let { strings.backedUpOn(strings.longDate(it)) }
                            ?: strings.notBackedUpYet,
                        style = NocturneType.meta,
                        color = Nocturne.neutral500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Glyph(Icon.openRow, size = 12.dp, tint = Nocturne.neutral600)
            }

            // Debug builds only. One tap, no confirmation, and every product,
            // price and bill is gone — right for resetting to first-run during
            // development, wrong on a counter holding the only copy of a shop.
            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(20.dp))
                Kicker(strings.startAgain, modifier = Modifier.padding(bottom = 8.dp))
                SecondaryButton(
                    strings.startOver,
                    onClick = onStartOver,
                    fullWidth = true,
                    height = 42.dp,
                    fontSize = 13.5
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = NocturneType.inter(11.0), color = Nocturne.neutral500)
        Text("$value", style = NocturneType.inter(17.0), color = Nocturne.text)
    }
}
