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
    /**
     * The shop's address for the masthead, from [Settings.addressLines].
     *
     * Every page the app prints carries the same letterhead now — the shop's
     * name and where it is — so a sheet on a desk says whose it is without
     * anybody having to remember. The ledger book is the exception, and it is
     * drawn by a different writer.
     */
    val shopAddressLines: List<String>,
    /** What this is. Says *summary*, never *statement*. */
    val title: String,
    /** `1 – 31 August 2026` — the stretch the figures cover. */
    val onDate: String,
    /** Takings down to what was kept, in reading order. */
    val lines: List<Line>,
    /** What the page could not account for. Empty when there is nothing. */
    val gapHeading: String,
    val gap: List<Line>,
    /**
     * The line under the gap: why nothing could be costed, or why the credit
     * notes are listed and not subtracted.
     */
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

                // And the chain stops there when there is nothing to cost.
                // Running it on would subtract nothing from nothing, arrive at
                // an earnings figure of zero, and hand back the month's expenses
                // with a minus in front as though the shop had lost them. It has
                // not lost anything; this page simply cannot say.
                if (earnings.nothingCostable) return@buildList

                // Net of what came back. Goods handed back are goods the shop
                // still has, so their cost was never really spent — and netting
                // it here rather than on a row of its own keeps every line a
                // figure the owner recognises.
                add(Line(strings.costOfGoods, money(earnings.netCostOfGoods), Weight.MINUS))
                add(Line(strings.goodsEarned, money(earnings.goodsEarned), Weight.TOTAL))
                // Only where one was written. The full credit comes off, its
                // goods having already been added back above: credit 200 for
                // goods that cost 140 and the shop is 60 worse off, which is
                // what these two lines together say.
                if (earnings.creditNotes > 0) {
                    add(Line(strings.creditedLabel, money(earnings.credited), Weight.MINUS))
                }
                add(Line(strings.expensesTitle, money(earnings.expenses), Weight.MINUS))
                // The one figure on the page that can go either way, so the one
                // that carries a sign.
                add(Line(strings.shopKept, Money.signed(earnings.kept, currency), Weight.TOTAL))
            }

            val gap = buildList {
                // Named by *why*, because the two ask different things of the
                // owner: one is "itemise the next one", the other is "this book
                // is older than the field" and needs nothing from them at all.
                if (earnings.billsAsTotal > 0) {
                    add(Line(strings.billsAsTotal(earnings.billsAsTotal), money(earnings.soldAsTotal)))
                }
                if (earnings.billsBeforeCosts > 0) {
                    add(Line(strings.billsBeforeCosts(earnings.billsBeforeCosts), money(earnings.soldBeforeCosts)))
                }
                // Counted, unlike the two above — but the owner is told which
                // part of the answer rests on today's prices rather than on what
                // was actually paid.
                if (earnings.billsEstimated > 0) {
                    add(Line(strings.billsEstimated(earnings.billsEstimated), money(earnings.soldEstimated)))
                }
                // Only the notes whose goods could not be valued at all. The
                // rest are on the page above, taken off rather than listed
                // beside — which is what this block used to do and what made it
                // the owner's arithmetic instead of the app's.
                if (earnings.creditNotesBeforeCosts > 0) {
                    add(Line(strings.creditNotesBeforeCosts(earnings.creditNotesBeforeCosts), ""))
                }
            }

            return EarningsDocument(
                shopName = settings.ownerName,
            shopAddressLines = settings.addressLines,
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
                gapNote = when {
                    // Said above everything else where it applies: a figure the
                    // owner might act on is partly guessed, and that is the most
                    // important thing on the page.
                    earnings.hasEstimates -> strings.costsEstimated
                    // The page owes an explanation before it owes a caveat: a
                    // shop whose whole book predates cost-keeping needs to know
                    // the figures will arrive as it trades, not that credit
                    // notes are handled a particular way.
                    earnings.nothingCostable -> strings.nothingCostableYet
                    // And where a return could not be valued, why that leaves
                    // the figure low rather than merely uncertain.
                    earnings.creditNotesBeforeCosts > 0 -> strings.returnsNotValued
                    else -> null
                },
                emptyLine = strings.nothingSoldThen
            )
        }
    }
}
