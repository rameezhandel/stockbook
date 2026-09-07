package com.stockbook.app.feature.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.FadedRule
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import com.stockbook.core.text.SummaryDocument
import java.time.Instant
import java.time.ZoneId

/**
 * Where a month's money went, folded by what it went on.
 *
 * **The other question about the same expenses.** The list behind this sheet
 * answers "what did I spend", one receipt a line, over whatever span the picker
 * says. This answers "where did the month go" — and the two are not the same
 * page in two styles: a register is checked against the receipts in a drawer
 * line by line, and a folded page cannot be checked against anything. Which is
 * why the wording keeps them apart, `Report` against `Summary`, everywhere they
 * are named.
 *
 * **A month at a time, and only a month.** The book's own picker offers a year
 * and a hand-picked stretch as well; neither belongs here. "Petrol, 84 times" is
 * a fact about a year that tells the owner nothing they can act on, and the
 * whole use of this page is comparing one month against the last — which is what
 * the two arrows are for.
 *
 * Everything drawn comes out of [SummaryDocument], so what is read on the screen
 * and what comes out of the printer cannot drift.
 */
@Composable
fun ExpenseSummarySheet(
    /** Any instant inside the month being folded. */
    month: Instant,
    state: ShopState,
    store: StockbookStore,
    strings: Strings,
    /** Steps to another month without closing the sheet. */
    onMonth: (Instant) -> Unit,
    /** Renders this month as a page and hands it to the chooser. */
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    // Keyed on the whole state, not read off the store bare: every getter here is
    // a plain function over a StateFlow snapshot, so an expense written while
    // this is open would otherwise leave the page showing the figures it had when
    // it was first drawn.
    val page = remember(month, state) {
        SummaryDocument.forSpendingSummary(
            store.spendingIn(StatementPeriod.Month(month)), month, state.settings, strings
        )
    }

    // No stepping into next month. A month that has not started has nothing in
    // it, and an owner who lands there wonders whether the app has lost their
    // records rather than that they walked past the end of the year.
    val isThisMonth = remember(month) {
        // Compared as calendar months in the phone's own zone, which is the zone
        // `StatementPeriod.Month` resolves in. Comparing the instants instead
        // would compare them in UTC, and an owner in Riyadh looking at their
        // spending at half past two on the morning of the 1st would find next
        // month already reachable.
        val zone = ZoneId.systemDefault()
        val here = month.atZone(zone).toLocalDate().withDayOfMonth(1)
        !here.isBefore(Instant.now().atZone(zone).toLocalDate().withDayOfMonth(1))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = page.title,
            onClose = onClose,
            // Only where there is a month to hand over. A page saying nothing
            // happened is a page nobody needs a copy of.
            onShare = if (page.isEmpty) null else onSave
        )

        // `‹ August 2026 ›` — the month reads as the thing being stepped through
        // rather than as a caption with two buttons parked beside it. The same
        // shape the day summary steps days with, because it is the same gesture.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            IconButton(
                Icon.stepBack,
                onClick = { onMonth(month.stepMonth(-1)) },
                contentDescription = strings.previousMonth,
                tint = Nocturne.accent
            )
            Text(
                page.asOf,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (isThisMonth) {
                // Holds the month in the middle when there is no forward arrow to
                // draw. Without it this month's name sits off to the right and
                // every step back nudges it, which reads as a bug.
                Spacer(Modifier.size(Metrics.minimumTouchTarget))
            } else {
                IconButton(
                    Icon.stepForward,
                    onClick = { onMonth(month.stepMonth(1)) },
                    contentDescription = strings.nextMonth,
                    tint = Nocturne.accent
                )
            }
        }

        if (page.isEmpty) {
            Text(
                page.emptyLine,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            return@Column
        }

        // A plain Column rather than a lazy list: the sheet already scrolls its
        // content, and a lazy list given an unbounded height measures to zero and
        // draws no rows at all. A shop with more things to spend on than fit here
        // has a bigger problem than the scroll.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .card()
                .hairline(radius = Metrics.cardRadius)
                .padding(14.dp)
        ) {
            page.rows.forEachIndexed { index, row ->
                SpendRow(row)
                if (index < page.rows.lastIndex) Spacer(Modifier.height(Metrics.rowGap))
            }

            // Inside the card it totals, under a rule — and it is the same figure
            // the Expense card on the pane shows for the same month, which is
            // what `SummaryDocumentTests` pins.
            FadedRule(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp), inset = 0.dp)
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    page.totalLabel,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    modifier = Modifier.weight(1f)
                )
                Text(page.totalValue, style = NocturneType.rowPrimary, color = Nocturne.text)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * One thing the money went on, and how often.
 *
 * The count sits under the name rather than in a column of its own: a phone row
 * has room for a name and a figure, and squeezing a third column between them
 * takes the room from the one thing on the row that has to stay readable.
 */
@Composable
private fun SpendRow(row: SummaryDocument.Row) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.name,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            row.count?.let {
                Text(it, style = NocturneType.meta, color = Nocturne.neutral500)
            }
        }
        Spacer(Modifier.size(10.dp))
        Text(row.amount, style = NocturneType.rowPrimary, color = Nocturne.text)
    }
}

/**
 * One month back or forward, as a calendar reads it.
 *
 * Through `LocalDate` and re-anchored at midday, for the two reasons the day
 * stepper does the same: `Instant` only supports exact durations and a month is
 * not one, and an exact shift across a clock change can land back in the month it
 * started from — an arrow that appears to do nothing.
 *
 * The first of the month rather than the same day of it, so stepping back from
 * the 31st does not skip February.
 */
private fun Instant.stepMonth(by: Long): Instant {
    val zone = ZoneId.systemDefault()
    return atZone(zone).toLocalDate()
        .withDayOfMonth(1)
        .plusMonths(by)
        .atTime(12, 0)
        .atZone(zone)
        .toInstant()
}
