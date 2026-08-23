import Foundation

/// Everyone who owes the shop money, and how much, on one page.
///
/// **This is the owner's own list, and it is the one document in the app that
/// must never be shown to a customer.** A statement is handed across the counter
/// on purpose — it says what one person owes, to that person. This says what
/// *everybody* owes, and letting Ahmed see that Khalid is four thousand behind is
/// not an untidy page, it is a breach. Nothing renders it beside a customer's own
/// documents, and the title says whose list it is.
///
/// **A balance, not a period.** Every other document here takes a
/// `StatementPeriod`; what is outstanding is true at a moment and meaningless
/// over a span. That is why the header carries the day it was made rather than a
/// range, and it is the whole of what stops a printout from last month reading as
/// this morning's.
///
/// Laid out here for the reason `StatementDocument` is: drawing is platform work,
/// but *what* is drawn is not, and two hand-written layouts drift the first time
/// either is corrected. The Kotlin twin is `OutstandingDocument.kt`, and
/// `OutstandingDocumentTests` is what holds the two together.
struct OutstandingDocument: Equatable {

    let shopName: String
    /// What this is, said so nobody mistakes it for a statement.
    let title: String
    /// `As of 22 August 2026` — see the note about balances above.
    let asOf: String
    let columnHeadings: [String]
    let rows: [Row]
    let totalLabel: String
    let totalValue: String
    /// Shown instead of the table when nobody owes anything.
    let emptyLine: String

    /// One debtor: what they are called, and what they are behind by.
    struct Row: Equatable {
        let name: String
        let amount: String
    }

    /// Whether there is a table to draw at all.
    var isEmpty: Bool { rows.isEmpty }

    /// Who owes the shop, from `StockbookStore.customers()`.
    ///
    /// - Parameter customers: **biggest debt first**, which is the order this
    ///   document wants and the order the on-screen list already shows. Sorting
    ///   again here would be a second opinion about which is right.
    static func forReceivable(
        customers: [Customer],
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil,
        now: Date = .now
    ) -> OutstandingDocument {
        make(
            parties: customers.map { ($0.name, $0.owed) },
            settings: settings,
            strings: strings,
            title: strings.receivableSummary,
            partyHeading: strings.columnCustomer,
            amountHeading: strings.receivableStat,
            totalLabel: strings.totalReceivable,
            emptyLine: strings.nothingReceivable,
            currency: currency ?? settings.currency,
            now: now
        )
    }

    /// Who the shop owes, from `StockbookStore.suppliers()`.
    ///
    /// The same page pointed the other way, and every word on it flips with it. A
    /// payable list headed "Receivable" would be the most expensive kind of
    /// wrong: one the owner acts on.
    static func forPayable(
        suppliers: [Supplier],
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil,
        now: Date = .now
    ) -> OutstandingDocument {
        make(
            parties: suppliers.map { ($0.name, $0.owed) },
            settings: settings,
            strings: strings,
            title: strings.payableSummary,
            partyHeading: strings.supplier,
            amountHeading: strings.payableStat,
            totalLabel: strings.totalPayable,
            emptyLine: strings.nothingPayable,
            currency: currency ?? settings.currency,
            now: now
        )
    }

    /// The page itself, which does not care which way the money points.
    ///
    /// `Customer` and `Supplier` are separate types with the same two fields that
    /// matter here, so they arrive already reduced to a name and a figure rather
    /// than behind a protocol neither of them asked for.
    private static func make(
        parties: [(name: String, owed: Double)],
        settings: Settings,
        strings: Strings,
        title: String,
        partyHeading: String,
        amountHeading: String,
        totalLabel: String,
        emptyLine: String,
        currency: Currency,
        now: Date
    ) -> OutstandingDocument {
        // Only what is actually outstanding. Somebody in advance is not a debtor,
        // and a negative row on a chasing list is a line the owner has to stop
        // and think about every time they read it.
        let owing = parties.filter { $0.owed > 0 }

        return OutstandingDocument(
            shopName: settings.ownerName,
            title: title,
            asOf: strings.asOfDate(strings.longDate(now)),
            columnHeadings: [partyHeading, amountHeading],
            rows: owing.map { Row(name: $0.name, amount: Money.text($0.owed, in: currency)) },
            totalLabel: totalLabel,
            // Summed from the same figures the rows print, so the foot of the
            // page can never disagree with the page. `outstanding()` and
            // `payable()` walk the same rosters to the same answers, and
            // `OutstandingDocumentTests` pins them together.
            totalValue: Money.text(owing.reduce(0) { $0 + $1.owed }, in: currency),
            emptyLine: emptyLine
        )
    }
}
