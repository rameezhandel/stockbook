import SwiftUI

/// What the trading left the shop with, over the span Home was showing.
///
/// **The owner's own page**, like the four summaries it sits beside — it says
/// what the shop makes, which is the last thing to hand across a counter.
///
/// Everything drawn here comes out of `EarningsDocument`, so the screen and the
/// printed page cannot describe the same month differently. The chain of figures
/// and the confession under it are both that document's decisions; this only
/// gives them weight.
struct EarningsSheet: View {
    @Environment(StockbookStore.self) private var store

    let period: StatementPeriod
    let onClose: () -> Void

    /// The rendered page, waiting for the share sheet.
    @State private var file: StatementFile?

    /// Read straight off the store rather than snapshotted into `@State`: it is
    /// `@Observable`, so a bill written while this is open redraws the figures.
    /// Android has to key this on the shop state by hand.
    private var page: EarningsDocument {
        EarningsDocument.make(
            earnings: store.earningsIn(period),
            range: period.range(),
            settings: store.settings,
            strings: Loc
        )
    }

    var body: some View {
        let document = page

        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: document.title,
                subtitle: document.onDate,
                onShare: document.isEmpty ? nil : save,
                onClose: onClose
            )

            if document.isEmpty {
                Text(document.emptyLine)
                    .nocturneText(.meta)
                    .padding(.bottom, 8)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(document.lines.enumerated()), id: \.offset) { _, line in
                        FigureRow(line: line)
                    }
                }
                .cardBox()

                // The confession, in a card of its own so it cannot be mistaken
                // for part of the arithmetic above it.
                if document.hasGap {
                    VStack(alignment: .leading, spacing: 0) {
                        Kicker(document.gapHeading)
                            .padding(.bottom, 10)

                        ForEach(Array(document.gap.enumerated()), id: \.offset) { _, line in
                            HStack(spacing: 8) {
                                Text(line.label)
                                    .nocturneText(.meta)
                                    .lineLimit(1)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                Text(line.value)
                                    .font(NocturneType.inter(11.5))
                                    .foregroundStyle(Nocturne.text)
                            }
                            .padding(.bottom, 6)
                        }

                        if let note = document.gapNote {
                            Text(note)
                                .nocturneText(.meta)
                                .padding(.top, 4)
                        }
                    }
                    .cardBox()
                    .padding(.top, Metrics.cardGap)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, 4)
        .sheet(item: $file) { ShareSheet(url: $0.url) }
    }

    /// A failure leaves `file` nil and nothing opens, which is the honest outcome
    /// `StatementScreen` already settled on: there is no half-written page worth
    /// offering, and the figures are still on screen.
    private func save() {
        guard let url = try? EarningsPDF.write(
            page,
            fileName: Loc.earningsFileName(date: Copy.fileDate(.now))
        ) else { return }
        file = StatementFile(url: url)
    }
}

/// One step of the arithmetic.
///
/// A subtraction wears its minus and stays grey; a total gets a rule over it and
/// the weight of an answer. Without the two apart the column reads as six
/// unrelated figures rather than as a sum being worked.
private struct FigureRow: View {
    let line: EarningsDocument.Line

    var body: some View {
        let isTotal = line.weight == .total
        let isMinus = line.weight == .minus

        VStack(spacing: 0) {
            if isTotal {
                FadedRule(inset: 0)
                    .padding(.vertical, 8)
            }

            HStack(spacing: 10) {
                Text(line.label)
                    .font(isTotal ? NocturneType.inter(14.5) : NocturneType.inter(11.5))
                    .foregroundStyle(isTotal ? Nocturne.text : Nocturne.neutral500)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Text(isMinus ? "− \(line.value)" : line.value)
                    .font(isTotal ? NocturneType.inter(14.5) : NocturneType.inter(11.5))
                    .foregroundStyle(isTotal ? Nocturne.accent400 : (isMinus ? Nocturne.neutral500 : Nocturne.text))
                    .lineLimit(1)
            }
            .padding(.bottom, isTotal ? 0 : 6)
        }
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
