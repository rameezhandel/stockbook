import SwiftUI

/// One day of the shop, opened from the date at the top of Home.
///
/// **The owner's own page.** It names every customer billed that day beside what
/// the shop spent its money on, which is why it is a summary and never a
/// statement — see `DaySummaryDocument`, where that rule and the wording both
/// live. Everything drawn here comes out of that document, so what the owner
/// reads on the screen and what comes out of the printer cannot drift.
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
            SheetHeader(title: document.title, subtitle: document.onDate, onClose: onClose)

            HStack(spacing: 4) {
                Button { onDay(step(-1)) } label: { Glyph(Icon.stepBack, size: 18) }
                    .buttonStyle(.iconOnly)
                    .foregroundStyle(Nocturne.accent)
                    .accessibilityLabel(Loc.previousDay)

                if !isToday {
                    Button { onDay(step(1)) } label: { Glyph(Icon.stepForward, size: 18) }
                        .buttonStyle(.iconOnly)
                        .foregroundStyle(Nocturne.accent)
                        .accessibilityLabel(Loc.nextDay)
                }

                Spacer(minLength: 8)

                // Only where there is a day to hand over. A page saying nothing
                // happened is a page nobody needs a copy of.
                if !document.isEmpty {
                    Button(Loc.sharePdf, action: save)
                        .buttonStyle(SecondaryButtonStyle(height: 34, fontSize: 12.5))
                }
            }
            .padding(.bottom, 14)

            if document.isEmpty {
                Text(document.emptyLine)
                    .nocturneText(.meta)
                    .padding(.bottom, 8)
            } else {
                // A plain stack rather than a lazy one: the sheet already
                // scrolls, and a day with more rows than fit in it is a very
                // good day.
                ForEach(Array(document.sections.enumerated()), id: \.offset) { _, section in
                    Kicker(section.heading)
                        .padding(.bottom, 6)

                    VStack(spacing: Metrics.rowGap) {
                        ForEach(Array(section.rows.enumerated()), id: \.offset) { _, row in
                            DayRow(row: row)
                        }
                    }

                    HStack {
                        Text(section.subtotalLabel).nocturneText(.meta)
                        Spacer(minLength: 8)
                        Text(section.subtotalValue)
                            .font(NocturneType.inter(11.5))
                            .foregroundStyle(Nocturne.text)
                    }
                    .padding(.top, 2)
                    .padding(.bottom, 16)
                }

                // What the day did to the cash box, under a rule so it reads as
                // the answer rather than as a seventh section.
                FadedRule()
                    .padding(.bottom, 10)

                ForEach(Array(document.cash.enumerated()), id: \.offset) { _, line in
                    HStack {
                        Text(line.label)
                            .font(line.isNet ? NocturneType.inter(14.5) : NocturneType.inter(11.5))
                            .foregroundStyle(line.isNet ? Nocturne.text : Nocturne.neutral500)
                        Spacer(minLength: 8)
                        Text(line.value)
                            .font(line.isNet ? NocturneType.inter(14.5) : NocturneType.inter(11.5))
                            .foregroundStyle(line.isNet ? Nocturne.accent400 : Nocturne.text)
                    }
                    .padding(.bottom, 6)
                }
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
