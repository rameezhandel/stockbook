import SwiftUI

/// What a delivery reads as on a statement, on screen and in the text copy alike.
///
/// `Purchase.name` became optional when a supplier's bill stopped having to name
/// a product, and "× 0" of nothing is not a line anybody can read. It lives here
/// rather than on `Purchase` because the model has a Kotlin twin that carries no
/// such property, and the two are not allowed to drift.
@MainActor
private func deliveryDetail(_ purchase: Purchase) -> String {
    guard purchase.isItemised, let name = purchase.name else { return Loc.supplierBillTitle }
    return "\(name) × \(purchase.qty)"
}

/// One customer's account over a period, as a document.
///
/// Full screen rather than a sheet, for two reasons: it can run to a page, and it
/// is the one thing in this app the owner may well turn round and show the person
/// it is about. Everything on it is drawn from `Statement`, which does the
/// arithmetic and is tested against literal figures.
struct StatementScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    /// Set once the PDF is on disk; the share sheet is presented off it.
    @State private var pdfFile: StatementFile?

    /// Whose account: a customer key, or a supplier key with `isSupplier` set.
    let partyKey: String

    /// Which side of the book. One screen for both, because a statement is a
    /// statement — see `Statement.make`, where the same arithmetic serves both —
    /// and two screens would drift the moment either was corrected.
    var isSupplier = false
    let onClose: () -> Void

    /// Which chip is on.
    ///
    /// Held as the *choice* rather than as a `StatementPeriod`, because a period
    /// carries a date: `.month(.now)` built for the chip would never equal the
    /// `.month(.now)` built a moment earlier and stored, so no chip would ever
    /// look selected. The period is derived from this instead, which also means
    /// "this month" is still this month if the app is left open past midnight on
    /// the 1st.
    private enum Choice: Equatable {
        case thisMonth, lastMonth, thisYear, dates
    }

    @State private var choice: Choice = .thisMonth

    /// The payment the owner has tapped, waiting for a second tap to remove.
    ///
    /// A mistyped payment would otherwise misstate a customer's balance for good
    /// — and unlike a bill, a payment has nothing to void: it is one number and
    /// one date, so the honest correction is to delete it and enter it again.
    @State private var deleting: UUID?
    @State private var from = Calendar.current.date(byAdding: .month, value: -1, to: .now) ?? .now
    @State private var to = Date.now

    private var period: StatementPeriod {
        switch choice {
        case .thisMonth: .thisMonth()
        case .lastMonth: .lastMonth()
        case .thisYear: .thisYear()
        case .dates: .custom(from: from, to: to)
        }
    }

    private var statement: Statement? {
        isSupplier
            ? store.statementForSupplier(key: partyKey, period: period)
            : store.statement(forCustomer: partyKey, period: period)
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Loc.statement, subtitle: statement?.party.name) {
                Button(Loc.done, action: onClose)
                    .buttonStyle(GhostButtonStyle(fontSize: 12.5))
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    periodChips.padding(.bottom, 10)

                    if choice == .dates {
                        dateRangeCard.padding(.bottom, 10)
                    }

                    if let statement {
                        contactLine(statement.party)
                        document(statement)
                        shareRow(statement)
                    }
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 24)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Nocturne.bg.ignoresSafeArea())
        .motion(Motion.list, value: choice)
        // A period change re-draws the whole document; a row still armed for
        // deletion in the old one would be armed against a row that has moved.
        .onChange(of: choice) { _, _ in deleting = nil }
        // Presented off the file rather than a flag, so the sheet cannot open
        // before the PDF exists — and closing it drops the URL, so the next tap
        // renders the period the owner is looking at *then*.
        .sheet(item: $pdfFile) { file in
            ShareSheet(url: file.url)
                .presentationDetents([.medium, .large])
        }
    }

    // MARK: Period

    private var periodChips: some View {
        // Three taps that answer almost every question, and a fourth for the
        // month-end that does not start on the 1st.
        FlowLayout(spacing: 6) {
            chip(Loc.thisMonth, isOn: choice == .thisMonth) { choice = .thisMonth }
            chip(Loc.lastMonth, isOn: choice == .lastMonth) { choice = .lastMonth }
            chip(Loc.thisYear, isOn: choice == .thisYear) { choice = .thisYear }
            chip(Loc.chooseDates, isOn: choice == .dates) { choice = .dates }
        }
    }

    private func chip(_ title: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(NocturneType.inter(12))
                .foregroundStyle(isOn ? Nocturne.bg : Nocturne.accent)
                .padding(.horizontal, 11)
                .padding(.vertical, 5)
                .frame(minHeight: 32)
                .background(
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(isOn ? Nocturne.accent : Color.clear)
                )
                .hairline(Nocturne.accent, radius: 7)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var dateRangeCard: some View {
        VStack(spacing: 10) {
            DatePicker(Loc.fromDate, selection: $from, displayedComponents: .date)
            DatePicker(Loc.toDate, selection: $to, displayedComponents: .date)
        }
        .datePickerStyle(.compact)
        .font(NocturneType.inter(13))
        .tint(Nocturne.accent)
        .padding(12)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .hairline(radius: Metrics.cardRadius)
        // No `onChange` needed: `period` is derived, so moving either picker
        // rebuilds the statement on the next pass. Whichever way round they were
        // dragged, `StatementPeriod` sorts out.
    }

    // MARK: The document

    @ViewBuilder
    private func contactLine(_ party: StatementParty) -> some View {
        let details = [party.phone, party.place].compactMap { $0 }
        if !details.isEmpty {
            Text(details.joined(separator: " · "))
                .nocturneText(.meta)
                .padding(.bottom, 8)
        }
    }

    private func document(_ statement: Statement) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Kicker(Loc.dateSpan(from: Loc.longDate(statement.range.start), to: Loc.longDate(lastDay(of: statement.range))))
                .padding(.bottom, 12)

            row(Loc.openingBalance, Money.text(statement.openingBalance, in: currency), muted: true)

            FadedRule().padding(.vertical, 10)

            if statement.isEmpty {
                Text(Loc.nothingInThisPeriod)
                    .nocturneText(.meta)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 14)
            } else {
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(Array(statement.entries.enumerated()), id: \.element.id) { index, entry in
                        entryRow(entry, balance: statement.runningBalances[index])
                    }
                }
            }

            FadedRule().padding(.vertical, 10)

            row(chargedLabel(statement), Money.text(statement.billed, in: currency))
            row(settledLabel(statement), Money.text(statement.received, in: currency))
            // Its own line, and only where there is one. Credit is not cash, and
            // a row saying "0.00 credited" on every statement teaches people to
            // stop reading the ones that are not zero.
            if statement.credited > 0 {
                row(Loc.creditNotes, Money.text(statement.credited, in: currency))
            }

            FadedRule().padding(.vertical, 10)

            // The number the whole document exists to state.
            HStack(alignment: .firstTextBaseline) {
                Text(Loc.closingBalance)
                    .font(NocturneType.inter(13, .medium))
                    .foregroundStyle(Nocturne.text)
                Spacer(minLength: 10)
                Text(closingText(statement))
                    .nocturneText(.bigNumber(22))
                    .foregroundStyle(statement.closingBalance > 0 ? Nocturne.accent : Nocturne.text)
                    .contentTransition(.numericText())
            }
            .motion(Motion.numbers, value: statement.closingBalance)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.statRadius, style: .continuous))
        .hairline(radius: Metrics.statRadius)
    }

    private func closingText(_ statement: Statement) -> String {
        if statement.closingBalance < 0 {
            return Loc.inAdvance(Money.text(-statement.closingBalance, in: currency))
        }
        return Money.text(statement.closingBalance, in: currency)
    }

    private func row(_ label: String, _ value: String, muted: Bool = false) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label).nocturneText(muted ? .meta : .body)
            Spacer(minLength: 10)
            Text(value)
                .font(NocturneType.inter(13))
                .foregroundStyle(muted ? Nocturne.neutral500 : Nocturne.text)
        }
    }

    @ViewBuilder
    private func entryRow(_ entry: Statement.Entry, balance: Double) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            entryLine(entry, balance: balance)

            // Only a payment. Correcting or removing a bill or a delivery lives
            // inside the opened document where it belongs — offering it beside the
            // line here would be a second, worse route to the same history.
            if let id = paymentID(entry), deleting == id {
                Button(Loc.deleteThisPayment) {
                    if isSupplier {
                        store.deleteSupplierPayment(id: id)
                    } else {
                        store.deletePayment(id: id)
                    }
                    deleting = nil
                }
                .buttonStyle(.ghostMuted)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            // A credit note opens for correction instead of arming a delete,
            // because it is a document with a number on it rather than a bare
            // figure: what wants fixing is usually the amount or the reason, and
            // removing it outright is one button inside the sheet that opens.
            if case .creditNote(let note) = entry {
                guard let customer = store.customer(key: note.customerKey) else { return }
                router.creditNoteFor = CreditNoteTarget(customer: customer, note: note)
                return
            }
            guard let id = paymentID(entry) else { return }
            withAnimation(Metrics.quick) {
                deleting = deleting == id ? nil : id
            }
        }
    }

    /// Either kind of payment can be armed for deletion; nothing else can.
    private func paymentID(_ entry: Statement.Entry) -> UUID? {
        switch entry {
        case .payment(let payment): payment.id
        case .supplierPayment(let payment): payment.id
        case .bill, .purchase, .creditNote: nil
        }
    }

    @ViewBuilder
    private func entryLine(_ entry: Statement.Entry, balance: Double) -> some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                switch entry {
                case .bill(let bill):
                    Text(reference(entry))
                        .font(NocturneType.inter(13))
                    Text(bill.summary)
                        .nocturneText(.meta)
                        .lineLimit(2)
                case .payment(let payment):
                    HStack(spacing: 5) {
                        Glyph(Icon.confirm, size: 10).foregroundStyle(Nocturne.accent400)
                        Text(reference(entry))
                            .font(NocturneType.inter(13))
                            .foregroundStyle(Nocturne.accent400)
                    }
                    if let note = payment.note {
                        Text(note).nocturneText(.meta)
                    }
                case .creditNote(let note):
                    Text(reference(entry))
                        .font(NocturneType.inter(13))
                        .foregroundStyle(Nocturne.accent400)
                    // Why it was written, where the owner said. On a document
                    // somebody is checking against their own paper, "returned,
                    // damaged" is the difference between a figure they
                    // recognise and one they have to go and ask about.
                    if let reason = note.reason {
                        Text(reason).nocturneText(.meta).lineLimit(2)
                    }
                case .purchase(let purchase):
                    Text(reference(entry))
                        .font(NocturneType.inter(13))
                    // The product and how many of it: a delivery note's whole
                    // content, on one line, since a purchase carries one product.
                    Text(deliveryDetail(purchase))
                        .nocturneText(.meta)
                        .lineLimit(2)
                case .supplierPayment(let payment):
                    HStack(spacing: 5) {
                        Glyph(Icon.confirm, size: 10).foregroundStyle(Nocturne.accent400)
                        Text(reference(entry))
                            .font(NocturneType.inter(13))
                            .foregroundStyle(Nocturne.accent400)
                    }
                    if let note = payment.note {
                        Text(note).nocturneText(.meta)
                    }
                }
                Text(Loc.longDate(entry.date)).nocturneText(.meta)
            }

            Spacer(minLength: 6)

            VStack(alignment: .trailing, spacing: 2) {
                Text(amountText(entry))
                    .font(NocturneType.inter(13))
                    .foregroundStyle(amountTint(entry))
                // The running balance beside every line: the column that turns a
                // list into a statement somebody can check.
                Text(Money.text(balance, in: currency))
                    .nocturneText(.meta)
            }
        }
    }

    /// "Billed" and "Received" are the customer's words. On a supplier's account
    /// they read backwards — the shop is the one being billed — so the two
    /// figures are named by which way the account runs.
    private func chargedLabel(_ statement: Statement) -> String {
        statement.party.isSupplier ? Loc.purchasedInPeriod : Loc.billedInPeriod
    }

    private func settledLabel(_ statement: Statement) -> String {
        statement.party.isSupplier ? Loc.paidOutInPeriod : Loc.receivedInPeriod
    }

    private func amountText(_ entry: Statement.Entry) -> String {
        switch entry {
        case .bill(let bill): Money.text(bill.total, in: currency)
        case .purchase(let purchase): Money.text(purchase.total, in: currency)
        // A minus sign on both kinds of payment: it is what the account moves by,
        // and on a supplier's statement that is money leaving rather than arriving.
        case .creditNote(let note): "− \(Money.text(note.total, in: currency))"
        case .payment(let payment): "− \(Money.text(payment.amount, in: currency))"
        case .supplierPayment(let payment): "− \(Money.text(payment.amount, in: currency))"
        }
    }

    private func amountTint(_ entry: Statement.Entry) -> Color {
        switch entry {
        case .bill, .purchase: Nocturne.text
        case .payment, .supplierPayment, .creditNote: Nocturne.accent400
        }
    }

    /// What a row is called on screen — the printed statement's own rule,
    /// borrowed rather than restated.
    private func reference(_ entry: Statement.Entry) -> String {
        StatementDocument.reference(entry, Loc)
    }

    /// The last day this statement can honestly say it covers — the range's own
    /// rule, so the screen and the PDF are headed with the same date.
    private func lastDay(of range: StatementRange) -> Date {
        range.asOf()
    }

    // MARK: Sharing

    /// Two ways out, side by side. The app makes no network call either way; the
    /// OS does whatever the owner picks.
    ///
    /// Plain text is the quick one — a WhatsApp line, which is how a statement
    /// usually reaches a customer here. The PDF is the document somebody files
    /// beside their supplier statements, and it is the same figures either way.
    private func shareRow(_ statement: Statement) -> some View {
        HStack(spacing: 8) {
            ShareLink(item: plainText(statement)) {
                Label(Loc.share, systemImage: Icon.share)
            }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))

            Button(Loc.sharePdf) { makePDF(statement) }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))
        }
        .padding(.top, 10)
    }

    /// Renders the document to a file and hands the URL to the share sheet.
    ///
    /// A failure leaves `pdfFile` nil and nothing opens, which is the honest
    /// outcome: there is no half-written statement worth offering, and the owner
    /// still has the plain-text button beside it.
    private func makePDF(_ statement: Statement) {
        let document = StatementDocument.make(
            statement: statement,
            settings: store.settings,
            strings: Loc
        )
        let slug = document.partyName
            .replacingOccurrences(of: "[^A-Za-z0-9]+", with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
            .lowercased()
        guard let url = try? StatementPDF.write(
            document,
            fileName: Loc.statementFileName(name: slug, date: Copy.fileDate(.now))
        ) else { return }
        pdfFile = StatementFile(url: url)
    }

    private func plainText(_ statement: Statement) -> String {
        var lines: [String] = []
        if !store.settings.ownerName.isBlank { lines.append(store.settings.ownerName) }
        lines.append("\(Loc.statement) — \(statement.party.name)")
        lines.append(Loc.dateSpan(from: Loc.longDate(statement.range.start), to: Loc.longDate(lastDay(of: statement.range))))
        lines.append("")
        lines.append("\(Loc.openingBalance): \(Money.text(statement.openingBalance, in: currency))")

        for (index, entry) in statement.entries.enumerated() {
            let balance = Money.text(statement.runningBalances[index], in: currency)
            switch entry {
            case .bill(let bill):
                lines.append("\(Loc.longDate(bill.createdAt))  \(reference(entry))  \(Money.text(bill.total, in: currency))  →  \(balance)")
            case .payment(let payment):
                lines.append("\(Loc.longDate(payment.receivedAt))  \(reference(entry))  − \(Money.text(payment.amount, in: currency))  →  \(balance)")
            case .creditNote(let note):
                lines.append("\(Loc.longDate(note.issuedAt))  \(reference(entry))  − \(Money.text(note.total, in: currency))  →  \(balance)")
            case .purchase(let purchase):
                lines.append("\(Loc.longDate(purchase.createdAt))  \(reference(entry))  \(deliveryDetail(purchase))  \(Money.text(purchase.total, in: currency))  →  \(balance)")
            case .supplierPayment(let payment):
                lines.append("\(Loc.longDate(payment.paidAt))  \(reference(entry))  − \(Money.text(payment.amount, in: currency))  →  \(balance)")
            }
        }

        lines.append("")
        lines.append("\(chargedLabel(statement)): \(Money.text(statement.billed, in: currency))")
        lines.append("\(settledLabel(statement)): \(Money.text(statement.received, in: currency))")
        if statement.credited > 0 {
            lines.append("\(Loc.creditNotes): \(Money.text(statement.credited, in: currency))")
        }
        lines.append("\(Loc.closingBalance): \(closingText(statement))")
        return lines.joined(separator: "\n")
    }
}
