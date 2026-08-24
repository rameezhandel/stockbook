package com.stockbook.core.text

import com.stockbook.core.model.Currency
import com.stockbook.core.model.Settings
import com.stockbook.core.model.StatementRange
import com.stockbook.core.money.Money
import com.stockbook.core.store.Earnings

/**
 * What a stretch of trading left the shop with, laid out to be read.
 *
 * One column of figures that walks from takings to what was kept, each step
 * subtracting something named. Nothing is derived here — [Earnings] has already
 * done the arithmetic and this decides only what it is called and in what order
 * it reads.
 *
 * **Owner-only, like every other summary in this file's neighbourhood.** It says
 * what the shop makes, which is the last thing to hand across a counter, and it
 * is never called a statement.
 *
 * **The gap is part of the page, not a footnote to it.** A shop entering paper
 * bills as single figures has takings this cannot account for, and a page that
 * silently answered for the rest would be flattering by exactly what it left
 * out. Where there is nothing to confess the block is absent and the chain
 * shortens, which is the honest shape for a shop that itemises everything.
 */
data class EarningsDocument(
    val shopName: String,
    /** What this is. Says *summary*, never *statement*. */
    val title: String,
    /** `1 – 31 August 2026` — the stretch the figures cover. */
    val onDate: String,
    /** Takings down to what was kept, in reading order. */
    val lines: List<Line>,
    /** What the page could not account for. Empty when there is nothing. */
    val gapHeading: String,
    val gap: List<Line>,
    /** Why the credit notes below are listed but not subtracted. */
    val gapNote: String?,
    /** Shown instead of everything when nothing was sold or spent. */
    val emptyLine: String
) {
    /**
     * How a line reads: a plain figure, something being taken away, or a figure
     * the ones above it add up to.
     */
    enum class Weight { PLAIN, MINUS, TOTAL }

    data class Line(val label: String, val value: String, val weight: Weight = Weight.PLAIN)

    val isEmpty: Boolean get() = lines.isEmpty()
    val hasGap: Boolean get() = gap.isNotEmpty()

    companion object {

        fun make(
            earnings: Earnings,
            range: StatementRange,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency
        ): EarningsDocument {
            fun money(value: Double) = Money.text(value, currency)

            val lines = buildList {
                add(Line(strings.soldInPeriod, money(earnings.sold)))

                // Only where some of the takings cannot be answered for. On a
                // shop that itemises every bill this pair disappears and the
                // page reads Sold → Cost → Earned, which is the whole story.
                if (earnings.billsWithoutCost > 0) {
                    add(Line(strings.notCounted, money(earnings.soldWithoutCost), Weight.MINUS))
                    add(Line(strings.countedSales, money(earnings.counted), Weight.TOTAL))
                }

                add(Line(strings.costOfGoods, money(earnings.costOfGoods), Weight.MINUS))
                add(Line(strings.goodsEarned, money(earnings.goodsEarned), Weight.TOTAL))
                add(Line(strings.expensesTitle, money(earnings.expenses), Weight.MINUS))
                // The one figure on the page that can go either way, so the one
                // that carries a sign.
                add(Line(strings.shopKept, Money.signed(earnings.kept, currency), Weight.TOTAL))
            }

            val gap = buildList {
                if (earnings.billsWithoutCost > 0) {
                    add(Line(strings.billsAsTotal(earnings.billsWithoutCost), money(earnings.soldWithoutCost)))
                }
                if (earnings.creditNotes > 0) {
                    add(Line(strings.creditNotesIssued(earnings.creditNotes), money(earnings.credited)))
                }
            }

            return EarningsDocument(
                shopName = settings.ownerName,
                title = strings.earningsSummary,
                onDate = strings.dateSpan(
                    strings.longDate(range.start),
                    // The last day *inside* the range, for the reason the expense
                    // summary gives: a period ending at midnight on the 1st is an
                    // August page titled "to 1 September".
                    strings.longDate(range.end.minusSeconds(1))
                ),
                lines = if (earnings.isEmpty) emptyList() else lines,
                gapHeading = strings.notCounted,
                gap = if (earnings.isEmpty) emptyList() else gap,
                // Said only where a note was actually written. A standing
                // disclaimer under a page with no credit notes on it is a line
                // the owner learns to skip.
                gapNote = strings.creditNotesNotSubtracted.takeIf { earnings.creditNotes > 0 },
                emptyLine = strings.nothingSoldThen
            )
        }
    }
}
