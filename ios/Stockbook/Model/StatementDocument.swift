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

    /// One line of the activity table: what, when, how much, and where it left
    /// the account.
    struct ActivityRow: Equatable, Identifiable {
        let date: String
        let transaction: String
        let amount: String
        let balance: String
        var deduction: Bool = false

        var id: String { "\(date)-\(transaction)-\(amount)-\(balance)" }
    }

    static func make(
        statement: Statement,
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil
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
            summaryTitle: strings.accountSummaryTill(
                strings.longDate(statement.range.end.addingTimeInterval(-1))
            ),
            summaryRows: summary,
            activityTitle: strings.accountActivity,
            columnHeadings: [
                strings.columnDate,
                strings.columnTransaction,
                strings.columnAmount,
                strings.columnBalance
            ],
            activityRows: statement.entries.enumerated().map { index, entry in
                let settles = entry.charge == 0
                return ActivityRow(
                    date: strings.shortDate(entry.date),
                    transaction: describe(entry, strings),
                    amount: Money.text(settles ? entry.settledAtOnce : entry.charge, in: money),
                    balance: Money.text(statement.runningBalances[index], in: money),
                    deduction: settles
                )
            },
            closingLabel: strings.balanceDue,
            closingValue: Money.text(statement.closingBalance, in: money)
        )
    }

    /// What the Transaction column calls each row.
    ///
    /// The paper's own number wherever there is one — that is the whole point of
    /// a statement somebody is checking against their file of invoices.
    private static func describe(_ entry: Statement.Entry, _ strings: Strings) -> String {
        switch entry {
        case .bill(let bill): bill.reference(strings)
        case .purchase(let purchase): purchase.reference(strings)
        case .creditNote(let note): note.reference(strings)
        case .payment, .supplierPayment: strings.paymentLabel
        }
    }
}
