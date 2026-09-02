import SwiftUI

/// One day of the shop, opened from the date at the top of Home.
///
/// **The owner's own page.** It names every customer billed that day beside what
/// the shop spent its money on, which is why it is a summary and never a
/// statement — see `DaySummaryDocument`, where that rule and the wording both
/// live. Everything drawn here comes out of that document, so what the owner
/// reads on the screen and what comes out of the printer cannot drift.
///
/// **A card per section.** Six kinds of record in one column read as one long
/// ledger, and the whole point of the page is that they are six separate
/// questions — what was sold, what came in against it, what went out. A card
/// puts a boundary where the meaning changes, and the subtotal at the foot of
/// each one lands inside the thing it totals.
struct DaySheet: View {
    @Environment(StockbookStore.self) private var store

    let day: Date
    /// Steps to another day without closing the sheet.
    let onDay: (Date) -> Void
    let onClose: () -> Void

    /// The rendered page, waiting for the share sheet. Rendered on the tap rather
    /// than when the view is built, because the day changes underneath it.
    @State private var file: StatementFile?

    /// Read straight off the store rather than snapshotted into `@State`: it is
    /// `@Observable`, so a payment taken while this is open redraws the row it
    /// settled. Android has to key this on the shop state by hand.
    private var page: DaySummaryDocument {
        DaySummaryDocument.forDay(book: store.dayBook(day), settings: store.settings, strings: Loc)
    }

    /// No stepping into tomorrow. A day that has not happened has nothing on it,
    /// and an owner who lands there wonders whether the app has lost the day's
    /// work rather than that they walked past the end of the week.
    private var isToday: Bool {
        Calendar.current.startOfDay(for: day) >= Calendar.current.startOfDay(for: .now)
    }

    var body: some View {
        let document = page

        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: document.title,
                // Only where there is a day to hand over. A page saying nothing
                // happened is a page nobody needs a copy of.
                onShare: document.isEmpty ? nil : save,
                onClose: onClose
            )

            // `‹ 22 August 2026 ›` — the date reads as the thing being stepped
            // through rather than as a caption with two buttons parked beside it.
            HStack {
                Button { onDay(step(-1)) } label: { Glyph(Icon.stepBack, size: 18) }
                    .buttonStyle(.iconOnly)
                    .foregroundStyle(Nocturne.accent)
                    .accessibilityLabel(Loc.previousDay)

                Text(document.onDate)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)

                if isToday {
                    // Holds the date in the middle on the day there is no forward
                    // arrow to draw. Without it today's date sits off to the
                    // right and every step back nudges it, which reads as a bug.
                    Color.clear.frame(width: Metrics.minimumTouchTarget, height: Metrics.minimumTouchTarget)
                } else {
                    Button { onDay(step(1)) } label: { Glyph(Icon.stepForward, size: 18) }
                        .buttonStyle(.iconOnly)
                        .foregroundStyle(Nocturne.accent)
                        .accessibilityLabel(Loc.nextDay)
                }
            }
            .padding(.bottom, 16)

            if document.isEmpty {
                Text(document.emptyLine)
                    .nocturneText(.meta)
                    .padding(.bottom, 8)
            } else {
                // A plain stack rather than a lazy one: the sheet already
                // scrolls, and a day with more rows than fit in it is a very
                // good day.
                VStack(spacing: Metrics.cardGap) {
                    ForEach(Array(document.sections.enumerated()), id: \.offset) { _, section in
                        SectionCard(section: section)
                    }

                    // What the day did to the cash box. Its own card, because it
                    // is the answer the page was read for and not a seventh kind
                    // of record.
                    VStack(spacing: 0) {
                        ForEach(Array(document.cash.enumerated()), id: \.offset) { index, line in
                            if line.isNet {
                                FadedRule(inset: 0)
                                    .padding(.vertical, 8)
                            }
                            HStack {
                                Text(line.label)
                                    .font(line.isNet ? NocturneType.inter(14.5) : NocturneType.inter(11.5))
                                    .foregroundStyle(line.isNet ? Nocturne.text : Nocturne.neutral500)
                                Spacer(minLength: 8)
                                Text(line.value)
                                    .font(line.isNet ? NocturneType.inter(14.5) : NocturneType.inter(11.5))
                                    .foregroundStyle(line.isNet ? Nocturne.accent400 : Nocturne.text)
                            }
                            if !line.isNet && index < document.cash.count - 1 {
                                Spacer().frame(height: 6)
                            }
                        }
                    }
                    .cardBox()
                }
                .padding(.bottom, 4)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .sheet(item: $file) { ShareSheet(url: $0.url) }
    }

    /// One day back or forward, as a calendar reads it.
    ///
    /// Through `Calendar` rather than by adding twenty-four hours: an exact day
    /// is not a calendar day across a clock change, and a step that landed back
    /// on the date it started from would look like an arrow that does nothing.
    private func step(_ by: Int) -> Date {
        Calendar.current.date(byAdding: .day, value: by, to: day) ?? day
    }

    /// A failure leaves `file` nil and nothing opens, which is the honest outcome
    /// and the one `StatementScreen` already settled on: there is no half-written
    /// page worth offering, and the day itself is still on screen.
    private func save() {
        guard let url = try? DaySummaryPDF.write(
            page,
            // Named for the day it covers, not for today: a folder of these is
            // read by their file names.
            fileName: Loc.dayFileName(date: Copy.fileDate(day))
        ) else { return }
        file = StatementFile(url: url)
    }
}

/// One kind of thing that happened, boxed off from the other five.
private struct SectionCard: View {
    let section: DaySummaryDocument.Section

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Kicker(section.heading)
                .padding(.bottom, 10)

            VStack(spacing: Metrics.rowGap) {
                ForEach(Array(section.rows.enumerated()), id: \.offset) { _, row in
                    DayRow(row: row)
                }
            }

            // Inside the card it totals, under a rule. A subtotal sitting
            // outside the box could belong to either side of the boundary.
            FadedRule(inset: 0)
                .padding(.top, 10)
                .padding(.bottom, 8)

            HStack {
                Text(section.subtotalLabel).nocturneText(.meta)
                Spacer(minLength: 8)
                Text(section.subtotalValue).nocturneText(.rowPrimary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardBox()
    }
}

/// One record, and what was on it where the record says.
private struct DayRow: View {
    let row: DaySummaryDocument.Row

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(row.name)
                        .nocturneText(.rowPrimary)
                        .lineLimit(1)
                    if let detail = row.detail {
                        Text(detail).nocturneText(.meta)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Text(row.amount).nocturneText(.rowPrimary)
            }

            // Immediately under the row rather than after the products, because
            // this is what the owner is scanning the page for: the record is
            // what happened, and this is where it leaves the person it happened
            // to. In the accent, so it can be picked out of a column of grey
            // asides.
            if let balance = row.balance {
                HStack(spacing: 8) {
                    Text(balance.label)
                        .nocturneText(.meta)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(balance.value)
                        .nocturneText(.meta)
                        .foregroundStyle(Nocturne.accent400)
                }
                .padding(.top, 1)
            }

            // Indented under the row they belong to, so a bill with four
            // products on it reads as one bill and not as four.
            ForEach(Array(row.items.enumerated()), id: \.offset) { _, item in
                HStack(spacing: 8) {
                    Text(item.text)
                        .nocturneText(.meta)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(item.amount).nocturneText(.meta)
                }
                .padding(.leading, 12)
                .padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private extension View {
    /// The padding every card on this sheet shares, over the app's own card
    /// treatment — surface, radius and hairline all come from `nocturneCard`.
    func cardBox() -> some View {
        padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .nocturneCard()
    }
}
