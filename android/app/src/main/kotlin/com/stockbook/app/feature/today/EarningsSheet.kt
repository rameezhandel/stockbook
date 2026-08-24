package com.stockbook.app.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.FadedRule
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.EarningsDocument
import com.stockbook.core.text.Strings

/**
 * What the trading left the shop with, over the span Home was showing.
 *
 * **The owner's own page**, like the four summaries it sits beside — it says
 * what the shop makes, which is the last thing to hand across a counter.
 *
 * Everything drawn here comes out of [EarningsDocument], so the screen and the
 * printed page cannot describe the same month differently. The chain of figures
 * and the confession under it are both that document's decisions; this only
 * gives them weight.
 */
@Composable
fun EarningsSheet(
    period: StatementPeriod,
    state: ShopState,
    store: StockbookStore,
    strings: Strings,
    /** Renders this span as a page and hands it to the chooser. */
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    // Keyed on the whole state: `earningsIn` is a plain function over a
    // StateFlow snapshot, so a bill written while this is open would otherwise
    // leave the figures where they were when it was first drawn.
    val page = remember(period, state) {
        EarningsDocument.make(store.earningsIn(period), period.range(), state.settings, strings)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = page.title,
            subtitle = page.onDate,
            onClose = onClose,
            onShare = if (page.isEmpty) null else onSave
        )

        if (page.isEmpty) {
            Text(
                page.emptyLine,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .card()
                .hairline(radius = Metrics.cardRadius)
                .padding(14.dp)
        ) {
            page.lines.forEach { line -> FigureRow(line) }
        }

        // The confession, in a card of its own so it cannot be mistaken for part
        // of the arithmetic above it.
        if (page.hasGap) {
            Spacer(Modifier.height(Metrics.cardGap))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .card()
                    .hairline(radius = Metrics.cardRadius)
                    .padding(14.dp)
            ) {
                Kicker(page.gapHeading, modifier = Modifier.padding(bottom = 10.dp))
                page.gap.forEach { line ->
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Text(
                            line.label,
                            style = NocturneType.meta,
                            color = Nocturne.neutral500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(line.value, style = NocturneType.meta, color = Nocturne.text)
                    }
                }
                page.gapNote?.let {
                    Text(
                        it,
                        style = NocturneType.meta,
                        color = Nocturne.neutral500,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * One step of the arithmetic.
 *
 * A subtraction wears its minus and stays grey; a total gets a rule over it and
 * the weight of an answer. Without the two apart the column reads as six
 * unrelated figures rather than as a sum being worked.
 */
@Composable
private fun FigureRow(line: EarningsDocument.Line) {
    val isTotal = line.weight == EarningsDocument.Weight.TOTAL
    val isMinus = line.weight == EarningsDocument.Weight.MINUS

    if (isTotal) FadedRule(modifier = Modifier.padding(vertical = 8.dp), inset = 0.dp)

    Row(modifier = Modifier.fillMaxWidth().padding(bottom = if (isTotal) 0.dp else 6.dp)) {
        Text(
            line.label,
            style = if (isTotal) NocturneType.rowPrimary else NocturneType.meta,
            color = if (isTotal) Nocturne.text else Nocturne.neutral500,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (isMinus) "− ${line.value}" else line.value,
            style = if (isTotal) NocturneType.rowPrimary else NocturneType.meta,
            color = when {
                isTotal -> Nocturne.accent400
                isMinus -> Nocturne.neutral500
                else -> Nocturne.text
            },
            maxLines = 1
        )
    }
}
