import SwiftUI

/// What a delivery reads as on a statement, on screen and in the text copy alike.
///
/// A supplier's bill entered as a figure names nothing, and "× 0" of nothing is
/// not a line anybody can read — so it says what the paper is instead.
///
/// The describing itself is `Purchase.described`, on the model, where the Kotlin
/// twin has the identical property: a statement is a document the two apps must
/// word the same way.
@MainActor
private func deliveryDetail(_ purchase: Purchase) -> String {
    purchase.described ?? Loc.supplierBillTitle
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
    @State private var choice: PeriodChoice = .thisMonth

    @State private var from = Calendar.current.date(byAdding: .month, value: -1, to: .now) ?? .now
    @State private var to = Date.now

    private var period: StatementPeriod { choice.period(from: from, to: to) }

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
                    PeriodPicker(choice: $choice, from: $from, to: $to).padding(.bottom, 10)

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
        // Presented off the file rather than a flag, so the sheet cannot open
        // before the PDF exists — and closing it drops the URL, so the next tap
        // renders the period the owner is looking at *then*.
        .sheet(item: $pdfFile) { file in
            ShareSheet(url: file.url)
                .presentationDetents([.medium, .large])
        }
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
            // Their own lines, and only where there is one — for the reason the
            // credit notes have theirs: a transfer in is not something the shop
            // invoiced and a transfer out is not money it took.
            if statement.transferredIn > 0 {
                row(Loc.transferredInLabel, Money.text(statement.transferredIn, in: currency))
            }
            if statement.transferredOut > 0 {
                row(Loc.transferredOutLabel, Money.text(statement.transferredOut, in: currency))
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
        entryLine(entry, balance: balance)
        .contentShape(Rectangle())
        .onTapGesture {
            // Both of the things this screen can correct open the same way: tap
            // the row, get the sheet it was written on, with removal one button
            // inside it. They used to differ — a credit note opened, a payment
            // armed a delete — which meant the same gesture did two things
            // depending on which row it landed on.
            switch entry {
            case .creditNote(let note):
                guard let customer = store.customer(key: note.customerKey) else { return }
                router.creditNoteFor = CreditNoteTarget(customer: customer, note: note)
            case .payment(let payment):
                // The customer comes with it: the sheet shows what will still be
                // owed once the correction is saved, which needs the whole
                // account rather than the one payment.
                guard let customer = store.customer(key: payment.customerKey) else { return }
                router.editingPayment = payment
                router.paymentFor = customer
            case .supplierPayment(let payment):
                guard let supplier = store.supplier(key: payment.supplierKey) else { return }
                router.editingSupplierPayment = payment
                router.supplierPaymentFor = supplier
            case .transfer:
                // Nothing to reopen. A transfer is an amount and a reason, and
                // correcting it means removing it and moving the right figure —
                // which the account screen it came from already offers.
                break
            case .bill, .purchase:
                // Not corrected from here. Removing one puts stock back on the
                // shelf, and offering that from a row on a document somebody is
                // reading would be a second, worse route than the opened document.
                break
            }
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
                case .transfer(let transfer, _, _):
                    // Named by the account at the other end — see `reference` —
                    // because there is no number to show. The reason is drawn
                    // under it for the same purpose a credit note's is: a figure
                    // the customer cannot place is one they come and ask about.
                    Text(reference(entry))
                        .font(NocturneType.inter(13))
                    if let note = transfer.note {
                        Text(note).nocturneText(.meta).lineLimit(2)
                    }
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
        // The sign says which way this account moved, not which way the money
        // went — no money went anywhere. Out of this account is a reduction like
        // a payment; into it is a charge like a bill.
        case .transfer(let transfer, let outgoing, _):
            outgoing
                ? "− \(Money.text(transfer.amount, in: currency))"
                : Money.text(transfer.amount, in: currency)
        }
    }

    private func amountTint(_ entry: Statement.Entry) -> Color {
        switch entry {
        case .bill, .purchase: Nocturne.text
        case .payment, .supplierPayment, .creditNote: Nocturne.accent400
        // Whichever way this one moves the account, so it reads like the charge
        // or the settlement it is.
        case .transfer(_, let outgoing, _): outgoing ? Nocturne.accent400 : Nocturne.text
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

    /// One way out, and it is the document. The app makes no network call; the OS
    /// does whatever the owner picks.
    ///
    /// There was a plain-text share beside this for a quick message, and it was a
    /// second rendering of the same figures with none of the page's wording,
    /// arithmetic or letterhead — a statement somebody could quote back that the
    /// app would not recognise as its own.
    private func shareRow(_ statement: Statement) -> some View {
        Button(Loc.sharePdf) { makePDF(statement) }
            .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))
            .padding(.top, 10)
    }

    /// Renders the document to a file and hands the URL to the share sheet.
    ///
    /// A failure leaves `pdfFile` nil and nothing opens, which is the honest
    /// outcome: there is no half-written statement worth offering, and the
    /// figures are still on screen.
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
}
