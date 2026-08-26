import Foundation

/// A statement laid out as a printable document: every label and every figure,
/// already worded and already formatted.
///
/// This exists so the PDF says the same thing on both phones. Drawing is
/// unavoidably platform work — Core Graphics here, a `Canvas` on Android — but
/// *what* is drawn does not have to be, and two hand-written layouts would drift
/// the first time either was corrected. Each platform walks this structure and
/// draws boxes; neither decides what a row is called or what goes in it.
///
/// It is also the only part of the document that can be checked without a
/// device: the arithmetic behind it is `Statement`'s, and the wording is here.
struct StatementDocument: Equatable {

    /// Top left: who is sending it.
    let shopName: String
    let shopAddressLines: [String]

    /// Top right: who it is for.
    let addressedToLabel: String
    let partyName: String
    let partyLines: [String]

    /// The boxed summary, in the order it prints.
    let summaryTitle: String
    let summaryRows: [Row]

    /// The table below it.
    let activityTitle: String
    let columnHeadings: [String]
    let activityRows: [ActivityRow]

    /// The figure the whole document exists to state, repeated under the table.
    let closingLabel: String
    let closingValue: String

    /// One line of the summary box.
    ///
    /// `deduction` is what puts a figure in brackets. Accounting convention, and
    /// the one the shop's own supplier statements use: `(SAR 530.00)` reads as
    /// money coming off, where a bare minus sign in front of a currency symbol
    /// reads as a typo.
    struct Row: Equatable {
        let label: String
        let value: String
        var deduction: Bool = false
    }

    /// One line of the activity table: what it was and when, then the money in
    /// whichever of the two columns it belongs to, then where the account stood.
    ///
    /// **Two money columns, filled independently.** What the account was charged
    /// goes in `charge` and what came off it goes in `settled`. A payment or a
    /// credit note fills only the second; a bill on credit fills only the first —
    /// but **a bill paid at the counter fills both**, and it has to. Its charge
    /// and its receipt happened in the same moment, so the balance beside it does
    /// not move, and a row showing a charge against an unmoved balance with an
    /// empty column next to it reads as an arithmetic mistake. It was one: the
    /// money taken at the till was simply missing from the page.
    ///
    /// This is what replaced a single Amount column with brackets round the
    /// deductions — the position now says which way the money went, where a
    /// bracketed figure needed a convention explained to whoever was reading it.
    ///
    /// `details` carries the kind as well as the number — `Invoice #6356`, not
    /// `6356` — because a credit note and a payment both land in `settled`, and
    /// without the word the customer cannot tell which of the two took the money
    /// off their account.
    struct ActivityRow: Equatable, Identifiable {
        /// `Invoice #6356 · 19/05/2026` — what it was, and the day it happened.
        let details: String
        /// What was charged. Empty on a row that settles.
        let charge: String
        /// What came off: a payment, or a credit note. Empty on a charge.
        let settled: String
        let balance: String

        var id: String { "\(details)-\(charge)-\(settled)-\(balance)" }
    }


    static func make(
        statement: Statement,
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil,
        now: Date = .now
    ) -> StatementDocument {
        let money = currency ?? settings.currency
        let isSupplier = statement.party.isSupplier

        // The summary is only ever as long as it needs to be. A shop that has
        // issued no credit notes should not read a row of zeroes and learn to
        // skip the block — the same rule the on-screen total already follows.
        var summary: [Row] = [
            Row(label: strings.openingBalance, value: Money.text(statement.openingBalance, in: money)),
            Row(
                label: isSupplier ? strings.purchasedInPeriod : strings.billedInPeriod,
                value: Money.text(statement.billed, in: money)
            ),
            Row(
                label: isSupplier ? strings.paidOutInPeriod : strings.receivedInPeriod,
                value: Money.text(statement.received, in: money),
                deduction: true
            )
        ]
        if statement.credited > 0 {
            summary.append(
                Row(
                    label: strings.creditNotes,
                    value: Money.text(statement.credited, in: money),
                    deduction: true
                )
            )
        }
        // Their own rows, drawn only where there is one — for the reason the
        // credit notes have theirs: a transfer in is not something the shop
        // invoiced and a transfer out is not money it took, so folding either
        // into a trading figure would make that figure mean two things on a
        // document somebody is handed.
        if statement.transferredIn > 0 {
            summary.append(
                Row(
                    label: strings.transferredInLabel,
                    value: Money.text(statement.transferredIn, in: money)
                )
            )
        }
        if statement.transferredOut > 0 {
            summary.append(
                Row(
                    label: strings.transferredOutLabel,
                    value: Money.text(statement.transferredOut, in: money),
                    deduction: true
                )
            )
        }

        return StatementDocument(
            shopName: settings.ownerName,
            shopAddressLines: settings.shopAddress
                .split(separator: "\n", omittingEmptySubsequences: false)
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty },
            addressedToLabel: strings.accountStatementFor,
            partyName: statement.party.name,
            partyLines: [statement.party.place, statement.party.phone]
                .compactMap { $0 }
                .filter { !$0.isBlank },
            summaryTitle: strings.accountSummaryTill(strings.longDate(statement.range.asOf(now))),
            summaryRows: summary,
            activityTitle: strings.accountActivity,
            // Four headings for four columns, and the middle two flip with the
            // direction: money the shop is owed was *received*, money it owes was
            // *paid*, and one pair of words for both would be backwards on one of
            // the two documents.
            columnHeadings: [
                isSupplier ? strings.columnBillReceipt : strings.columnInvoiceReceipt,
                isSupplier ? strings.columnBillAmount : strings.columnInvoiceAmount,
                isSupplier ? strings.columnPaidAmount : strings.columnReceivedAmount,
                strings.columnBalance
            ],
            activityRows: statement.entries.enumerated().map { index, entry in
                // Each column asks its own question of the entry rather than the
                // two sharing one answer. A bill settled at the counter has both a
                // charge and a receipt, and reading it as one or the other is what
                // hid every over-the-counter payment.
                ActivityRow(
                    details: strings.referenceOn(reference(entry, strings), date: strings.shortDate(entry.date)),
                    charge: entry.charge > 0 ? Money.text(entry.charge, in: money) : "",
                    settled: entry.settledAtOnce > 0 ? Money.text(entry.settledAtOnce, in: money) : "",
                    balance: Money.text(statement.runningBalances[index], in: money)
                )
            },
            closingLabel: strings.balanceDue,
            closingValue: Money.text(statement.closingBalance, in: money)
        )
    }

    /// What the Transaction column calls each row: **the kind of document, then
    /// its number**.
    ///
    /// "06011" alone tells somebody checking against their own file nothing
    /// about what 06011 *is*, and the books are numbered separately — invoice
    /// 130 and credit note 130 are different pieces of paper. Where a record
    /// carries no number of its own the type is still named, which is the honest
    /// answer rather than a blank cell.
    ///
    /// Not private: the on-screen statement calls it too. The two are read side
    /// by side when somebody checks a PDF against the app, and a row named two
    /// different ways is a row they reconcile by eye.
    static func reference(_ entry: Statement.Entry, _ strings: Strings) -> String {
        switch entry {
        case .bill(let bill):
            if let no = bill.invoiceNo, !no.isBlank { return strings.invoiceRef(no) }
            return strings.billNumber(bill.number)
        case .purchase(let purchase):
            if let no = purchase.invoiceNo, !no.isBlank { return strings.deliveryRef(no) }
            return strings.purchaseLabel
        case .creditNote(let note):
            if let no = note.noteNo, !no.isBlank { return strings.creditNoteRef(no) }
            return strings.creditNoteLabel
        case .payment(let payment):
            if let no = payment.paymentNo, !no.isBlank { return strings.paymentRef(no) }
            return strings.paymentLabel
        case .supplierPayment(let payment):
            if let no = payment.paymentNo, !no.isBlank { return strings.paymentRef(no) }
            return strings.paymentLabel
        // No number to show — nothing was written for it — so the row names the
        // account at the other end instead. "Transferred" alone would leave the
        // customer holding a figure they cannot place.
        case .transfer(_, let outgoing, let otherName):
            return outgoing ? strings.transferredTo(otherName) : strings.transferredFrom(otherName)
        }
    }
}
