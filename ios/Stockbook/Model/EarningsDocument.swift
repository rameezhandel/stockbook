import Foundation

/// What a stretch of trading left the shop with, laid out to be read.
///
/// One column of figures that walks from takings to what was kept, each step
/// subtracting something named. Nothing is derived here — `Earnings` has already
/// done the arithmetic and this decides only what it is called and in what order
/// it reads.
///
/// **Owner-only, like every other summary in this file's neighbourhood.** It says
/// what the shop makes, which is the last thing to hand across a counter, and it
/// is never called a statement.
///
/// **The gap is part of the page, not a footnote to it.** A shop entering paper
/// bills as single figures has takings this cannot account for, and a page that
/// silently answered for the rest would be flattering by exactly what it left
/// out. Where there is nothing to confess the block is absent and the chain
/// shortens, which is the honest shape for a shop that itemises everything.
///
/// The Kotlin twin is `EarningsDocument.kt`.
struct EarningsDocument: Equatable {

    let shopName: String
    /// What this is. Says *summary*, never *statement*.
    let title: String
    /// `1 – 31 August 2026` — the stretch the figures cover.
    let onDate: String
    /// Takings down to what was kept, in reading order.
    let lines: [Line]
    /// What the page could not account for. Empty when there is nothing.
    let gapHeading: String
    let gap: [Line]
    /// Why the credit notes below are listed but not subtracted.
    let gapNote: String?
    /// Shown instead of everything when nothing was sold or spent.
    let emptyLine: String

    /// How a line reads: a plain figure, something being taken away, or a figure
    /// the ones above it add up to.
    enum Weight { case plain, minus, total }

    struct Line: Equatable {
        let label: String
        let value: String
        var weight: Weight = .plain
    }

    var isEmpty: Bool { lines.isEmpty }
    var hasGap: Bool { !gap.isEmpty }

    static func make(
        earnings: Earnings,
        range: StatementRange,
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil
    ) -> EarningsDocument {
        let money = currency ?? settings.currency
        func text(_ value: Double) -> String { Money.text(value, in: money) }

        var lines: [Line] = [Line(label: strings.soldInPeriod, value: text(earnings.sold))]

        // Only where some of the takings cannot be answered for. On a shop that
        // itemises every bill this pair disappears and the page reads Sold →
        // Cost → Earned, which is the whole story.
        if earnings.billsWithoutCost > 0 {
            lines.append(Line(label: strings.notCounted, value: text(earnings.soldWithoutCost), weight: .minus))
            lines.append(Line(label: strings.countedSales, value: text(earnings.counted), weight: .total))
        }

        // And the chain stops there when there is nothing to cost. Running it on
        // would subtract nothing from nothing, arrive at an earnings figure of
        // zero, and hand back the month's expenses with a minus in front as
        // though the shop had lost them. It has not lost anything; this page
        // simply cannot say.
        if !earnings.nothingCostable {
            lines.append(Line(label: strings.costOfGoods, value: text(earnings.costOfGoods), weight: .minus))
            lines.append(Line(label: strings.goodsEarned, value: text(earnings.goodsEarned), weight: .total))
            lines.append(Line(label: strings.expensesTitle, value: text(earnings.expenses), weight: .minus))
            // The one figure on the page that can go either way, so the one that
            // carries a sign.
            lines.append(Line(label: strings.shopKept, value: Money.signed(earnings.kept, in: money), weight: .total))
        }

        var gap: [Line] = []
        // Named by *why*, because the two ask different things of the owner: one
        // is "itemise the next one", the other is "this book is older than the
        // field" and needs nothing from them at all.
        if earnings.billsAsTotal > 0 {
            gap.append(Line(label: strings.billsAsTotal(earnings.billsAsTotal), value: text(earnings.soldAsTotal)))
        }
        if earnings.billsBeforeCosts > 0 {
            gap.append(Line(label: strings.billsBeforeCosts(earnings.billsBeforeCosts), value: text(earnings.soldBeforeCosts)))
        }
        // Counted, unlike the two above — but the owner is told which part of
        // the answer rests on today's prices rather than on what was actually
        // paid.
        if earnings.billsEstimated > 0 {
            gap.append(Line(label: strings.billsEstimated(earnings.billsEstimated), value: text(earnings.soldEstimated)))
        }
        if earnings.creditNotes > 0 {
            gap.append(Line(label: strings.creditNotesIssued(earnings.creditNotes), value: text(earnings.credited)))
        }

        return EarningsDocument(
            shopName: settings.ownerName,
            title: strings.earningsSummary,
            onDate: strings.dateSpan(
                from: strings.longDate(range.start),
                // The last day *inside* the range, for the reason the expense
                // summary gives: a period ending at midnight on the 1st is an
                // August page titled "to 1 September".
                to: strings.longDate(range.end.addingTimeInterval(-1))
            ),
            lines: earnings.isEmpty ? [] : lines,
            gapHeading: strings.notCounted,
            gap: earnings.isEmpty ? [] : gap,
            // Said above everything else where it applies: a figure the owner
            // might act on is partly guessed, and that is the most important
            // thing on the page.
            //
            // Then the page owes an explanation before it owes a caveat: a shop
            // whose whole book predates cost-keeping needs to know the figures
            // will arrive as it trades, not that credit notes are handled a
            // particular way. Last, said only where a note was actually written
            // — a standing disclaimer under a page with none on it is a line the
            // owner learns to skip.
            gapNote: {
                if earnings.hasEstimates { return strings.costsEstimated }
                if earnings.nothingCostable { return strings.nothingCostableYet }
                if earnings.creditNotes > 0 { return strings.creditNotesNotSubtracted }
                return nil
            }(),
            emptyLine: strings.nothingSoldThen
        )
    }
}
