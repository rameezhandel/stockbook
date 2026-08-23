import Foundation

/// A titled list of things and figures, with a total under it: who owes the shop,
/// who it owes, or what it spent its money on.
///
/// **Every page built here is the owner's own, and none of them may be shown to
/// anybody else.** A statement is handed across the counter on purpose — it says
/// what one person owes, to that person. These say what *everybody* owes, or
/// where the shop's own money went; letting Ahmed see that Khalid is four
/// thousand behind is not an untidy page, it is a breach, and the owner's
/// spending is private by a rule written into `Expense` itself. Nothing renders
/// any of them beside a customer's own documents, and each title says whose list
/// it is.
///
/// **`asOf` carries what makes the figures true.** For money owed that is a day,
/// because a balance is true at a moment and meaningless over a span; for
/// spending it is the stretch of days it was spent over. Either way it is the
/// whole of what stops last month's printout reading as this morning's.
///
/// Laid out here for the reason `StatementDocument` is: drawing is platform work,
/// but *what* is drawn is not, and two hand-written layouts drift the first time
/// either is corrected. The Kotlin twin is `SummaryDocument.kt`, and
/// `SummaryDocumentTests` is what holds the two together.
struct SummaryDocument: Equatable {

    let shopName: String
    /// What this is, said so nobody mistakes it for a statement.
    let title: String
    /// `As of 22 August 2026`, or `1 – 31 August 2026`. See above.
    let asOf: String
    let columnHeadings: [String]
    let rows: [Row]
    let totalLabel: String
    let totalValue: String
    /// Shown instead of the table when nobody owes anything.
    let emptyLine: String

    /// One line: what it is, and what it comes to.
    ///
    /// `detail` is the small grey aside between the two — `12 times` on a
    /// spending line, absent on a debtor, who is behind by one figure and not by
    /// a count of anything.
    struct Row: Equatable {
        let name: String
        let amount: String
        var detail: String?
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
    ) -> SummaryDocument {
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
    ) -> SummaryDocument {
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

    /// Where the shop's own money went, from `StockbookStore.spendingIn`.
    ///
    /// The one page here that covers a **stretch of days** rather than a moment:
    /// money owed is a balance and true right now, but money spent only means
    /// anything over a period, and the header says which.
    ///
    /// - Parameter lines: biggest first, which `spendingIn` already returns.
    static func forSpending(
        lines: [SpendLine],
        range: StatementRange,
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil
    ) -> SummaryDocument {
        let money = currency ?? settings.currency
        return SummaryDocument(
            shopName: settings.ownerName,
            title: strings.expenseSummary,
            asOf: strings.dateSpan(
                from: strings.longDate(range.start),
                // The last day *inside* the range. A period that ends at midnight
                // on the 1st is an August statement titled "to 1 September",
                // which nobody reads as August.
                to: strings.longDate(range.end.addingTimeInterval(-1))
            ),
            columnHeadings: [strings.columnWhatItWentOn, strings.expenseInPeriod],
            rows: lines.map {
                Row(
                    name: $0.what,
                    amount: Money.text($0.total, in: money),
                    // How often, beside what it came to. "Petrol, 12 times, 780"
                    // is a different fact from "petrol 780", and it is the one
                    // that tells the owner whether to look at the price or the
                    // habit.
                    detail: strings.timesSpent($0.times)
                )
            },
            totalLabel: strings.totalSpentLabel,
            totalValue: Money.text(lines.reduce(0) { $0 + $1.total }, in: money),
            emptyLine: strings.nothingSpentThen
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
    ) -> SummaryDocument {
        // Only what is actually outstanding. Somebody in advance is not a debtor,
        // and a negative row on a chasing list is a line the owner has to stop
        // and think about every time they read it.
        let owing = parties.filter { $0.owed > 0 }

        return SummaryDocument(
            shopName: settings.ownerName,
            title: title,
            asOf: strings.asOfDate(strings.longDate(now)),
            columnHeadings: [partyHeading, amountHeading],
            rows: owing.map { Row(name: $0.name, amount: Money.text($0.owed, in: currency)) },
            totalLabel: totalLabel,
            // Summed from the same figures the rows print, so the foot of the
            // page can never disagree with the page. `outstanding()` and
            // `payable()` walk the same rosters to the same answers, and
            // `SummaryDocumentTests` pins them together.
            totalValue: Money.text(owing.reduce(0) { $0 + $1.owed }, in: currency),
            emptyLine: emptyLine
        )
    }
}
