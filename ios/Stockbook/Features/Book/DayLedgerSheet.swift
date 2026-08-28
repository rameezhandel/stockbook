import SwiftUI

/// Every customer, and what one day did to what they owe.
///
/// The twin of `DayLedgerSheet.kt`.
///
/// **The roll-call is the feature.** A hundred names appear whether or not
/// anything happened to them, because this page is read down against a paper book
/// and a list that quietly skipped the quiet ones could not be. The three that
/// moved are told apart by weight rather than by being the only ones present, and
/// the switch at the top narrows to them when the owner wants the day rather than
/// the ledger.
///
/// **Five columns, always.** A day carrying credit notes or moved balances would
/// need seven, which no phone has room for, so those land as a line under the name
/// of the row they belong to. The row still adds up and the reader can still see
/// why — which a sixth and seventh column of mostly nothing would not have earned.
struct DayLedgerSheet: View {
    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    let day: Date
    /// Steps to another day without closing the sheet.
    let onDay: (Date) -> Void
    let onClose: () -> Void

    @State private var onlyMoved = false

    /// Read straight off the store rather than snapshotted into `@State`: it is
    /// `@Observable`, so a payment taken while this is open redraws the row it
    /// settled. Android has to key this on the shop state by hand.
    private var ledger: DayLedger { store.dayLedger(day) }

    /// No stepping into tomorrow, for the reason the day sheet gives: a day that
    /// has not happened has nothing on it, and an owner who lands there wonders
    /// whether the app has lost the day's work.
    private var isToday: Bool {
        Calendar.current.startOfDay(for: day) >= Calendar.current.startOfDay(for: .now)
    }

    var body: some View {
        let ledger = self.ledger
        let shown = onlyMoved ? ledger.busyRows : ledger.rows

        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(title: Loc.dayBalances, onClose: onClose)

            // `‹ 22 August 2026 ›` — the date reads as the thing being stepped
            // through rather than as a caption with two buttons parked beside it.
            HStack {
                Button { onDay(step(-1)) } label: { Glyph(Icon.stepBack, size: 18) }
                    .buttonStyle(.iconOnly)
                    .foregroundStyle(Nocturne.accent)
                    .accessibilityLabel(Loc.previousDay)

                Text(Loc.longDate(day))
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)

                if isToday {
                    // Holds the date in the middle on the day there is no forward
                    // arrow to draw, so stepping back does not nudge it sideways.
                    Color.clear.frame(width: Metrics.minimumTouchTarget, height: Metrics.minimumTouchTarget)
                } else {
                    Button { onDay(step(1)) } label: { Glyph(Icon.stepForward, size: 18) }
                        .buttonStyle(.iconOnly)
                        .foregroundStyle(Nocturne.accent)
                        .accessibilityLabel(Loc.nextDay)
                }
            }
            .padding(.bottom, 12)

            // How much of the page is the day and how much is the roll-call, said
            // before the owner starts counting names.
            HStack {
                Text(
                    ledger.busyRows.isEmpty
                        ? Loc.ledgerNobodyMoved
                        : Loc.ledgerBusyCount(ledger.busyRows.count)
                )
                .nocturneText(.meta)
                Spacer(minLength: 0)
                // Offered only when it would change what is on the screen. On a
                // day where everybody moved, or nobody did, the switch is a
                // control that does nothing.
                if !ledger.busyRows.isEmpty, ledger.busyRows.count < ledger.rows.count {
                    Button(onlyMoved ? Loc.ledgerShowAll : Loc.ledgerShowMoved) {
                        onlyMoved.toggle()
                    }
                    .buttonStyle(GhostButtonStyle(fontSize: 12, tint: Nocturne.accent400))
                }
            }
            .padding(.bottom, 10)

            if ledger.isEmpty {
                Text(Loc.ledgerNoCustomers)
                    .nocturneText(.meta)
                    .padding(.vertical, 14)
            } else {
                headings

                ScrollView {
                    LazyVStack(spacing: 2) {
                        ForEach(shown) { row in
                            LedgerRow(row: row, currency: currency)
                        }
                    }
                }

                totals(ledger)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The column headings, in the order the row below draws them.
    private var headings: some View {
        HStack(alignment: .bottom, spacing: 0) {
            heading(Loc.customersTitle, align: .leading)
                .frame(maxWidth: .infinity, alignment: .leading)
            heading(Loc.ledgerInvoiced)
            heading(Loc.ledgerReceived)
            heading(Loc.ledgerOldBalance)
            heading(Loc.ledgerCurrentBalance)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 6)
    }

    private func heading(_ text: String, align: Alignment = .trailing) -> some View {
        Text(text)
            .font(NocturneType.inter(10))
            .foregroundStyle(Nocturne.neutral600)
            .lineLimit(1)
            .frame(width: align == .trailing ? DayLedgerColumn.width : nil, alignment: align)
    }

    /// The columns added up, so the page can be checked against what the shop is
    /// owed altogether.
    private func totals(_ ledger: DayLedger) -> some View {
        HStack(spacing: 0) {
            Spacer(minLength: 0)
            LedgerFigure(amount: ledger.invoiced == 0 ? nil : ledger.invoiced, currency: currency, tint: Nocturne.text, bold: true)
            LedgerFigure(amount: ledger.received == 0 ? nil : ledger.received, currency: currency, tint: Nocturne.accent400, bold: true)
            LedgerFigure(amount: ledger.openingBalance, currency: currency, tint: Nocturne.neutral500, always: true, bold: true)
            LedgerFigure(amount: ledger.closingBalance, currency: currency, tint: Nocturne.accent400, always: true, bold: true)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 9)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .padding(.top, 6)
    }

    private func step(_ by: Int) -> Date {
        Calendar.current.date(byAdding: .day, value: by, to: day) ?? day
    }
}

/// One customer's line.
///
/// A quiet row is drawn grey and its two movement columns are left empty rather
/// than filled with zeroes: the owner asked for the whole roll-call, and a page of
/// a hundred `0.00`s is a page whose real figures have nowhere to stand out from.
private struct LedgerRow: View {
    let row: DayLedger.Row
    let currency: Currency

    var body: some View {
        let moved = !row.isQuiet

        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Text(row.name)
                    .font(NocturneType.inter(12))
                    .foregroundStyle(moved ? Nocturne.text : Nocturne.neutral500)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                LedgerFigure(amount: row.invoiced == 0 ? nil : row.invoiced, currency: currency, tint: Nocturne.text)
                LedgerFigure(amount: row.received == 0 ? nil : row.received, currency: currency, tint: Nocturne.accent400)
                LedgerFigure(amount: row.openingBalance, currency: currency, tint: Nocturne.neutral500, always: true)
                LedgerFigure(
                    amount: row.closingBalance,
                    currency: currency,
                    tint: row.closingBalance > 0 ? Nocturne.accent400 : Nocturne.neutral500,
                    always: true
                )
            }

            // The two things a five-column table cannot hold, said under the name
            // of the row they belong to so it still adds up on the page.
            if !notes.isEmpty {
                Text(notes.joined(separator: " · "))
                    .font(NocturneType.inter(10.5))
                    .foregroundStyle(Nocturne.neutral500)
                    .padding(.top, 2)
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            if moved {
                RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous)
                    .fill(Nocturne.surface)
            }
        }
    }

    private var notes: [String] {
        var lines: [String] = []
        if row.credited != 0 { lines.append("\(Loc.ledgerCredited) \(Money.text(row.credited, in: currency))") }
        if row.transferredIn != 0 { lines.append("\(Loc.ledgerMoved) +\(Money.text(row.transferredIn, in: currency))") }
        if row.transferredOut != 0 { lines.append("\(Loc.ledgerMoved) −\(Money.text(row.transferredOut, in: currency))") }
        return lines
    }
}

/// One money column. Nil is drawn as nothing at all, never as a zero.
private struct LedgerFigure: View {
    let amount: Double?
    let currency: Currency
    let tint: Color
    var always = false
    var bold = false

    var body: some View {
        Text(text)
            .font(NocturneType.inter(bold ? 11.5 : 11))
            .foregroundStyle(tint)
            .lineLimit(1)
            .frame(width: DayLedgerColumn.width, alignment: .trailing)
    }

    private var text: String {
        guard let amount else { return "" }
        if !always, amount == 0 { return "" }
        return Money.amount(amount, in: currency)
    }
}

/// The width of one money column.
///
/// Fixed rather than flexible so the figures line up down the page — a column that
/// sized itself to its own contents would step in and out as the owner scrolls,
/// which is the one thing that makes a table of numbers unreadable.
private enum DayLedgerColumn {
    static let width: CGFloat = 62
}
