package com.stockbook.app.feature.today

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.FadedRule
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.ShopState
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.DaySummaryDocument
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId

/**
 * One day of the shop, opened from the date at the top of Home.
 *
 * **The owner's own page.** It names every customer billed that day beside what
 * the shop spent its money on, which is why it is a summary and never a
 * statement — see [DaySummaryDocument], where that rule and the wording both
 * live. Everything drawn here comes out of that document, so what the owner
 * reads on the screen and what comes out of the printer cannot drift.
 *
 * **A card per section.** Six kinds of record in one column read as one long
 * ledger, and the whole point of the page is that they are six separate
 * questions — what was sold, what came in against it, what went out. A card puts
 * a boundary where the meaning changes, and the subtotal at the foot of each one
 * lands inside the thing it totals.
 */
@Composable
fun DaySheet(
    day: Instant,
    state: ShopState,
    store: StockbookStore,
    strings: Strings,
    /** Steps to another day without closing the sheet. */
    onDay: (Instant) -> Unit,
    /** Renders this day as a page and hands it to the chooser. */
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    // Keyed on the whole state, not read off the store bare: every getter here
    // is a plain function over a StateFlow snapshot, so a payment taken while
    // this is open would otherwise leave the day showing the figures it had when
    // it was first drawn.
    val page = remember(day, state) {
        DaySummaryDocument.forDay(store.dayBook(day), state.settings, strings)
    }

    // No stepping into tomorrow. A day that has not happened has nothing on it,
    // and an owner who lands there wonders whether the app has lost the day's
    // work rather than that they walked past the end of the week.
    val isToday = remember(day) {
        // Compared as calendar days in the phone's own zone, which is the zone
        // `dayBook` groups by. Truncating the instants instead would compare
        // them in UTC, and an owner in Riyadh looking at the day's takings at
        // half past two in the morning would find tomorrow already reachable.
        val zone = ZoneId.systemDefault()
        !day.atZone(zone).toLocalDate().isBefore(Instant.now().atZone(zone).toLocalDate())
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = page.title,
            onClose = onClose,
            // Only where there is a day to hand over. A page saying nothing
            // happened is a page nobody needs a copy of.
            onShare = if (page.isEmpty) null else onSave
        )

        // `‹ 22 August 2026 ›` — the date reads as the thing being stepped
        // through rather than as a caption with two buttons parked beside it.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            IconButton(
                Icon.stepBack,
                onClick = { onDay(day.stepDay(-1)) },
                contentDescription = strings.previousDay,
                tint = Nocturne.accent
            )
            Text(
                page.onDate,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (isToday) {
                // Holds the date in the middle on the day there is no forward
                // arrow to draw. Without it today's date sits off to the right
                // and every step back nudges it, which reads as a bug.
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

        if (page.isEmpty) {
            Text(
                page.emptyLine,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            return@Column
        }

        // A plain Column rather than a lazy list: the sheet already scrolls, and
        // a day with more rows than fit in it is a very good day.
        page.sections.forEach { section ->
            SectionCard(section)
            Spacer(Modifier.height(Metrics.cardGap))
        }

        // What the day did to the cash box. Its own card, because it is the
        // answer the page was read for and not a seventh kind of record.
        Spacer(Modifier.height(2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .card()
                .hairline(radius = Metrics.cardRadius)
                .padding(14.dp)
        ) {
            page.cash.forEachIndexed { index, line ->
                if (line.isNet) {
                    FadedRule(modifier = Modifier.padding(vertical = 8.dp), inset = 0.dp)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        line.label,
                        style = if (line.isNet) NocturneType.rowPrimary else NocturneType.meta,
                        color = if (line.isNet) Nocturne.text else Nocturne.neutral500,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        line.value,
                        style = if (line.isNet) NocturneType.rowPrimary else NocturneType.meta,
                        color = if (line.isNet) Nocturne.accent400 else Nocturne.text
                    )
                }
                if (!line.isNet && index < page.cash.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** One kind of thing that happened, boxed off from the other five. */
@Composable
private fun SectionCard(section: DaySummaryDocument.Section) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .hairline(radius = Metrics.cardRadius)
            .padding(14.dp)
    ) {
        Kicker(section.heading, modifier = Modifier.padding(bottom = 10.dp))

        section.rows.forEachIndexed { index, row ->
            DayRow(row)
            if (index < section.rows.lastIndex) Spacer(Modifier.height(Metrics.rowGap))
        }

        // Inside the card it totals, under a rule. A subtotal sitting outside
        // the box could belong to either side of the boundary.
        FadedRule(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp), inset = 0.dp)
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                section.subtotalLabel,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
            Text(section.subtotalValue, style = NocturneType.rowPrimary, color = Nocturne.text)
        }
    }
}

/**
 * One day back or forward, as a calendar reads it.
 *
 * Re-anchored at midday rather than shifted by twenty-four hours: an exact day
 * is not a calendar day across a clock change, and a step that landed back on
 * the date it started from would look like an arrow that does nothing. The same
 * rule the date picker follows, for the same reason.
 */
private fun Instant.stepDay(by: Long): Instant {
    val zone = ZoneId.systemDefault()
    return atZone(zone).toLocalDate().plusDays(by).atTime(12, 0).atZone(zone).toInstant()
}

/** One record, and what was on it where the record says. */
@Composable
private fun DayRow(row: DaySummaryDocument.Row) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = NocturneType.rowPrimary,
                    color = Nocturne.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                row.detail?.let {
                    Text(it, style = NocturneType.meta, color = Nocturne.neutral500)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(row.amount, style = NocturneType.rowPrimary, color = Nocturne.text)
        }

        // Immediately under the row rather than after the products, because this
        // is what the owner is scanning the page for: the record is what
        // happened, and this is where it leaves the person it happened to. In
        // the accent, so it can be picked out of a column of grey asides.
        row.balance?.let { balance ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp)
            ) {
                Text(
                    balance.label,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(balance.value, style = NocturneType.meta, color = Nocturne.accent400)
            }
        }

        // Indented under the row they belong to, so a bill with four products on
        // it reads as one bill and not as four.
        row.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp)) {
                Text(
                    item.text,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(item.amount, style = NocturneType.meta, color = Nocturne.neutral500)
            }
        }
    }
}
