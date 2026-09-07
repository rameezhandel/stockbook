import SwiftUI

/// Where a month's money went, folded by what it went on.
///
/// **The other question about the same expenses.** The list behind this sheet
/// answers "what did I spend", one receipt a line, over whatever span the picker
/// says. This answers "where did the month go" — and the two are not the same
/// page in two styles: a register is checked against the receipts in a drawer
/// line by line, and a folded page cannot be checked against anything. Which is
/// why the wording keeps them apart, `Report` against `Summary`, everywhere they
/// are named.
///
/// **A month at a time, and only a month.** The book's own picker offers a year
/// and a hand-picked stretch as well; neither belongs here. "Petrol, 84 times"
/// is a fact about a year that tells the owner nothing they can act on, and the
/// whole use of this page is comparing one month against the last — which is
/// what the two arrows are for.
///
/// Everything drawn comes out of `SummaryDocument`, so what is read on the
/// screen and what comes out of the printer cannot drift.
struct ExpenseSummarySheet: View {
    @Environment(StockbookStore.self) private var store

    /// Any date inside the month being folded.
    let month: Date
    /// Steps to another month without closing the sheet.
    let onMonth: (Date) -> Void
    let onClose: () -> Void

    /// The rendered page, waiting for the share sheet. Rendered on the tap rather
    /// than when the view is built, because the month changes underneath it.
    @State private var file: StatementFile?

    /// Read straight off the store rather than snapshotted into `@State`: it is
    /// `@Observable`, so an expense written while this is open redraws the line
    /// it belongs to. Android has to key this on the shop state by hand.
    private var page: SummaryDocument {
        SummaryDocument.forSpendingSummary(
            lines: store.spendingIn(.month(month)),
            monthOf: month,
            settings: store.settings,
            strings: Loc
        )
    }

    /// No stepping into next month. A month that has not started has nothing in
    /// it, and an owner who lands there wonders whether the app has lost their
    /// records rather than that they walked past the end of the year.
    private var isThisMonth: Bool {
        Calendar.current.compare(month, to: .now, toGranularity: .month) != .orderedAscending
    }

    var body: some View {
        let document = page

        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: document.title,
                // Only where there is a month to hand over. A page saying nothing
                // happened is a page nobody needs a copy of.
                onShare: document.isEmpty ? nil : save,
                onClose: onClose
            )

            // `‹ August 2026 ›` — the month reads as the thing being stepped
            // through rather than as a caption with two buttons parked beside it.
            // The same shape the day summary steps days with, because it is the
            // same gesture.
            HStack {
                Button { onMonth(step(-1)) } label: { Glyph(Icon.stepBack, size: 18) }
                    .buttonStyle(.iconOnly)
                    .foregroundStyle(Nocturne.accent)
                    .accessibilityLabel(Loc.previousMonth)

                Text(document.asOf)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)

                if isThisMonth {
                    // Holds the month in the middle when there is no forward arrow
                    // to draw. Without it this month's name sits off to the right
                    // and every step back nudges it, which reads as a bug.
                    Color.clear.frame(width: Metrics.minimumTouchTarget, height: Metrics.minimumTouchTarget)
                } else {
                    Button { onMonth(step(1)) } label: { Glyph(Icon.stepForward, size: 18) }
                        .buttonStyle(.iconOnly)
                        .foregroundStyle(Nocturne.accent)
                        .accessibilityLabel(Loc.nextMonth)
                }
            }
            .padding(.bottom, 16)

            if document.isEmpty {
                Text(document.emptyLine)
                    .nocturneText(.meta)
                    .padding(.bottom, 8)
            } else {
                // A plain stack rather than a lazy one: the sheet already scrolls
                // its content, and a second scroll nested in the first is the trap
                // that has emptied a list on this codebase before. A shop with
                // more things to spend on than fit here has a bigger problem than
                // the scroll.
                VStack(alignment: .leading, spacing: 0) {
                    VStack(spacing: Metrics.rowGap) {
                        ForEach(Array(document.rows.enumerated()), id: \.offset) { _, row in
                            SpendRow(row: row)
                        }
                    }

                    // Inside the card it totals, under a rule — and it is the same
                    // figure the Expense card on the pane shows for the same
                    // month, which is what `SummaryDocumentTests` pins.
                    FadedRule(inset: 0)
                        .padding(.top, 10)
                        .padding(.bottom, 8)

                    HStack {
                        Text(document.totalLabel).nocturneText(.meta)
                        Spacer(minLength: 8)
                        Text(document.totalValue).nocturneText(.rowPrimary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                // The card treatment written out rather than through a
                // `cardBox()` helper: the two that exist are `private extension
                // View` in the sheets that use them, so they are file-scoped and
                // a third copy here would earn nothing over the two lines.
                .padding(14)
                .nocturneCard()
                .padding(.bottom, 4)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .sheet(item: $file) { ShareSheet(url: $0.url) }
    }

    /// One month back or forward, as a calendar reads it.
    ///
    /// Through `Calendar` rather than by adding thirty days, for the reason the
    /// day stepper goes through it too: months are not a fixed length, and a step
    /// that landed back in the month it started from would look like an arrow
    /// that does nothing.
    private func step(_ by: Int) -> Date {
        Calendar.current.date(byAdding: .month, value: by, to: month) ?? month
    }

    /// A failure leaves `file` nil and nothing opens, which is the honest outcome
    /// the other printed pages already settled on.
    private func save() {
        guard let url = try? SummaryPDF.write(
            page,
            // Named for the month it folds, not for today: two prints of August
            // are the same page, and a folder of these is read by their file
            // names.
            fileName: Loc.expenseSummaryFileName(month: Copy.fileMonth(month))
        ) else { return }
        file = StatementFile(url: url)
    }
}

/// One thing the money went on, and how often.
///
/// The count sits under the name rather than in a column of its own: a phone row
/// has room for a name and a figure, and squeezing a third column between them
/// takes the room from the one thing on the row that has to stay readable.
private struct SpendRow: View {
    let row: SummaryDocument.Row

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 0) {
                Text(row.name)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                if let count = row.count {
                    Text(count).nocturneText(.meta)
                }
            }
            Spacer(minLength: 8)
            Text(row.amount).nocturneText(.rowPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
