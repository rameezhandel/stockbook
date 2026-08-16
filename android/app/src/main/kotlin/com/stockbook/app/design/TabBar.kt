package com.stockbook.app.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockbook.core.text.AppTab
import com.stockbook.core.text.Strings

/**
 * The four-tab bar. Hand-built rather than Material's `NavigationBar` because
 * the design specifies the exact metrics — surface ground, a single top hairline
 * and no elevation, 22dp icons over a 10.5sp label, filled icon plus accent on
 * the active tab. Settings is deliberately *not* a tab.
 */
@Composable
fun StockbookTabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().background(Nocturne.surface)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Metrics.hairline)
                .background(Nocturne.neutral800)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        ) {
            AppTab.entries.forEach { tab ->
                val active = tab == selected
                val tint by animateColorAsState(
                    if (active) Nocturne.accent else Nocturne.neutral500,
                    label = "tabTint"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 4.dp)
                ) {
                    Glyph(tab.icon(active), size = 22.dp, tint = tint)
                    Spacer(Modifier.height(3.dp))
                    Text(strings.tab(tab), style = NocturneType.tabLabel, color = tint)
                }
            }
        }
    }
}

private fun AppTab.icon(active: Boolean) = when (this) {
    AppTab.TODAY -> if (active) Icon.todayActive else Icon.today
    AppTab.ITEMS -> if (active) Icon.itemsActive else Icon.items
    AppTab.SELL -> if (active) Icon.sellActive else Icon.sell
    AppTab.BILLS -> if (active) Icon.billsActive else Icon.bills
}
