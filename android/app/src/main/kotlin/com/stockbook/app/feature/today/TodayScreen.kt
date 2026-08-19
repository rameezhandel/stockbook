package com.stockbook.app.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.stockbook.app.design.StatCard
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import com.stockbook.core.text.firstName
import java.time.Instant

/**
 * The home screen: what the shop sold, what is owed each way, who owes money,
 * and what the shelf is running short of.
 */
@Composable
fun TodayScreen(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    val currency = state.settings.currency

    // Which span the sales card is showing. Local to this screen and not
    // remembered across launches: the useful answer on opening the app in the
    // morning is almost always this month, and a screen that came back showing
    // last March would be quietly lying about "Sold".
    var span by remember { mutableStateOf(Span.THIS_MONTH) }
    val period = remember(span, state.bills) { span.period() }
    val sold = remember(period, state.bills) { store.soldIn(period) }

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
                // What the shop turned over, over a span the owner picks. The two
                // cards below are balances and answer "where do I stand"; this
                // one answers "how did we do", which is a different question and
                // the only one on this screen with a period attached to it.
                SoldCard(
                    label = strings.soldInPeriod,
                    value = Money.text(sold, currency),
                    span = span,
                    strings = strings,
                    onChoose = { span = it }
                )
                Spacer(Modifier.height(Metrics.cardGap))

                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        label = strings.receivableStat,
                        value = Money.text(owedTotal, currency),
                        gradient = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Metrics.cardGap))
                    StatCard(
                        label = strings.payableStat,
                        value = Money.text(payableTotal, currency),
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
                        amount = Money.text(owedTotal, currency),
                        onClick = { router.showingDebtors = true }
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
                        icon = Icon.items,
                        onClick = { router.showingCreditors = true }
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }

            // What the shelf is short of, and a tap straight to restocking it.
            //
            // This used to be the three most recent bills — which is the Reports
            // tab, one tap away, on the screen the owner lands on. Nothing else in
            // the app volunteers that something is running out, and it is the one
            // thing here they can act on while standing at the counter.
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)
                ) {
                    Kicker(strings.runningLow, modifier = Modifier.weight(1f))
                    GhostButton(strings.all, onClick = { router.tab = com.stockbook.core.text.AppTab.ITEMS })
                }
            }

            val low = state.products
                .filter { it.isLow(state.settings.lowStockAt) }
                .sortedBy { it.stock }

            when {
                state.products.isEmpty() -> item {
                    EmptyStateBox(
                        message = strings.shelfEmpty,
                        actionTitle = strings.addAProduct,
                        onAction = { router.openNewProduct() }
                    )
                }

                low.isEmpty() -> item {
                    // Said rather than left blank. An empty space here reads as a
                    // section that failed to load; one line reads as good news.
                    Text(
                        strings.nothingRunningLow,
                        style = NocturneType.meta,
                        color = Nocturne.neutral500,
                        modifier = Modifier.padding(bottom = Metrics.rowGap)
                    )
                }

                // Emptiest first: the shelf closest to costing a sale.
                else -> items(low.take(4), key = { it.uid }) { product ->
                    LowStockRow(
                        product = product,
                        strings = strings,
                        onClick = { router.openAddStock(product) },
                        modifier = Modifier.padding(bottom = Metrics.rowGap)
                    )
                }
            }
        }
    }
}

/**
 * One product the shelf is short of, and a tap to put stock back on it.
 *
 * The count is drawn in the accent, not the neutral it wears on the Items
 * screen: everything in this list is by definition low, so the colour is the
 * point rather than a warning that some rows carry and others do not.
 */
@Composable
private fun LowStockRow(
    product: com.stockbook.core.model.Product,
    strings: Strings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .card()
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Text(
            product.name,
            style = NocturneType.rowPrimary,
            color = Nocturne.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            strings.stockLabel(product.stock),
            style = NocturneType.inter(13.0),
            color = Nocturne.accent400
        )
    }
}

/**
 * "Ahmed still owes", and the way to do something about it.
 *
 * The banner opens the list of everybody behind the figure, because noticing a
 * debt and collecting it were two unconnected halves of the app before: the
 * owner read this line and then went hunting through the Book for the name. The
 * chevron is what says the line is a door rather than a notice.
 */
@Composable
private fun OwedBanner(
    note: String,
    amount: String,
    onClick: () -> Unit,
    icon: ImageVector = Icon.customer
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .owedBannerBackground()
            .clickable(onClick = onClick)
            .padding(start = 13.dp, end = 9.dp, top = 12.dp, bottom = 12.dp)
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
        Spacer(Modifier.width(4.dp))
        Glyph(Icon.openRow, size = 12.dp, tint = Nocturne.neutral600)
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

/**
 * The three spans Home offers.
 *
 * The statement screen's first three chips, minus its custom range: picking two
 * dates is a job for the document you are about to send somebody, not for a
 * glance on the way past. The period arithmetic is [StatementPeriod]'s either
 * way, so "this month" means the same thing on both screens.
 */
private enum class Span(val label: (Strings) -> String) {
    THIS_MONTH({ it.thisMonth }),
    LAST_MONTH({ it.lastMonth }),
    THIS_YEAR({ it.thisYear });

    fun period(): StatementPeriod = when (this) {
        THIS_MONTH -> StatementPeriod.thisMonth()
        LAST_MONTH -> StatementPeriod.lastMonth()
        THIS_YEAR -> StatementPeriod.thisYear()
    }
}

@Composable
private fun SoldCard(
    label: String,
    value: String,
    span: Span,
    strings: Strings,
    onChoose: (Span) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().card().padding(14.dp)) {
        Text(label, style = NocturneType.inter(11.0), color = Nocturne.neutral500)
        Text(
            value,
            style = NocturneType.bigNumber(26.0),
            color = Nocturne.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp, bottom = 10.dp)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Span.entries.forEach { candidate ->
                SpanChip(
                    title = candidate.label(strings),
                    selected = candidate == span,
                    onClick = { onChoose(candidate) },
                    modifier = Modifier.weight(1f)
                )
                if (candidate != Span.entries.last()) Spacer(Modifier.width(6.dp))
            }
        }
    }
}

/** The statement screen's chip, at the size a card has room for. */
@Composable
private fun SpanChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        title,
        style = NocturneType.inter(11.5),
        color = if (selected) Nocturne.accent else Nocturne.neutral500,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(Metrics.controlRadius))
            .background(if (selected) Nocturne.primaryPressed else Color.Transparent)
            .hairline(
                if (selected) Nocturne.accent else Nocturne.divider,
                Metrics.controlRadius
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp)
    )
}
