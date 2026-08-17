package com.stockbook.app.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.EmptyStateBox
import androidx.compose.ui.graphics.vector.ImageVector
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.StatCard
import com.stockbook.app.design.dashedBox
import com.stockbook.app.feature.bills.BillRow
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import com.stockbook.core.text.firstName
import java.time.Instant

/**
 * The home screen: what sold today, who owes money, the last few bills, and a
 * standing reminder that nothing is backed up.
 */
@Composable
fun TodayScreen(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = state.settings.currency
    val liveBills = state.bills.filterNot { it.voided }
    val (owedNames, owedTotal) = store.outstanding()
    val (payableNames, payableTotal) = store.payable()
    val greetingName = state.settings.ownerName.firstName

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            kicker = strings.headerDate(Instant.now()),
            title = if (greetingName.isEmpty()) strings.today else strings.greeting(greetingName)
        ) {
            IconButton(
                Icon.settings,
                onClick = { router.showingSettings = true },
                contentDescription = strings.settings,
                tint = Nocturne.text
            )
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Metrics.screenPadding,
                end = Metrics.screenPadding,
                top = 4.dp,
                bottom = 18.dp
            )
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        label = strings.soldToday,
                        value = Money.text(liveBills.sumOf { it.total }, currency),
                        gradient = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Metrics.cardGap))
                    StatCard(
                        label = strings.billsStat,
                        value = liveBills.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Metrics.cardGap))
            }

            if (owedNames.isNotEmpty()) {
                item {
                    OwedBanner(
                        note = if (owedNames.size == 1) {
                            strings.stillOwes(owedNames.first())
                        } else {
                            strings.stillOwe(owedNames.size)
                        },
                        amount = Money.text(owedTotal, currency)
                    )
                    Spacer(Modifier.height(if (payableNames.isEmpty()) 18.dp else 6.dp))
                }
            }

            // The other direction, and only when there is one. A shop owner's own
            // bills matter as much as the ones owed to them, but a banner saying
            // "you owe nothing" every day teaches people to stop reading banners.
            if (payableNames.isNotEmpty()) {
                item {
                    OwedBanner(
                        note = if (payableNames.size == 1) {
                            strings.youOweOne(payableNames.first())
                        } else {
                            strings.youOweMany(payableNames.size)
                        },
                        amount = Money.text(payableTotal, currency),
                        icon = Icon.items
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)
                ) {
                    Kicker(strings.recentBills, modifier = Modifier.weight(1f))
                    GhostButton(strings.all, onClick = { router.tab = com.stockbook.core.text.AppTab.BOOK })
                }
            }

            if (state.bills.isEmpty()) {
                item {
                    EmptyStateBox(
                        message = strings.noBillsToday,
                        actionTitle = strings.startABill,
                        onAction = { router.startBill() }
                    )
                }
            } else {
                items(state.bills.take(3), key = { it.number }) { bill ->
                    BillRow(
                        bill = bill,
                        currency = currency,
                        strings = strings,
                        onClick = { router.openBill(bill) },
                        modifier = Modifier.padding(bottom = Metrics.rowGap)
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                BackupNudge(
                    hasBackup = state.settings.hasBackup,
                    note = if (state.settings.hasBackup) strings.backupWrittenNote else strings.backupMissingNote,
                    actionTitle = strings.saveFile,
                    onAction = onExport
                )
            }
        }
    }
}

@Composable
private fun OwedBanner(note: String, amount: String, icon: ImageVector = Icon.customer) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .owedBannerBackground()
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Glyph(icon, size = 19.dp, tint = Nocturne.accent400)
        Spacer(Modifier.width(10.dp))
        Text(
            note,
            style = NocturneType.inter(12.5),
            color = Nocturne.neutral400,
            modifier = Modifier.weight(1f)
        )
        Text(amount, style = NocturneType.inter(16.0), color = Nocturne.accent400)
    }
}

/**
 * The accent rule down the left edge is the banner's whole signature, so it is
 * drawn rather than approximated with a border.
 */
private fun Modifier.owedBannerBackground(): Modifier = drawBehind {
    drawRect(Nocturne.surface)
    drawRect(color = Nocturne.accent, size = Size(2.dp.toPx(), size.height))
}

@Composable
private fun BackupNudge(
    hasBackup: Boolean,
    note: String,
    actionTitle: String,
    onAction: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().dashedBox().padding(12.dp)
    ) {
        Glyph(
            if (hasBackup) Icon.confirm else Icon.edit,
            size = 20.dp,
            tint = if (hasBackup) Nocturne.accent else Nocturne.neutral500
        )
        Spacer(Modifier.width(11.dp))
        Text(
            note,
            style = NocturneType.inter(12.0),
            color = Nocturne.neutral500,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        SecondaryButton(actionTitle, onClick = onAction, height = 34.dp, fontSize = 12.5)
    }
}
