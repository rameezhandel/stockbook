package com.stockbook.app.feature.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.card
import com.stockbook.core.model.Currency
import com.stockbook.core.model.DayLedger
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId

/**
 * Every customer, and what one day did to what they owe.
 *
 * **The roll-call is the feature.** A hundred names appear whether or not
 * anything happened to them, because this page is read down against a paper book
 * and a list that quietly skipped the quiet ones could not be. The three that
 * moved are told apart by weight rather than by being the only ones present —
 * see [Row] — and the switch at the top narrows to them when the owner wants the
 * day rather than the ledger.
 *
 * **Five columns, always.** A day carrying credit notes or moved balances would
 * need seven, which no phone has room for, so those land as a line under the name
 * of the row they belong to. The row still adds up and the reader can still see
 * why — which a sixth and seventh column of mostly nothing would not have earned.
 */
@Composable
fun DayLedgerSheet(
    day: Instant,
    state: ShopState,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    /** Steps to another day without closing the sheet. */
    onDay: (Instant) -> Unit,
    /**
     * Renders what is on screen as a page and hands it to the chooser.
     *
     * Takes the narrowed ledger rather than the day, so the sheet of paper says
     * exactly what the screen said. Printing the whole roll-call from a screen
     * showing only what moved would hand the owner a page they did not ask for
     * and whose totals do not match the ones they were just reading.
     */
    onSave: (DayLedger, Boolean) -> Unit,
    onClose: () -> Unit
) {
    // Keyed on the whole state, never read off the store bare: every getter here
    // is a plain function over a StateFlow snapshot, so a payment taken while
    // this is open would otherwise leave the page showing stale figures.
    val ledger = remember(day, state) { store.dayLedger(day) }
    var onlyMoved by remember(day) { mutableStateOf(false) }

    // No stepping into tomorrow, for the reason the day sheet gives: a day that
    // has not happened has nothing on it, and an owner who lands there wonders
    // whether the app has lost the day's work.
    val isToday = remember(day) {
        val zone = ZoneId.systemDefault()
        !day.atZone(zone).toLocalDate().isBefore(Instant.now().atZone(zone).toLocalDate())
    }

    // A whole ledger, not a filtered list: every total below is derived from
    // `rows`, so narrowing this narrows them too. The page used to show all-book
    // totals under a filtered column, which is a figure a column does not add up
    // to — see `DayLedger.movedOnly`.
    val shown = remember(ledger, onlyMoved) { if (onlyMoved) ledger.movedOnly() else ledger }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = strings.dayBalances,
            onClose = onClose,
            // Nothing to hand over on a page with no rows on it.
            onShare = if (shown.isEmpty) null else ({ onSave(shown, onlyMoved) })
        )

        // `‹ 22 August 2026 ›` — the date reads as the thing being stepped
        // through rather than as a caption with two buttons parked beside it.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            IconButton(
                Icon.stepBack,
                onClick = { onDay(day.stepDay(-1)) },
                contentDescription = strings.previousDay,
                tint = Nocturne.accent
            )
            Text(
                strings.longDate(day),
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (isToday) {
                // Holds the date in the middle on the day there is no forward
                // arrow to draw, so stepping back does not nudge it sideways.
                Spacer(Modifier.size(Metrics.minimumTouchTarget))
            } else {
                IconButton(
                    Icon.stepForward,
                    onClick = { onDay(day.stepDay(1)) },
                    contentDescription = strings.nextDay,
                    tint = Nocturne.accent
                )
            }
        }

        // How much of the page is the day and how much is the roll-call, said
        // before the owner starts counting names.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            Text(
                if (ledger.busyRows.isEmpty()) {
                    strings.ledgerNobodyMoved
                } else {
                    strings.ledgerBusyCount(ledger.busyRows.size)
                },
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
            // One question only: did anything happen on this day. If it did, the
            // switch is offered; if it did not, there is nothing to switch to.
            //
            // It used to also hide when *everybody* moved, on the reasoning that
            // filtering would then show the same list. That made the control
            // appear and disappear on an arithmetic coincidence — and a button
            // that comes and goes as the owner steps through dates reads as a
            // bug, which is worse than a button that shows the same rows twice.
            if (ledger.busyRows.isNotEmpty()) {
                GhostButton(
                    if (onlyMoved) strings.ledgerShowAll else strings.ledgerShowMoved,
                    onClick = { onlyMoved = !onlyMoved },
                    fontSize = 12.0,
                    tint = Nocturne.accent400
                )
            }
        }

        if (shown.isEmpty) {
            Text(
                strings.ledgerNoCustomers,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(vertical = 14.dp)
            )
            return@Column
        }

        HeadingRow(strings)

        // Drawn straight into the column, never a `LazyColumn`.
        //
        // The sheet's own content slot is a `verticalScroll` column, so its
        // height is unbounded — and a lazy list inside one of those measures to
        // nothing and draws no rows at all. This page shipped that way once: the
        // heading and the totals appeared and all hundred customers between them
        // were silently missing, which is a screen that looks finished and
        // answers nothing. Every other list sheet in the app uses `forEach` for
        // the same reason.
        shown.rows.forEach { row ->
            LedgerRow(row, currency, strings)
            Spacer(Modifier.height(2.dp))
        }

        TotalsRow(shown, currency)
    }
}

/** The column headings, in the order the row below draws them. */
@Composable
private fun HeadingRow(strings: Strings) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Heading(strings.customersTitle, Modifier.weight(1f), TextAlign.Start)
        Heading(strings.ledgerInvoiced, Modifier.width(COLUMN))
        Heading(strings.ledgerReceived, Modifier.width(COLUMN))
        Heading(strings.ledgerOldBalance, Modifier.width(COLUMN))
        Heading(strings.ledgerCurrentBalance, Modifier.width(COLUMN))
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier, align: TextAlign = TextAlign.End) {
    Text(
        text,
        style = NocturneType.inter(10.0),
        color = Nocturne.neutral600,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * One customer's line.
 *
 * A quiet row is drawn grey and its two movement columns are left empty rather
 * than filled with zeroes: the owner asked for the whole roll-call, and a page
 * of a hundred `0.00`s is a page whose real figures have nowhere to stand out
 * from.
 */
@Composable
private fun LedgerRow(row: DayLedger.Row, currency: Currency, strings: Strings) {
    val moved = !row.isQuiet
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (moved) Modifier.card(Metrics.controlRadius) else Modifier)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                row.name,
                style = NocturneType.inter(12.0),
                color = if (moved) Nocturne.text else Nocturne.neutral500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Figure(row.invoiced.takeIf { it != 0.0 }, currency, Nocturne.text)
            Figure(row.received.takeIf { it != 0.0 }, currency, Nocturne.accent400)
            Figure(row.openingBalance, currency, Nocturne.neutral500, always = true)
            Figure(
                row.closingBalance,
                currency,
                if (row.closingBalance > 0) Nocturne.accent400 else Nocturne.neutral500,
                always = true
            )
        }

        // The two things a five-column table cannot hold, said under the name of
        // the row they belong to so it still adds up on the page.
        val notes = buildList {
            if (row.credited != 0.0) add("${strings.ledgerCredited} ${Money.text(row.credited, currency)}")
            if (row.transferredIn != 0.0) add("${strings.ledgerMoved} +${Money.text(row.transferredIn, currency)}")
            if (row.transferredOut != 0.0) add("${strings.ledgerMoved} −${Money.text(row.transferredOut, currency)}")
        }
        if (notes.isNotEmpty()) {
            Text(
                notes.joinToString(" · "),
                style = NocturneType.inter(10.5),
                color = Nocturne.neutral500,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** The columns added up, so the page can be checked against what the shop is owed. */
@Composable
private fun TotalsRow(ledger: DayLedger, currency: Currency) {
    Spacer(Modifier.height(6.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .card(Metrics.controlRadius)
            .padding(horizontal = 4.dp, vertical = 9.dp)
    ) {
        Spacer(Modifier.weight(1f))
        Figure(ledger.invoiced.takeIf { it != 0.0 }, currency, Nocturne.text, bold = true)
        Figure(ledger.received.takeIf { it != 0.0 }, currency, Nocturne.accent400, bold = true)
        Figure(ledger.openingBalance, currency, Nocturne.neutral500, always = true, bold = true)
        Figure(ledger.closingBalance, currency, Nocturne.accent400, always = true, bold = true)
    }
}

/** One money column. Null is drawn as nothing at all, never as a zero. */
@Composable
private fun Figure(
    amount: Double?,
    currency: Currency,
    tint: androidx.compose.ui.graphics.Color,
    always: Boolean = false,
    bold: Boolean = false
) {
    Text(
        when {
            amount == null -> ""
            !always && amount == 0.0 -> ""
            else -> Money.amount(amount, currency)
        },
        style = if (bold) NocturneType.inter(11.5) else NocturneType.inter(11.0),
        color = tint,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(COLUMN)
    )
}

/**
 * The width of one money column.
 *
 * Fixed rather than weighted so the figures line up down the page — a column
 * that sized itself to its own contents would step in and out as the owner
 * scrolls, which is the one thing that makes a table of numbers unreadable.
 */
private val COLUMN = 62.dp

/**
 * One day back or forward, as a calendar reads it.
 *
 * Re-anchored at midday rather than shifted by twenty-four hours: an exact day is
 * not a calendar day across a clock change, and a step that landed back on the
 * date it started from would look like an arrow that does nothing.
 */
private fun Instant.stepDay(by: Long): Instant {
    val zone = ZoneId.systemDefault()
    return atZone(zone).toLocalDate().plusDays(by).atTime(12, 0).atZone(zone).toInstant()
}
