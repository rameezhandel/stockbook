import SwiftUI

/// A month folded into an answer: where it went, or who it was with.
///
/// **The other question about the same records.** The list behind this sheet
/// answers "what happened", one record a line, over whatever span the picker
/// says. This answers "where did the month go" — and the two are not the same
/// page in two styles: a register is checked against the receipts in a drawer
/// line by line, and a folded page cannot be checked against anything. Which is
/// why the wording keeps them apart, `Report` against `Summary`, everywhere they
/// are named.
///
/// **One sheet for all four sides.** Expenses fold by what the money went on and
/// the other three by the person on the other side of the counter, but that
/// difference lives entirely in `SummaryDocument` — everything below is a title,
/// some rows, a total and a month, whichever chip asked for it.
///
/// **A month at a time, and only a month.** The book's own picker offers a year
/// and a hand-picked stretch as well; neither belongs here. "Petrol, 84 times"
/// is a fact about a year that tells the owner nothing they can act on, and the
/// whole use of this page is comparing one month against the last — which is
/// what the two arrows are for.
///
/// Everything drawn comes out of `SummaryDocument`, so what is read on the
/// screen and what comes out of the printer cannot drift.
struct SummarySheet: View {
    @Environment(StockbookStore.self) private var store

    /// Which of the four is being folded.
    let side: BookSide
    /// Any date inside the month being folded.
    let month: Date
    /// Steps to another month without closing the sheet.
    let onMonth: (Date) -> Void
    let onClose: () -> Void

    /// The rendered page, waiting for the share sheet. Rendered on the tap rather
    /// than when the view is built, because the month changes underneath it.
    @State private var file: StatementFile?

    /// Read straight off the store rather than snapshotted into `@State`: it is
    /// `@Observable`, so a record written while this is open redraws the line it
    /// belongs to. Android has to key this on the shop state by hand.
    private var page: SummaryDocument {
        SummaryDocument.folded(side: side, month: month, in: store)
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
                // that has emptied a list on this codebase before. A month with
                // more names on it than fit here still scrolls, because the sheet
                // does.
                VStack(alignment: .leading, spacing: 0) {
                    VStack(spacing: Metrics.rowGap) {
                        ForEach(Array(document.rows.enumerated()), id: \.offset) { _, row in
                            FoldedRow(row: row)
                        }
                    }

                    // Inside the card it totals, under a rule — and it is the same
                    // figure this side's card on the pane shows for the same
                    // month, which is what `SummaryDocumentTests` pins for all
                    // four.
                    FadedRule(inset: 0)
                        .padding(.top, 10)
                        .padding(.bottom, 8)

                    HStack {
                        Text(document.totalLabel).nocturneText(.meta)
                        Spacer(minLength: 8)
                        Text(document.totalValue).nocturneText(.rowPrimary)
                    }

                    // What the column cannot carry — on payments, the money that
                    // went the other way. Under the total rather than in it,
                    // because a total that is not what the rows add up to is the
                    // figure the first reader to check it stops trusting. Drawn
                    // where the printed page draws it.
                    if let footnote = document.footnote {
                        Text(footnote)
                            .nocturneText(.meta)
                            .padding(.top, 8)
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
        // Named for the month it folds, not for today: two prints of August are
        // the same page, and a folder of these is read by their file names.
        let name = Copy.fileMonth(month)
        let fileName = switch side {
        case .sales: Loc.salesSummaryFileName(month: name)
        case .purchases: Loc.purchaseSummaryFileName(month: name)
        case .payments: Loc.paymentsSummaryFileName(month: name)
        case .expenses: Loc.expenseSummaryFileName(month: name)
        }
        guard let url = try? SummaryPDF.write(page, fileName: fileName) else { return }
        file = StatementFile(url: url)
    }
}

extension SummaryDocument {
    /// The folded page for one side of the book, over one month.
    ///
    /// The one place the four differ, kept together so a fifth side is a branch
    /// here rather than a fifth sheet — and so the sheet and the printed page are
    /// built by the same call. Payments is the odd one and reads the store twice:
    /// its column is money in, and what went out over the same month goes under
    /// the total as a fact rather than a row.
    ///
    /// `@MainActor` because `Loc` is read inside it and `Loc` is main-actor
    /// isolated. Every caller is a `body` or a button action there already, so
    /// the annotation costs nothing — and without it this is the isolation error
    /// that has cost this codebase a round trip before.
    @MainActor
    static func folded(side: BookSide, month: Date, in store: StockbookStore) -> SummaryDocument {
        let period = StatementPeriod.month(month)
        switch side {
        case .sales:
            return forSalesSummary(
                lines: store.salesByCustomerIn(period),
                monthOf: month,
                settings: store.settings,
                strings: Loc
            )
        case .purchases:
            return forPurchaseSummary(
                lines: store.purchasesBySupplierIn(period),
                monthOf: month,
                settings: store.settings,
                strings: Loc
            )
        case .payments:
            return forPaymentsSummary(
                lines: store.receiptsByCustomerIn(period),
                paidOut: store.paidOutIn(period),
                monthOf: month,
                settings: store.settings,
                strings: Loc
            )
        case .expenses:
            return forSpendingSummary(
                lines: store.spendingIn(period),
                monthOf: month,
                settings: store.settings,
                strings: Loc
            )
        }
    }
}

/// One folded line — a name, and how many records stand behind it.
///
/// The count sits under the name rather than in a column of its own: a phone row
/// has room for a name and a figure, and squeezing a third column between them
/// takes the room from the one thing on the row that has to stay readable.
private struct FoldedRow: View {
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
